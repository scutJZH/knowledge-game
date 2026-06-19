package com.knowledgegame.app.api.assembler;

import com.knowledgegame.app.api.dto.response.KnowledgeCategoryTreeResponse;
import com.knowledgegame.core.domain.model.entity.KnowledgeCategory;
import com.knowledgegame.core.domain.model.vo.FileRef;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 知识点分类 MapStruct Assembler（用户端，领域实体 → DTO）
 */
@Mapper
public interface KnowledgeCategoryAssembler {

    KnowledgeCategoryAssembler INSTANCE = Mappers.getMapper(KnowledgeCategoryAssembler.class);

    @Mapping(target = "iconFileId", expression = "java(fileIdOf(category.getIcon()))")
    @Mapping(target = "iconUrl", expression = "java(urlOf(category.getIcon()))")
    @Mapping(target = "coverImageFileId", expression = "java(fileIdOf(category.getCoverImage()))")
    @Mapping(target = "coverImageUrl", expression = "java(urlOf(category.getCoverImage()))")
    @Mapping(target = "status", expression = "java(category.getStatus().name())")
    @Mapping(target = "createdAt", expression = "java(toEpochMilli(category.getCreatedAt()))")
    @Mapping(target = "updatedAt", expression = "java(toEpochMilli(category.getUpdatedAt()))")
    @Mapping(target = "children", ignore = true)
    KnowledgeCategoryTreeResponse toTreeNode(KnowledgeCategory category);

    default Long toEpochMilli(LocalDateTime dateTime) {
        if (dateTime == null) return null;
        return dateTime.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    default Long fileIdOf(FileRef ref) {
        return ref != null ? ref.fileId() : null;
    }

    default String urlOf(FileRef ref) {
        return ref != null ? ref.url() : null;
    }
}
