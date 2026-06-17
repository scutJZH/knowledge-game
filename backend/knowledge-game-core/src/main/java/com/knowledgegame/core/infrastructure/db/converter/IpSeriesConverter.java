package com.knowledgegame.core.infrastructure.db.converter;

import com.knowledgegame.core.domain.model.entity.IpSeries;
import com.knowledgegame.core.domain.model.vo.FileRef;
import com.knowledgegame.core.infrastructure.db.entity.IpSeriesPO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

/**
 * PO ↔ 领域模型转换器（IP 系列，MapStruct 自动生成实现）
 */
@Mapper
public interface IpSeriesConverter {

    IpSeriesConverter INSTANCE = Mappers.getMapper(IpSeriesConverter.class);

    /**
     * PO 转领域模型（使用 reconstruct 工厂方法）
     */
    default IpSeries toDomain(IpSeriesPO po) {
        if (po == null) {
            return null;
        }
        FileRef coverImage = toFileRef(po.getCoverImageFileId(), po.getCoverImageUrl());
        return IpSeries.reconstruct(
                po.getId(),
                po.getCode(),
                po.getName(),
                po.getDescription(),
                coverImage,
                po.getStatus(),
                po.getCreatedAt(),
                po.getUpdatedAt()
        );
    }

    /**
     * 领域模型转 PO（新增用）
     */
    default IpSeriesPO toPO(IpSeries domain) {
        if (domain == null) {
            return null;
        }
        return IpSeriesPO.builder()
                .code(domain.getCode())
                .name(domain.getName())
                .description(domain.getDescription())
                .coverImageFileId(fileIdOf(domain.getCoverImage()))
                .coverImageUrl(urlOf(domain.getCoverImage()))
                .status(domain.getStatus())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .build();
    }

    /**
     * 用领域模型更新已有 PO（显式赋值双字段，不依赖 MapStruct IGNORE 策略）
     */
    default void updatePO(@MappingTarget IpSeriesPO po, IpSeries domain) {
        if (domain.getCode() != null) {
            po.setCode(domain.getCode());
        }
        if (domain.getName() != null) {
            po.setName(domain.getName());
        }
        if (domain.getDescription() != null) {
            po.setDescription(domain.getDescription());
        }
        if (domain.getCoverImage() != null) {
            po.setCoverImageFileId(fileIdOf(domain.getCoverImage()));
            po.setCoverImageUrl(urlOf(domain.getCoverImage()));
        }
        if (domain.getStatus() != null) {
            po.setStatus(domain.getStatus());
        }
        po.setUpdatedAt(domain.getUpdatedAt());
    }

    default FileRef toFileRef(Long fileId, String url) {
        if (fileId == null && url == null) {
            return null;
        }
        return FileRef.of(fileId, url);
    }

    default Long fileIdOf(FileRef ref) {
        return ref != null ? ref.fileId() : null;
    }

    default String urlOf(FileRef ref) {
        return ref != null ? ref.url() : null;
    }
}
