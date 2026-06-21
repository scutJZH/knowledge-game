package com.knowledgegame.admin.application.scheduler;

import com.knowledgegame.core.domain.model.domainenum.ResourceType;
import com.knowledgegame.core.domain.model.domainenum.TaskExecutionStatus;
import com.knowledgegame.core.domain.model.entity.ScheduledTaskLog;
import com.knowledgegame.core.domain.model.vo.FailureDetail;
import com.knowledgegame.core.domain.port.outbound.ScheduledTaskLogRepositoryPort;
import com.knowledgegame.core.domain.service.recyclebin.RecycleBinItemStrategy;
import com.knowledgegame.core.domain.service.recyclebin.RecycleBinItemStrategyRegistry;
import com.knowledgegame.core.infrastructure.db.entity.RecycleBinItemPO;
import com.knowledgegame.core.infrastructure.db.repository.RecycleBinItemJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RecycleBinCleanupScheduler 黑盒测试
 * <p>
 * 仅凭 PRD §4.1 行为描述写测试，不参考实现代码。
 * 与开发者白盒测试（RecycleBinCleanupSchedulerTest）形成双重保障。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecycleBinCleanupSchedulerBlackBoxTest {

    @Mock
    private RecycleBinItemJpaRepository jpaRepository;

    @Mock
    private RecycleBinItemStrategyRegistry strategyRegistry;

    @Mock
    private ScheduledTaskLogRepositoryPort logRepositoryPort;

    @Mock
    private RecycleBinItemStrategy<?> strategy;

    private RecycleBinCleanupScheduler scheduler;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        scheduler = new RecycleBinCleanupScheduler(jpaRepository, strategyRegistry, logRepositoryPort, null);
        java.lang.reflect.Field selfField = RecycleBinCleanupScheduler.class.getDeclaredField("self");
        selfField.setAccessible(true);
        selfField.set(scheduler, scheduler);
    }

    // ===== 场景 1：无过期记录写 0 日志 =====

    @Test
    void shouldWriteZeroLogWhenNoExpiredRecords() {
        when(jpaRepository.findByRestoreDeadlineBefore(any())).thenReturn(List.of());

        scheduler.cleanupExpiredRecords();

        ArgumentCaptor<ScheduledTaskLog> captor = ArgumentCaptor.forClass(ScheduledTaskLog.class);
        verify(logRepositoryPort).save(captor.capture());
        ScheduledTaskLog log = captor.getValue();
        assertEquals(0, log.getTotalCount());
        assertEquals(0, log.getSuccessCount());
        assertEquals(0, log.getFailureCount());
        assertEquals(TaskExecutionStatus.SUCCESS, log.getStatus());
        assertNull(log.getFailureDetails());
    }

    // ===== 场景 2：部分失败不中断后续处理 =====

    @Test
    @SuppressWarnings("unchecked")
    void shouldNotInterruptOnPartialFailure() {
        when(strategyRegistry.get(any())).thenReturn((RecycleBinItemStrategy) strategy);
        List<RecycleBinItemPO> expired = List.of(
                buildPO(1L, ResourceType.IP_SERIES, "pass1"),
                buildPO(2L, ResourceType.CARD_TEMPLATE, "fail1"),
                buildPO(3L, ResourceType.IP_SERIES, "pass2"),
                buildPO(4L, ResourceType.QUESTION, "fail2"),
                buildPO(5L, ResourceType.IP_SERIES, "pass3"));
        when(jpaRepository.findByRestoreDeadlineBefore(any())).thenReturn(expired);
        doThrow(new RuntimeException("文件服务不可达")).when(strategy).purge(2L);
        doThrow(new RuntimeException("详情表已不存在")).when(strategy).purge(4L);

        scheduler.cleanupExpiredRecords();

        // 验证全部 5 次 purge 都被调用
        verify(strategy).purge(1L);
        verify(strategy).purge(2L);
        verify(strategy).purge(3L);
        verify(strategy).purge(4L);
        verify(strategy).purge(5L);

        ArgumentCaptor<ScheduledTaskLog> captor = ArgumentCaptor.forClass(ScheduledTaskLog.class);
        verify(logRepositoryPort).save(captor.capture());
        ScheduledTaskLog log = captor.getValue();

        assertEquals(5, log.getTotalCount());
        assertEquals(3, log.getSuccessCount());
        assertEquals(2, log.getFailureCount());
        assertEquals(TaskExecutionStatus.PARTIAL_FAILURE, log.getStatus());

        // 验证 failureDetails 格式与 PRD 一致
        assertNotNull(log.getFailureDetails());
        assertEquals(2, log.getFailureDetails().size());

        FailureDetail fd1 = log.getFailureDetails().get(0);
        assertEquals(2L, fd1.recycleBinId());
        assertEquals("CARD_TEMPLATE", fd1.resourceType());
        assertEquals("fail1", fd1.name());
        assertEquals("文件服务不可达", fd1.reason());

        FailureDetail fd2 = log.getFailureDetails().get(1);
        assertEquals(4L, fd2.recycleBinId());
        assertEquals("QUESTION", fd2.resourceType());
        assertEquals("fail2", fd2.name());
        assertEquals("详情表已不存在", fd2.reason());
    }

    // ===== 场景 3：FailureDetail 格式符合 PRD =====

    @Test
    @SuppressWarnings("unchecked")
    void failureDetailShouldContainRequiredFields() {
        when(strategyRegistry.get(any())).thenReturn((RecycleBinItemStrategy) strategy);
        RecycleBinItemPO po = buildPO(42L, ResourceType.IP_SERIES, "火影忍者");
        when(jpaRepository.findByRestoreDeadlineBefore(any())).thenReturn(List.of(po));
        doThrow(new RuntimeException("文件服务不可达")).when(strategy).purge(42L);

        scheduler.cleanupExpiredRecords();

        ArgumentCaptor<ScheduledTaskLog> captor = ArgumentCaptor.forClass(ScheduledTaskLog.class);
        verify(logRepositoryPort).save(captor.capture());
        ScheduledTaskLog log = captor.getValue();

        assertEquals(1, log.getFailureCount());
        assertNotNull(log.getFailureDetails());
        FailureDetail fd = log.getFailureDetails().get(0);
        // PRD 规定的 4 个字段：recycleBinId, resourceType, name, reason
        assertEquals(42L, fd.recycleBinId());
        assertEquals("IP_SERIES", fd.resourceType());
        assertEquals("火影忍者", fd.name());
        assertEquals("文件服务不可达", fd.reason());
    }

    private RecycleBinItemPO buildPO(Long id, ResourceType type, String name) {
        return RecycleBinItemPO.builder()
                .id(id)
                .resourceType(type)
                .originalId(id * 10)
                .originalName(name)
                .deletedBy("admin")
                .deletedAt(LocalDateTime.now())
                .restoreDeadline(LocalDateTime.now().minusDays(1))
                .build();
    }
}
