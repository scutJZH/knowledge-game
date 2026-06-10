package com.knowledgegame.admin.api.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 知识点分类树节点响应 DTO
 */
@Getter
@Builder
public class KnowledgeCategoryTreeResponse {

    private Long id;
    private Long parentId;
    private String name;
    private String status;
    private String iconUrl;
    private String color;
    private int sortOrder;

    @Setter
    private List<KnowledgeCategoryTreeResponse> children;
}
