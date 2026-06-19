package com.knowledgegame.core.infrastructure.db.converter;

import com.knowledgegame.core.domain.model.entity.StudyGroup;
import com.knowledgegame.core.domain.model.vo.FileRef;
import com.knowledgegame.core.infrastructure.db.entity.StudyGroupPO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

/**
 * PO ↔ 领域模型转换器（学习群组，MapStruct 自动生成实现）
 */
@Mapper
public interface StudyGroupConverter {

    StudyGroupConverter INSTANCE = Mappers.getMapper(StudyGroupConverter.class);

    /**
     * PO 转领域模型（使用 reconstruct 工厂方法）
     */
    default StudyGroup toDomain(StudyGroupPO po) {
        if (po == null) {
            return null;
        }
        FileRef avatar = toFileRef(po.getAvatarFileId(), po.getAvatarUrl());
        return StudyGroup.reconstruct(
                po.getId(),
                po.getName(),
                po.getDescription(),
                avatar,
                po.getOwnerId(),
                po.getCreatedAt(),
                po.getUpdatedAt()
        );
    }

    /**
     * 领域模型转 PO（新增用）
     */
    default StudyGroupPO toPO(StudyGroup domain) {
        if (domain == null) {
            return null;
        }
        return StudyGroupPO.builder()
                .name(domain.getName())
                .description(domain.getDescription())
                .avatarFileId(fileIdOf(domain.getAvatar()))
                .avatarUrl(urlOf(domain.getAvatar()))
                .ownerId(domain.getOwnerId())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .build();
    }

    /**
     * 用领域模型更新已有 PO（显式赋值双字段，不依赖 MapStruct IGNORE 策略）
     */
    default void updatePO(@MappingTarget StudyGroupPO po, StudyGroup domain) {
        // name 是 NOT NULL 字段，保留 if-null 守卫作为防御
        if (domain.getName() != null) {
            po.setName(domain.getName());
        }
        // description / avatar 是 nullable，必须无条件写回 null，
        // 否则领域 clearXxx() 调用产生的 null 会被吞掉
        po.setDescription(domain.getDescription());
        po.setAvatarFileId(fileIdOf(domain.getAvatar()));
        po.setAvatarUrl(urlOf(domain.getAvatar()));
        // ownerId 是 NOT NULL 字段，保留 if-null 守卫
        if (domain.getOwnerId() != null) {
            po.setOwnerId(domain.getOwnerId());
        }
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
