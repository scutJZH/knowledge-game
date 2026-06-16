package com.knowledgegame.admin.api.dto.request;

import com.knowledgegame.core.domain.model.domainenum.CardRarity;
import com.knowledgegame.core.domain.model.domainenum.CardTemplateStatus;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 更新卡牌模板请求 DTO（基础字段可选，null 不修改）
 */
@Getter
@Setter
public class UpdateCardTemplateRequest {

    @Size(min = 2, max = 50, message = "编码长度 2-50")
    private String code;

    @Size(min = 1, max = 50, message = "名称长度 1-50")
    private String name;

    private CardRarity rarity;

    @Size(max = 500, message = "描述最长 500")
    private String description;

    private CardTemplateStatus status;

    private Long imageFileId;
}
