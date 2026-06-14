package com.knowledgegame.admin.api.dto.response;

import lombok.Builder;
import lombok.Getter;


/**
 * 卡牌模板详情响应 DTO（含 IP 系列名称）
 */
@Getter
@Builder
public class CardTemplateResponse {

    private Long id;
    private Long ipSeriesId;
    private String ipSeriesName;
    private String code;
    private String name;
    private String rarity;
    private String description;
    private String status;
    private String imageUrl;
    private Long createdAt;
    private Long updatedAt;
}
