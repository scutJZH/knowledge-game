package com.knowledgegame.admin.api.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * 星级图片响应 DTO
 */
@Getter
@Builder
public class StarImageResponse {

    private int starLevel;
    private String imageUrl;
}
