package com.knowledgegame.admin.api.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 知识点分类响应 DTO
 */
@Getter
@Builder
public class KnowledgeCategoryResponse {

    private Long id;
    private Long parentId;
    private String name;
    private String description;
    private String iconUrl;
    private String color;
    private String coverImageUrl;
    private int sortOrder;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
