package com.knowledgegame.core.infrastructure.db.converter;

import com.knowledgegame.core.domain.model.entity.CardTemplate;
import com.knowledgegame.core.domain.model.vo.CardStarImage;
import com.knowledgegame.core.infrastructure.db.entity.CardStarImagePO;
import com.knowledgegame.core.infrastructure.db.entity.CardTemplatePO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.factory.Mappers;

import java.util.ArrayList;
import java.util.List;

/**
 * PO ↔ 领域模型转换器（卡牌模板，MapStruct + 手动嵌套转换）
 */
@Mapper(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface CardTemplateConverter {

    CardTemplateConverter INSTANCE = Mappers.getMapper(CardTemplateConverter.class);

    /**
     * PO 转领域模型（手动转换嵌套的 starImages）
     */
    default CardTemplate toDomain(CardTemplatePO po) {
        if (po == null) {
            return null;
        }
        List<CardStarImage> starImages = new ArrayList<>();
        if (po.getStarImages() != null) {
            for (CardStarImagePO imgPO : po.getStarImages()) {
                starImages.add(CardStarImage.create(imgPO.getStarLevel(), imgPO.getImageUrl()));
            }
        }
        return CardTemplate.reconstruct(
                po.getId(),
                po.getIpSeriesId(),
                po.getCode(),
                po.getName(),
                po.getRarity(),
                po.getDescription(),
                po.getStatus(),
                starImages,
                po.getCreatedAt(),
                po.getUpdatedAt()
        );
    }

    /**
     * 领域模型转 PO（新增，手动处理 starImages 双向关联）
     */
    default CardTemplatePO toPO(CardTemplate template) {
        if (template == null) {
            return null;
        }
        CardTemplatePO po = CardTemplatePO.builder()
                .id(template.getId())
                .ipSeriesId(template.getIpSeriesId())
                .code(template.getCode())
                .name(template.getName())
                .rarity(template.getRarity())
                .description(template.getDescription())
                .status(template.getStatus())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .build();
        // 手动设置 starImages 双向关联
        if (template.getStarImages() != null) {
            List<CardStarImagePO> imagePOList = new ArrayList<>();
            for (CardStarImage img : template.getStarImages()) {
                CardStarImagePO imgPO = CardStarImagePO.builder()
                        .starLevel(img.getStarLevel())
                        .imageUrl(img.getImageUrl())
                        .cardTemplate(po)
                        .build();
                imagePOList.add(imgPO);
            }
            po.setStarImages(imagePOList);
        }
        return po;
    }

    /**
     * 用领域模型更新已有 PO（原地更新 starImages，避免 clear+add 导致唯一约束冲突）
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
        po.setUpdatedAt(template.getUpdatedAt());

        // 原地更新 starImages：匹配已有 PO 则更新，无匹配则新增，多余的删除
        List<CardStarImage> domainImages = template.getStarImages();
        if (domainImages == null) {
            po.getStarImages().clear();
            return;
        }

        // 第一步：更新已有、新增缺失
        for (CardStarImage img : domainImages) {
            CardStarImagePO existingPO = po.getStarImages().stream()
                    .filter(existing -> existing.getStarLevel() == img.getStarLevel())
                    .findFirst()
                    .orElse(null);
            if (existingPO != null) {
                // 原地更新已有 PO 的 imageUrl
                existingPO.setImageUrl(img.getImageUrl());
            } else {
                // 新增 starLevel 不在现有集合中的图片
                CardStarImagePO imgPO = CardStarImagePO.builder()
                        .starLevel(img.getStarLevel())
                        .imageUrl(img.getImageUrl())
                        .cardTemplate(po)
                        .build();
                po.getStarImages().add(imgPO);
            }
        }

        // 第二步：删除领域模型中已不存在的 starLevel
        po.getStarImages().removeIf(existing ->
                domainImages.stream().noneMatch(
                        img -> img.getStarLevel() == existing.getStarLevel()));
    }
}
