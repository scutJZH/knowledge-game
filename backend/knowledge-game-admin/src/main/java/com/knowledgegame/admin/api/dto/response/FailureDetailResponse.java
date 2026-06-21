package com.knowledgegame.admin.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 定时任务失败明细响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailureDetailResponse {

    private Long recycleBinId;
    private String resourceType;
    private String name;
    private String reason;
}
