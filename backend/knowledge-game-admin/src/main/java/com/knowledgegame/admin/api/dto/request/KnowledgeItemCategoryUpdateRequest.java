package com.knowledgegame.admin.api.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 知识条目分类更新请求 DTO
 */
@Getter
@Setter
public class KnowledgeItemCategoryUpdateRequest {

    @NotEmpty(message = "分类不能为空")
    private List<Long> categoryIds;
}
