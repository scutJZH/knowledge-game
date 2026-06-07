package com.knowledgegame.api.assembler;

import com.knowledgegame.api.dto.response.IpSeriesResponse;
import com.knowledgegame.domain.model.entity.IpSeries;

/**
 * IP 系列领域模型 ↔ DTO 转换器
 */
public class IpSeriesAssembler {

    /**
     * 领域模型转响应 DTO
     */
    public static IpSeriesResponse toResponse(IpSeries ipSeries) {
        if (ipSeries == null) {
            return null;
        }
        return IpSeriesResponse.builder()
                .id(ipSeries.getId())
                .code(ipSeries.getCode())
                .name(ipSeries.getName())
                .description(ipSeries.getDescription())
                .coverImageUrl(ipSeries.getCoverImageUrl())
                .status(ipSeries.getStatus().name())
                .createdAt(ipSeries.getCreatedAt())
                .updatedAt(ipSeries.getUpdatedAt())
                .build();
    }
}
