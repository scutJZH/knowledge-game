package com.knowledgegame.admin.api.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 批量状态变更请求 DTO
 */
@Getter
@Setter
public class BatchStatusRequest {

    @NotEmpty(message = "ID 列表不能为空")
    @Size(max = 100, message = "批量操作最多 100 条")
    private List<Long> ids;
}