package com.knowledgegame.admin.api.assembler;

import com.knowledgegame.admin.api.dto.response.KnowledgeCategoryResponse;
import com.knowledgegame.admin.api.dto.response.KnowledgeCategoryTreeResponse;
import com.knowledgegame.core.domain.model.entity.KnowledgeCategory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 知识点分类领域模型 → DTO 转换器（MapStruct 自动生成实现）
 */
@Mapper
public interface KnowledgeCategoryAssembler {

    KnowledgeCategoryAssembler INSTANCE = Mappers.getMapper(KnowledgeCategoryAssembler.class);

    /**
     * 领域模型转响应 DTO（status 枚举转字符串）
     */
    @Mapping(target = "status", expression = "java(category.getStatus().name())")
    KnowledgeCategoryResponse toResponse(KnowledgeCategory category);

    /**
     * 领域模型转树节点 DTO（不含 children）
     */
    @Mapping(target = "status", expression = "java(category.getStatus().name())")
    @Mapping(target = "children", ignore = true)
    KnowledgeCategoryTreeResponse toTreeNode(KnowledgeCategory category);

    /**
     * 批量转换
     */
    List<KnowledgeCategoryResponse> toResponseList(List<KnowledgeCategory> categories);
}
