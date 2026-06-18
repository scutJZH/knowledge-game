package com.knowledgegame.admin.api.dto.request;

import com.knowledgegame.core.domain.model.domainenum.CardRarity;
import com.knowledgegame.core.domain.model.domainenum.CardTemplateStatus;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.openapitools.jackson.nullable.JsonNullable;

/**
 * 更新卡牌模板请求 DTO
 * <p>
 * 必填字段（code/name/rarity/status）保持原 Java 类型，沿用 null=不更新 语义。
 * 可清空字段（description/imageFileId）包装为 JsonNullable，支持三态。
 */
@Getter
@Setter
public class UpdateCardTemplateRequest {

    @Size(min = 2, max = 50, message = "编码长度 2-50")
    private String code;

    @Size(min = 1, max = 50, message = "名称长度 1-50")
    private String name;

    private CardRarity rarity;

    private JsonNullable<String> description = JsonNullable.undefined();

    private CardTemplateStatus status;

    private JsonNullable<Long> imageFileId = JsonNullable.undefined();
}
