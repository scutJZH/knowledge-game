package com.knowledgegame.core.infrastructure.db.converter;

import com.knowledgegame.core.domain.model.entity.KnowledgeCategory;
import com.knowledgegame.core.domain.model.vo.FileRef;
import com.knowledgegame.core.infrastructure.db.entity.KnowledgeCategoryPO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

/**
 * PO ↔ 领域模型转换器（知识点分类）
 */
@Mapper
public interface KnowledgeCategoryConverter {

    KnowledgeCategoryConverter INSTANCE = Mappers.getMapper(KnowledgeCategoryConverter.class);

    default KnowledgeCategory toDomain(KnowledgeCategoryPO po) {
        if (po == null) {
            return null;
        }
        FileRef icon = toFileRef(po.getIconFileId(), po.getIconUrl());
        FileRef cover = toFileRef(po.getCoverImageFileId(), po.getCoverImageUrl());
        return KnowledgeCategory.reconstruct(
                po.getId(), po.getParentId(), po.getName(), po.getDescription(),
                icon, po.getColor(), cover,
                po.getSortOrder(), po.getStatus(),
                po.getCreatedAt(), po.getUpdatedAt());
    }

    default KnowledgeCategoryPO toPO(KnowledgeCategory domain) {
        if (domain == null) {
            return null;
        }
        return KnowledgeCategoryPO.builder()
                .parentId(domain.getParentId())
                .name(domain.getName())
                .description(domain.getDescription())
                .iconFileId(fileIdOf(domain.getIcon()))
                .iconUrl(urlOf(domain.getIcon()))
                .color(domain.getColor())
                .coverImageFileId(fileIdOf(domain.getCoverImage()))
                .coverImageUrl(urlOf(domain.getCoverImage()))
                .sortOrder(domain.getSortOrder())
                .status(domain.getStatus())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .build();
    }

    default void updatePO(@MappingTarget KnowledgeCategoryPO po, KnowledgeCategory domain) {
        if (domain.getName() != null) {
            po.setName(domain.getName());
        }
        if (domain.getDescription() != null) {
            po.setDescription(domain.getDescription());
        }
        if (domain.getIcon() != null) {
            po.setIconFileId(fileIdOf(domain.getIcon()));
            po.setIconUrl(urlOf(domain.getIcon()));
        }
        if (domain.getColor() != null) {
            po.setColor(domain.getColor());
        }
        if (domain.getCoverImage() != null) {
            po.setCoverImageFileId(fileIdOf(domain.getCoverImage()));
            po.setCoverImageUrl(urlOf(domain.getCoverImage()));
        }
        if (domain.getStatus() != null) {
            po.setStatus(domain.getStatus());
        }
        // sortOrder 是 int 基本类型（无 null 语义），无条件写回。
        // AppService.update/batchSort 调用 category.update() 时若 sortOrder 未变会跳过 setter，
        // domain.sortOrder 保持原值；此时写回等于原值，无副作用。
        // 关键场景：编辑排序号、拖拽 batch-sort、move 后自动重排 都依赖此行生效。
        po.setSortOrder(domain.getSortOrder());
        po.setUpdatedAt(domain.getUpdatedAt());
    }

    default FileRef toFileRef(Long fileId, String url) {
        if (fileId == null && url == null) {
            return null;
        }
        return FileRef.of(fileId, url);
    }

    default Long fileIdOf(FileRef ref) {
        return ref != null ? ref.fileId() : null;
    }

    default String urlOf(FileRef ref) {
        return ref != null ? ref.url() : null;
    }
}
