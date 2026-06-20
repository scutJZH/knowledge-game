package com.knowledgegame.admin.api.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 批量恢复请求 DTO
 */
@Getter
@Setter
public class BatchRestoreRequest {

    @NotEmpty(message = "ids 不能为空")
    @Size(max = 100, message = "一次最多恢复 100 条")
    private List<@NotNull(message = "ids 元素不能为 null") Long> ids;
}
