package com.knowledgegame.core.infrastructure.db.converter;

import com.knowledgegame.core.domain.model.entity.KnowledgeCategory;
import com.knowledgegame.core.infrastructure.db.entity.KnowledgeCategoryPO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.factory.Mappers;

/**
 * PO ↔ 领域模型转换器（知识点分类，MapStruct 自动生成实现）
 */
@Mapper(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface KnowledgeCategoryConverter {

    KnowledgeCategoryConverter INSTANCE = Mappers.getMapper(KnowledgeCategoryConverter.class);

    /**
     * PO 转领域模型（使用 reconstruct 工厂方法）
     */
    default KnowledgeCategory toDomain(KnowledgeCategoryPO po) {
        if (po == null) {
            return null;
        }
        return KnowledgeCategory.reconstruct(
                po.getId(),
                po.getParentId(),
                po.getName(),
                po.getDescription(),
                po.getIconUrl(),
                po.getColor(),
                po.getCoverImageUrl(),
                po.getSortOrder(),
                po.getStatus(),
                po.getCreatedAt(),
                po.getUpdatedAt()
        );
    }

    /**
     * 领域模型转 PO（新增）
     */
    KnowledgeCategoryPO toPO(KnowledgeCategory category);

    /**
     * 用领域模型更新已有 PO（忽略 null 字段）
     */
    void updatePO(@MappingTarget KnowledgeCategoryPO po, KnowledgeCategory category);
}
