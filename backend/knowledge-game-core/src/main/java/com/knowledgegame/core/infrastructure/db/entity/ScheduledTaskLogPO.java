package com.knowledgegame.core.infrastructure.db.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

import com.knowledgegame.core.domain.model.domainenum.TaskExecutionStatus;

/**
 * 定时任务执行日志持久化对象
 * <p>
 * 单表通用设计：{@code task_name} 区分不同任务。
 * failure_details 为 MySQL json 列，Java String 类型，由 Converter 做序列化/反序列化。
 * 无 created_at / updated_at：executedAt 即记录创建时间，日志写入后不可修改。
 */
@Entity
@Table(name = "scheduled_task_log",
        indexes = @Index(name = "idx_task_executed", columnList = "task_name, executed_at DESC"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduledTaskLogPO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_name", nullable = false, length = 64)
    private String taskName;

    @Column(name = "task_display", nullable = false, length = 128)
    private String taskDisplay;

    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "total_count", nullable = false)
    private int totalCount;

    @Column(name = "success_count", nullable = false)
    private int successCount;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @Column(name = "failure_details", columnDefinition = "json")
    private String failureDetails;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TaskExecutionStatus status;
}
