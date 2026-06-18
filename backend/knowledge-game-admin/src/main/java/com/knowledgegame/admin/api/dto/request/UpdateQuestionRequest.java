package com.knowledgegame.admin.api.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.openapitools.jackson.nullable.JsonNullable;

import java.util.List;

/**
 * 更新题目请求 DTO
 * <p>
 * 必填字段（content / options / answer / difficulty）保持原 Java 类型，沿用 null=不更新 语义。
 * 可清空字段（explanation / tags）包装为 JsonNullable，支持三态：
 * - 字段缺失（undefined）：不更新
 * - 字段为 null：清空
 * - 字段有值：更新为新值
 * <p>
 * 注意：分类关联（categoryIds）不在本 DTO 中，通过独立的 PUT /questions/{id}/categories 接口更新。
 */
@Getter
@Setter
public class UpdateQuestionRequest {

    @Size(max = 500, message = "题目内容不超过 500 字")
    private String content;

    private List<CreateQuestionRequest.OptionItem> options;

    private String answer;

    private Integer difficulty;

    private JsonNullable<String> explanation = JsonNullable.undefined();

    private JsonNullable<List<String>> tags = JsonNullable.undefined();
}
