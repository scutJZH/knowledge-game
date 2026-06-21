package com.knowledgegame.core.domain.model.domainenum;

/**
 * 定时任务执行状态枚举
 * <p>
 * 用于 {@code scheduled_task_log} 表的 status 字段，表示一轮定时任务的执行结果。
 */
public enum TaskExecutionStatus {

    /** 全部成功（含无过期记录的场景） */
    SUCCESS,

    /** 部分成功、部分失败 */
    PARTIAL_FAILURE,

    /** 全部失败 */
    FAILURE;
}
