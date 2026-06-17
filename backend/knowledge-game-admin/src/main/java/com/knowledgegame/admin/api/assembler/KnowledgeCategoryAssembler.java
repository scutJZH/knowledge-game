package com.knowledgegame.admin.api.assembler;

import com.knowledgegame.admin.api.dto.response.KnowledgeCategoryResponse;
import com.knowledgegame.admin.api.dto.response.KnowledgeCategoryTreeResponse;
import com.knowledgegame.core.domain.model.entity.KnowledgeCategory;
import com.knowledgegame.core.domain.model.vo.FileRef;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

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
    KnowledgeCategoryResponse toResponse(KnowledgeCategory category);

    @Mapping(target = "iconUrl", expression = "java(urlOf(category.getIcon()))")
    @Mapping(target = "status", expression = "java(category.getStatus().name())")
    @Mapping(target = "children", ignore = true)
    KnowledgeCategoryTreeResponse toTreeNode(KnowledgeCategory category);

    List<KnowledgeCategoryResponse> toResponseList(List<KnowledgeCategory> categories);

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
