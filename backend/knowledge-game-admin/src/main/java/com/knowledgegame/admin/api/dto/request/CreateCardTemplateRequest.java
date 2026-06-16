package com.knowledgegame.admin.api.dto.request;

import com.knowledgegame.core.domain.model.domainenum.CardRarity;
import com.knowledgegame.core.domain.model.domainenum.CardTemplateStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 创建卡牌模板请求 DTO
 */
@Getter
@Setter
public class CreateCardTemplateRequest {

    @NotNull(message = "IP 系列 ID 不能为空")
    private Long ipSeriesId;

    @NotBlank(message = "编码不能为空")
    @Size(min = 2, max = 50, message = "编码长度 2-50")
    private String code;

    @NotBlank(message = "名称不能为空")
    @Size(min = 1, max = 50, message = "名称长度 1-50")
    private String name;

    @NotNull(message = "稀有度不能为空")
    private CardRarity rarity;

    @Size(max = 500, message = "描述最长 500")
    private String description;

    @NotNull(message = "状态不能为空")
    private CardTemplateStatus status;

    private Long imageFileId;
}
