package com.knowledgegame.admin.api.assembler;

import com.knowledgegame.admin.api.dto.response.KnowledgeItemResponse;
import com.knowledgegame.core.domain.model.entity.KnowledgeItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 知识条目领域模型 → DTO 转换器
 */
@Mapper
public interface KnowledgeItemAssembler {

    KnowledgeItemAssembler INSTANCE = Mappers.getMapper(KnowledgeItemAssembler.class);

    /**
     * 领域模型转响应 DTO
     */
    @Mapping(target = "coverImageFileId", expression = "java(item.getCoverImage() != null ? item.getCoverImage().fileId() : null)")
    @Mapping(target = "coverImageUrl", expression = "java(item.getCoverImage() != null ? item.getCoverImage().url() : null)")
    @Mapping(target = "status", expression = "java(item.getStatus().name())")
    @Mapping(target = "categoryIds", ignore = true)
    @Mapping(target = "createdAt", expression = "java(toEpochMilli(item.getCreatedAt()))")
    @Mapping(target = "updatedAt", expression = "java(toEpochMilli(item.getUpdatedAt()))")
    KnowledgeItemResponse toResponse(KnowledgeItem item);

    /**
     * 领域模型转响应 DTO（含分类 ID 列表）
     */
    default KnowledgeItemResponse toResponse(KnowledgeItem item, List<Long> categoryIds) {
        KnowledgeItemResponse response = toResponse(item);
        return KnowledgeItemResponse.builder()
                .id(response.getId())
                .title(response.getTitle())
                .content(response.getContent())
                .contentHtml(response.getContentHtml())
                .coverImageFileId(response.getCoverImageFileId())
                .coverImageUrl(response.getCoverImageUrl())
                .tags(response.getTags())
                .categoryIds(categoryIds)
                .sortOrder(response.getSortOrder())
                .status(response.getStatus())
                .createdAt(response.getCreatedAt())
                .updatedAt(response.getUpdatedAt())
                .build();
    }

    /**
     * LocalDateTime 转 epoch 毫秒（UTC）
     */
    default Long toEpochMilli(LocalDateTime dateTime) {
        if (dateTime == null) return null;
        return dateTime.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
    }
}
