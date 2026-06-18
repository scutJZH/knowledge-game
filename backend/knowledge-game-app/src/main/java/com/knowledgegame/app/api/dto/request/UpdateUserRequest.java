package com.knowledgegame.app.api.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.openapitools.jackson.nullable.JsonNullable;

/**
 * 更新用户请求 DTO
 * <p>
 * 必填字段（nickname）保持原 Java 类型，沿用 null=不更新 语义。
 * 可清空字段（avatarFileId）包装为 JsonNullable，支持三态：
 * - 字段缺失（undefined）：不更新
 * - 字段为 null：清空
 * - 字段有值：更新为新值
 */
@Getter
@Setter
public class UpdateUserRequest {

    @Size(max = 50, message = "昵称最长 50")
    private String nickname;

    private JsonNullable<Long> avatarFileId = JsonNullable.undefined();
}
