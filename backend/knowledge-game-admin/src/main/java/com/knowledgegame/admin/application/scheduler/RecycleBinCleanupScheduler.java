package com.knowledgegame.admin.application.scheduler;

import com.knowledgegame.core.domain.model.domainenum.TaskExecutionStatus;
import com.knowledgegame.core.domain.model.entity.RecycleBinItem;
import com.knowledgegame.core.domain.model.entity.ScheduledTaskLog;
import com.knowledgegame.core.domain.model.vo.FailureDetail;
import com.knowledgegame.core.domain.port.outbound.ScheduledTaskLogRepositoryPort;
import com.knowledgegame.core.domain.service.recyclebin.RecycleBinItemStrategyRegistry;
import com.knowledgegame.core.infrastructure.db.converter.RecycleBinItemConverter;
import com.knowledgegame.core.infrastructure.db.entity.RecycleBinItemPO;
import com.knowledgegame.core.infrastructure.db.repository.RecycleBinItemJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 回收站定时清理调度器（REQ-101）
 * <p>
 * 每日执行，查询 recycle_bin 中 restore_deadline 过期的记录，逐条调策略 purge 物理删除。
 * 执行结果写入 scheduled_task_log 表供管理端查询。
 */
@Component
public class RecycleBinCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecycleBinCleanupScheduler.class);

    private static final String TASK_NAME = "RECYCLE_BIN_CLEANUP";
    private static final String TASK_DISPLAY = "回收站定时清理";

    private final RecycleBinItemJpaRepository recycleBinItemJpaRepository;
    private final RecycleBinItemStrategyRegistry strategyRegistry;
    private final ScheduledTaskLogRepositoryPort scheduledTaskLogRepositoryPort;
    private final RecycleBinCleanupScheduler self;

    public RecycleBinCleanupScheduler(
            RecycleBinItemJpaRepository recycleBinItemJpaRepository,
            RecycleBinItemStrategyRegistry strategyRegistry,
            ScheduledTaskLogRepositoryPort scheduledTaskLogRepositoryPort,
            @Lazy RecycleBinCleanupScheduler self) {
        this.recycleBinItemJpaRepository = recycleBinItemJpaRepository;
        this.strategyRegistry = strategyRegistry;
        this.scheduledTaskLogRepositoryPort = scheduledTaskLogRepositoryPort;
        this.self = self;
    }

    /**
     * 定时清理过期回收站记录
     * <p>
     * cron 通过 application.yml 配置项 knowledgegame.recycle-bin.cleanup-cron 控制，默认每日凌晨 3:00。
     */
    @Scheduled(cron = "${knowledgegame.recycle-bin.cleanup-cron:0 0 3 * * ?}")
    public void cleanupExpiredRecords() {
        long start = System.currentTimeMillis();
        LocalDateTime now = LocalDateTime.now();

        List<RecycleBinItemPO> expiredPOs = recycleBinItemJpaRepository.findByRestoreDeadlineBefore(now);

        if (expiredPOs.isEmpty()) {
            // 无过期记录也写日志，作为"执行过"的证明
            ScheduledTaskLog logEntry = new ScheduledTaskLog(
                    null, TASK_NAME, TASK_DISPLAY, now,
                    System.currentTimeMillis() - start, 0, 0, 0,
                    null, TaskExecutionStatus.SUCCESS);
            scheduledTaskLogRepositoryPort.save(logEntry);
            log.info("回收站定时清理完成: 无过期记录, 耗时={}ms", System.currentTimeMillis() - start);
            return;
        }

        int successCount = 0;
        List<FailureDetail> failures = new ArrayList<>();

        for (RecycleBinItemPO po : expiredPOs) {
            RecycleBinItem item = RecycleBinItemConverter.INSTANCE.toDomain(po);
            try {
                self.purgeInNewTransaction(item);
                successCount++;
            } catch (Exception e) {
                log.error("定时清理失败: recycleBinId={}, resourceType={}, name={}",
                        po.getId(), po.getResourceType(), po.getOriginalName(), e);
                failures.add(new FailureDetail(
                        po.getId(),
                        po.getResourceType().name(),
                        po.getOriginalName(),
                        e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            }
        }

        long duration = System.currentTimeMillis() - start;
        TaskExecutionStatus status = failures.isEmpty() ? TaskExecutionStatus.SUCCESS
                : (successCount == 0 ? TaskExecutionStatus.FAILURE : TaskExecutionStatus.PARTIAL_FAILURE);

        ScheduledTaskLog logEntry = new ScheduledTaskLog(
                null, TASK_NAME, TASK_DISPLAY, now,
                duration, expiredPOs.size(), successCount, failures.size(),
                failures.isEmpty() ? null : failures, status);
        scheduledTaskLogRepositoryPort.save(logEntry);

        log.info("回收站定时清理完成: 总数={}, 成功={}, 失败={}, 耗时={}ms",
                expiredPOs.size(), successCount, failures.size(), duration);
    }

    /**
     * 在独立新事务中执行单条永久删除（仅内部用，不可从外部直接调用）
     * <p>
     * public 是 Spring CGLIB 代理的硬性要求。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void purgeInNewTransaction(RecycleBinItem item) {
        strategyRegistry.get(item.getResourceType()).purge(item.getId());
    }
}
