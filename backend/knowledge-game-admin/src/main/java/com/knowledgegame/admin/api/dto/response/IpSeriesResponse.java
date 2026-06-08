package com.knowledgegame.admin.api.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * IP 系列响应 DTO
 */
@Getter
@Builder
public class IpSeriesResponse {

    private Long id;
    private String code;
    private String name;
    private String description;
    private String coverImageUrl;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
