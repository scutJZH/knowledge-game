package com.knowledgegame.admin.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 定时任务执行日志列表查询请求
 */
@Data
public class ScheduledTaskLogListRequest {

    /** 任务标识过滤，null 表示不过滤 */
    private String taskName;

    /** 页码（0-based），默认 0 */
    @Min(0)
    private Integer page = 0;

    /** 每页条数，默认 20，最大 100 */
    @Min(1)
    @Max(100)
    private Integer size = 20;
}
