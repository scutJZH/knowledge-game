package com.knowledgegame.admin.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 定时任务执行日志列表响应项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduledTaskLogResponse {

    private Long id;
    private String taskName;
    private String taskDisplay;
    /** epoch 毫秒时间戳 */
    private Long executedAt;
    private Long durationMs;
    private Integer totalCount;
    private Integer successCount;
    private Integer failureCount;
    /** 失败明细列表，null 表示无失败 */
    private List<FailureDetailResponse> failureDetails;
    private String status;
}
