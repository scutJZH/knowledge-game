package com.knowledgegame.admin.api.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 知识条目响应 DTO
 */
@Getter
@Builder
public class KnowledgeItemResponse {

    private Long id;
    private String title;
    private String content;
    private String contentHtml;
    private Long coverImageFileId;
    private String coverImageUrl;
    private List<String> tags;
    private List<Long> categoryIds;
    private int sortOrder;
    private String status;
    private Long createdAt;
    private Long updatedAt;
}
