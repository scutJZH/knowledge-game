package com.knowledgegame.admin.api.dto.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 移动知识点分类请求 DTO
 */
@Getter
@Setter
public class MoveKnowledgeCategoryRequest {

    /**
     * 目标父级 ID，null 表示移到顶级
     */
    private Long newParentId;
}
