package com.knowledgegame.core.infrastructure.db.converter;

import com.knowledgegame.core.domain.model.entity.CardTemplate;
import com.knowledgegame.core.infrastructure.db.entity.CardTemplatePO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.factory.Mappers;

/**
 * PO ↔ 领域模型转换器（卡牌模板，MapStruct）
 */
@Mapper(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface CardTemplateConverter {

    CardTemplateConverter INSTANCE = Mappers.getMapper(CardTemplateConverter.class);

    /**
     * PO 转领域模型
     */
    default CardTemplate toDomain(CardTemplatePO po) {
        if (po == null) {
            return null;
        }
        return CardTemplate.reconstruct(
                po.getId(),
                po.getIpSeriesId(),
                po.getCode(),
                po.getName(),
                po.getRarity(),
                po.getDescription(),
                po.getStatus(),
                po.getImageUrl(),
                po.getCreatedAt(),
                po.getUpdatedAt()
        );
    }

    /**
     * 领域模型转 PO（新增）
     */
    default CardTemplatePO toPO(CardTemplate template) {
        if (template == null) {
            return null;
        }
        return CardTemplatePO.builder()
                .id(template.getId())
                .ipSeriesId(template.getIpSeriesId())
                .code(template.getCode())
                .name(template.getName())
                .rarity(template.getRarity())
                .description(template.getDescription())
                .status(template.getStatus())
                .imageUrl(template.getImageUrl())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .build();
    }

    /**
     * 用领域模型更新已有 PO
     */
    default void updatePO(@MappingTarget CardTemplatePO po, CardTemplate template) {
        if (template == null) {
            return;
        }
        po.setCode(template.getCode());
        po.setName(template.getName());
        po.setRarity(template.getRarity());
        po.setDescription(template.getDescription());
        po.setStatus(template.getStatus());
        po.setImageUrl(template.getImageUrl());
        po.setUpdatedAt(template.getUpdatedAt());
    }
}
