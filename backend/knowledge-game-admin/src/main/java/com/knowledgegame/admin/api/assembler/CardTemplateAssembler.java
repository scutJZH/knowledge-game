package com.knowledgegame.admin.api.assembler;

import com.knowledgegame.admin.api.dto.response.CardTemplateListResponse;
import com.knowledgegame.admin.api.dto.response.CardTemplateResponse;
import com.knowledgegame.admin.api.dto.response.StarImageResponse;
import com.knowledgegame.domain.model.entity.CardTemplate;
import com.knowledgegame.domain.model.vo.CardStarImage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

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
    CardTemplateResponse toResponse(CardTemplate template, String ipSeriesName);

    /**
     * 领域模型转列表响应 DTO（不含 starImages）
     */
    @Mapping(target = "ipSeriesName", source = "ipSeriesName")
    @Mapping(target = "rarity", expression = "java(template.getRarity() != null ? template.getRarity().name() : null)")
    @Mapping(target = "status", expression = "java(template.getStatus() != null ? template.getStatus().name() : null)")
    CardTemplateListResponse toListResponse(CardTemplate template, String ipSeriesName);

    /**
     * 值对象 CardStarImage → StarImageResponse
     */
    StarImageResponse toStarImageResponse(CardStarImage img);
}
