package com.knowledgegame.admin.api.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 卡牌模板详情响应 DTO（含星级图片 + IP 系列名称）
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
    private List<StarImageResponse> starImages;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
