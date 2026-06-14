package com.knowledgegame.admin.api.dto.response;

import lombok.Builder;
import lombok.Getter;


/**
 * 卡牌模板列表响应 DTO（不含星级图片）
 */
@Getter
@Builder
public class CardTemplateListResponse {

    private Long id;
    private Long ipSeriesId;
    private String ipSeriesName;
    private String code;
    private String name;
    private String rarity;
    private String description;
    private String status;
    private Long createdAt;
    private Long updatedAt;
}
