package com.knowledgegame.infrastructure.db.converter;

import com.knowledgegame.domain.model.entity.IpSeries;
import com.knowledgegame.infrastructure.db.entity.IpSeriesPO;

/**
 * PO ↔ 领域模型转换器（IP 系列）
 */
public class IpSeriesConverter {

    /**
     * PO 转领域模型
     */
    public static IpSeries toDomain(IpSeriesPO po) {
        if (po == null) {
            return null;
        }
        return IpSeries.reconstruct(
                po.getId(),
                po.getCode(),
                po.getName(),
                po.getDescription(),
                po.getCoverImageUrl(),
                po.getStatus(),
                po.getCreatedAt(),
                po.getUpdatedAt()
        );
    }

    /**
     * 领域模型转 PO（新增）
     */
    public static IpSeriesPO toPO(IpSeries ipSeries) {
        if (ipSeries == null) {
            return null;
        }
        return IpSeriesPO.builder()
                .code(ipSeries.getCode())
                .name(ipSeries.getName())
                .description(ipSeries.getDescription())
                .coverImageUrl(ipSeries.getCoverImageUrl())
                .status(ipSeries.getStatus())
                .createdAt(ipSeries.getCreatedAt())
                .updatedAt(ipSeries.getUpdatedAt())
                .build();
    }

    /**
     * 用领域模型更新已有 PO
     */
    public static void updatePO(IpSeriesPO po, IpSeries ipSeries) {
        po.setCode(ipSeries.getCode());
        po.setName(ipSeries.getName());
        po.setDescription(ipSeries.getDescription());
        po.setCoverImageUrl(ipSeries.getCoverImageUrl());
        po.setStatus(ipSeries.getStatus());
        po.setUpdatedAt(ipSeries.getUpdatedAt());
    }
}
