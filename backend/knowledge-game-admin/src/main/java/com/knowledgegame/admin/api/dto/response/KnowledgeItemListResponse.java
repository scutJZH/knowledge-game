package com.knowledgegame.admin.api.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 知识条目列表响应 DTO（不含正文 content/contentHtml，用于列表接口性能优化）
 */
@Getter
@Builder
public class KnowledgeItemListResponse {

    private Long id;
    private String title;
    private Long coverImageFileId;
    private String coverImageUrl;
    private List<String> tags;
    private List<Long> categoryIds;
    private int sortOrder;
    private String status;
    private Long createdAt;
    private Long updatedAt;
}
