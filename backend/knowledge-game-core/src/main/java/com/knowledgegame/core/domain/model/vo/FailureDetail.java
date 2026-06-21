package com.knowledgegame.core.domain.model.vo;

/**
 * 定时任务单条失败明细（值对象）
 *
 * @param recycleBinId 回收站记录 ID
 * @param resourceType 资源类型（ResourceType 枚举字符串值，如 "IP_SERIES"）
 * @param name         资源名称
 * @param reason       失败原因
 */
public record FailureDetail(
        Long recycleBinId,
        String resourceType,
        String name,
        String reason) {
}
