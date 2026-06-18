package com.knowledgegame.admin.api.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.openapitools.jackson.nullable.JsonNullable;

/**
 * 更新知识点分类请求 DTO
 * <p>
 * 必填字段（name / sortOrder）保持原 Java 类型，沿用 null=不更新 语义。
 * 可清空字段（description / iconFileId / color / coverImageFileId）包装为 JsonNullable，支持三态：
 * - 字段缺失（undefined）：不更新
 * - 字段为 null：清空
 * - 字段有值：更新为新值
 */
@Getter
@Setter
public class UpdateKnowledgeCategoryRequest {

    @Size(min = 2, max = 50, message = "名称长度 2-50")
    private String name;

    private JsonNullable<String> description = JsonNullable.undefined();

    private JsonNullable<Long> iconFileId = JsonNullable.undefined();

    private JsonNullable<String> color = JsonNullable.undefined();

    private JsonNullable<Long> coverImageFileId = JsonNullable.undefined();

    private Integer sortOrder;
}
