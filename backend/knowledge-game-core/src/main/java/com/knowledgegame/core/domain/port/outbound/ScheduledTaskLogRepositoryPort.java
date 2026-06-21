package com.knowledgegame.core.domain.port.outbound;

import com.knowledgegame.core.domain.model.entity.ScheduledTaskLog;
import com.knowledgegame.core.domain.model.vo.PageResult;

/**
 * 定时任务执行日志仓储端口（出端口）
 * <p>
 * 定义 {@code scheduled_task_log} 表的持久化操作。
 * 供所有定时任务复用。
 */
public interface ScheduledTaskLogRepositoryPort {

    /**
     * 保存一条执行日志
     *
     * @param log 执行日志
     */
    void save(ScheduledTaskLog log);

    /**
     * 分页查询执行日志
     *
     * @param taskName 任务标识过滤，null 表示不过滤
     * @param page     页码（0-based）
     * @param size     每页条数
     * @return 分页结果（按 executedAt 降序）
     */
    PageResult<ScheduledTaskLog> findAll(String taskName, int page, int size);
}
