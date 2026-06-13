package com.knowledgegame.admin.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 批量排序请求 DTO
 */
@Getter
@Setter
public class BatchSortRequest {

    @NotEmpty(message = "items 不能为空")
    @Size(max = 50, message = "一次最多排序 50 个分类")
    @Valid
    private List<BatchSortItem> items;
}
