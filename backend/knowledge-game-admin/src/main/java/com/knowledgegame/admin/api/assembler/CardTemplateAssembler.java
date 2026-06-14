package com.knowledgegame.admin.api.assembler;

import com.knowledgegame.admin.api.dto.response.CardTemplateListResponse;
import com.knowledgegame.admin.api.dto.response.CardTemplateResponse;
import com.knowledgegame.core.domain.model.entity.CardTemplate;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 卡牌模板领域模型 → DTO 转换器（MapStruct 自动生成实现）
 */
@Mapper
public interface CardTemplateAssembler {

    CardTemplateAssembler INSTANCE = Mappers.getMapper(CardTemplateAssembler.class);

    /**
     * 领域模型转详情响应 DTO
     */
    @Mapping(target = "ipSeriesName", source = "ipSeriesName")
    @Mapping(target = "rarity", expression = "java(template.getRarity() != null ? template.getRarity().name() : null)")
    @Mapping(target = "status", expression = "java(template.getStatus() != null ? template.getStatus().name() : null)")
    @Mapping(target = "createdAt", expression = "java(toEpochMilli(template.getCreatedAt()))")
    @Mapping(target = "updatedAt", expression = "java(toEpochMilli(template.getUpdatedAt()))")
    CardTemplateResponse toResponse(CardTemplate template, String ipSeriesName);

    /**
     * 领域模型转列表响应 DTO
     */
    @Mapping(target = "ipSeriesName", source = "ipSeriesName")
    @Mapping(target = "rarity", expression = "java(template.getRarity() != null ? template.getRarity().name() : null)")
    @Mapping(target = "status", expression = "java(template.getStatus() != null ? template.getStatus().name() : null)")
    @Mapping(target = "createdAt", expression = "java(toEpochMilli(template.getCreatedAt()))")
    @Mapping(target = "updatedAt", expression = "java(toEpochMilli(template.getUpdatedAt()))")
    CardTemplateListResponse toListResponse(CardTemplate template, String ipSeriesName);

    /**
     * LocalDateTime 转 epoch 毫秒（UTC）
     */
    default Long toEpochMilli(LocalDateTime dateTime) {
        if (dateTime == null) return null;
        return dateTime.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
    }
}
