package com.knowledgegame.admin.api.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 更新知识条目请求 DTO（所有字段可选，null 表示不修改）
 */
@Getter
@Setter
public class UpdateKnowledgeItemRequest {

    @Size(max = 200, message = "标题不超过 200 字")
    private String title;

    private String content;

    private Long coverImageFileId;

    @Size(max = 10, message = "标签最多 10 个")
    private List<String> tags;

    private Integer sortOrder;
}
