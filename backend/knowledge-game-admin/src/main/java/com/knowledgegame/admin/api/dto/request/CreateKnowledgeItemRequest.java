package com.knowledgegame.admin.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 创建知识条目请求 DTO
 */
@Getter
@Setter
public class CreateKnowledgeItemRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题不超过 200 字")
    private String title;

    @NotBlank(message = "内容不能为空")
    private String content;

    private Long coverImageFileId;

    @Size(max = 10, message = "标签最多 10 个")
    private List<String> tags;

    private Integer sortOrder;

    @NotEmpty(message = "分类不能为空")
    private List<Long> categoryIds;
}
