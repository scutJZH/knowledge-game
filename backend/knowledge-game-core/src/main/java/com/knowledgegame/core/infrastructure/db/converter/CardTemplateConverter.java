package com.knowledgegame.core.infrastructure.db.converter;

import com.knowledgegame.core.domain.model.entity.CardTemplate;
import com.knowledgegame.core.domain.model.vo.FileRef;
import com.knowledgegame.core.infrastructure.db.entity.CardTemplatePO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

@Mapper
public interface CardTemplateConverter {

    CardTemplateConverter INSTANCE = Mappers.getMapper(CardTemplateConverter.class);

    default CardTemplate toDomain(CardTemplatePO po) {
        if (po == null) return null;
        FileRef image = toFileRef(po.getImageFileId(), po.getImageUrl());
        return CardTemplate.reconstruct(
                po.getId(), po.getIpSeriesId(), po.getCode(), po.getName(),
                po.getRarity(), po.getDescription(), po.getStatus(), image,
                po.getCreatedAt(), po.getUpdatedAt());
    }

    default CardTemplatePO toPO(CardTemplate domain) {
        if (domain == null) return null;
        return CardTemplatePO.builder()
                .id(domain.getId())
                .ipSeriesId(domain.getIpSeriesId())
                .code(domain.getCode())
                .name(domain.getName())
                .rarity(domain.getRarity())
                .description(domain.getDescription())
                .status(domain.getStatus())
                .imageFileId(fileIdOf(domain.getImage()))
                .imageUrl(urlOf(domain.getImage()))
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .build();
    }

    default void updatePO(@MappingTarget CardTemplatePO po, CardTemplate domain) {
        if (domain == null) return;
        if (domain.getCode() != null) po.setCode(domain.getCode());
        if (domain.getName() != null) po.setName(domain.getName());
        if (domain.getRarity() != null) po.setRarity(domain.getRarity());
        if (domain.getDescription() != null) po.setDescription(domain.getDescription());
        if (domain.getStatus() != null) po.setStatus(domain.getStatus());
        po.setImageFileId(fileIdOf(domain.getImage()));
        po.setImageUrl(urlOf(domain.getImage()));
        po.setUpdatedAt(domain.getUpdatedAt());
    }

    default FileRef toFileRef(Long fileId, String url) {
        if (fileId == null && url == null) return null;
        return FileRef.of(fileId, url);
    }

    default Long fileIdOf(FileRef ref) {
        return ref != null ? ref.fileId() : null;
    }

    default String urlOf(FileRef ref) {
        return ref != null ? ref.url() : null;
    }
}
