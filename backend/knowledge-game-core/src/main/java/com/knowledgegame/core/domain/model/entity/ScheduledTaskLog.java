package com.knowledgegame.core.domain.model.entity;

import com.knowledgegame.core.domain.model.domainenum.TaskExecutionStatus;
import com.knowledgegame.core.domain.model.vo.FailureDetail;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时任务执行日志（领域实体，纯数据持有对象）
 * <p>
 * 记录每轮定时任务的执行结果。供所有定时任务共用，按 {@code taskName} 区分。
 */
public class ScheduledTaskLog {

    private final Long id;
    private final String taskName;
    private final String taskDisplay;
    private final LocalDateTime executedAt;
    private final long durationMs;
    private final int totalCount;
    private final int successCount;
    private final int failureCount;
    private final List<FailureDetail> failureDetails;
    private final TaskExecutionStatus status;

    public ScheduledTaskLog(Long id, String taskName, String taskDisplay,
                            LocalDateTime executedAt, long durationMs,
                            int totalCount, int successCount, int failureCount,
                            List<FailureDetail> failureDetails,
                            TaskExecutionStatus status) {
        this.id = id;
        this.taskName = taskName;
        this.taskDisplay = taskDisplay;
        this.executedAt = executedAt;
        this.durationMs = durationMs;
        this.totalCount = totalCount;
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.failureDetails = failureDetails;
        this.status = status;
    }

    public Long getId() { return id; }
    public String getTaskName() { return taskName; }
    public String getTaskDisplay() { return taskDisplay; }
    public LocalDateTime getExecutedAt() { return executedAt; }
    public long getDurationMs() { return durationMs; }
    public int getTotalCount() { return totalCount; }
    public int getSuccessCount() { return successCount; }
    public int getFailureCount() { return failureCount; }
    public List<FailureDetail> getFailureDetails() { return failureDetails; }
    public TaskExecutionStatus getStatus() { return status; }
}
