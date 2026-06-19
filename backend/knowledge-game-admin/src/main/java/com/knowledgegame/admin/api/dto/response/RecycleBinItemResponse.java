package com.knowledgegame.admin.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 回收站列表项响应 DTO（13 个字段）
 * <p>
 * 所有时间字段统一返回 epoch 毫秒（Long），遵循 REQ-86 时间戳协议。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecycleBinItemResponse {

    private Long id;
    private String resourceType;
    private String resourceTypeDisplay;
    private Long originalId;
    private String originalName;
    private Long originalCreatedAt;
    private Long originalUpdatedAt;
    private String originalCreatedBy;
    private String originalUpdatedBy;
    private String deletedBy;
    private Long deletedAt;
    private Long restoreDeadline;
    private Integer daysUntilPurge;
}
