package com.knowledgegame.admin.api.dto.request;

import com.knowledgegame.core.domain.model.domainenum.IpSeriesStatus;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.openapitools.jackson.nullable.JsonNullable;

/**
 * 更新 IP 系列请求 DTO
 * <p>
 * 必填字段（code / name / status）保持原 Java 类型，沿用 null=不更新 语义。
 * 可清空字段（description / coverImageFileId）包装为 JsonNullable，支持三态：
 * - 字段缺失（undefined）：不更新
 * - 字段为 null：清空
 * - 字段有值：更新为新值
 */
@Getter
@Setter
public class UpdateIpSeriesRequest {

    @Size(min = 2, max = 30, message = "编码长度 2-30")
    private String code;

    @Size(min = 2, max = 50, message = "名称长度 2-50")
    private String name;

    private JsonNullable<String> description = JsonNullable.undefined();

    private JsonNullable<Long> coverImageFileId = JsonNullable.undefined();

    private IpSeriesStatus status;
}
