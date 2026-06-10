package com.knowledgegame.admin.api.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 更新知识点分类请求 DTO（所有字段可选，null 表示不修改）
 */
@Getter
@Setter
public class UpdateKnowledgeCategoryRequest {

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

    private Integer sortOrder;
}
