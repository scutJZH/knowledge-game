package com.knowledgegame.admin.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 创建知识点分类请求 DTO
 */
@Getter
@Setter
public class CreateKnowledgeCategoryRequest {

    private Long parentId;

    @NotBlank(message = "名称不能为空")
    @Size(min = 2, max = 50, message = "名称长度 2-50")
    private String name;

    @Size(max = 500, message = "描述最长 500")
    private String description;

    @Size(max = 500, message = "图标 URL 最长 500")
    private String iconUrl;

    @Size(max = 20, message = "颜色值最长 20")
    private String color;

    @Size(max = 500, message = "封面图 URL 最长 500")
    private String coverImageUrl;

    /**
     * 排序号（可选，不传时后端自动计算）
     */
    private Integer sortOrder;
}
