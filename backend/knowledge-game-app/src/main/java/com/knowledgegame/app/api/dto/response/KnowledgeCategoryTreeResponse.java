package com.knowledgegame.app.api.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 知识点分类树节点响应 DTO（用户端）
 */
@Getter
@Builder
public class KnowledgeCategoryTreeResponse {

    private Long id;
    private Long parentId;
    private String name;
    private String description;
    private Long iconFileId;
    private String iconUrl;
    private String color;
    private Long coverImageFileId;
    private String coverImageUrl;
    private int sortOrder;
    private String status;
    private Long createdAt;
    private Long updatedAt;

    @Setter
    private List<KnowledgeCategoryTreeResponse> children;
}
