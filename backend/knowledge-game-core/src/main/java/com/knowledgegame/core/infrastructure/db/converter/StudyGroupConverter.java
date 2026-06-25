package com.knowledgegame.core.infrastructure.db.converter;

import com.knowledgegame.core.domain.model.domainenum.JoinPolicy;
import com.knowledgegame.core.domain.model.entity.StudyGroup;
import com.knowledgegame.core.domain.model.vo.FileRef;
import com.knowledgegame.core.domain.model.vo.InviteCode;
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
        InviteCode inviteCode = toInviteCode(po.getInviteCode());
        return StudyGroup.reconstruct(
                po.getId(),
                po.getName(),
                po.getDescription(),
                avatar,
                po.getOwnerId(),
                po.getStatus(),
                po.getJoinPolicy(),
                inviteCode,
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
                .status(domain.getStatus())
                .joinPolicy(domain.getJoinPolicy())
                .inviteCode(inviteCodeOf(domain.getInviteCode()))
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .build();
    }

    /**
     * 用领域模型更新已有 PO（显式赋值，不依赖 MapStruct IGNORE 策略）
     */
    default void updatePO(@MappingTarget StudyGroupPO po, StudyGroup domain) {
        if (domain.getName() != null) {
            po.setName(domain.getName());
        }
        po.setDescription(domain.getDescription());
        po.setAvatarFileId(fileIdOf(domain.getAvatar()));
        po.setAvatarUrl(urlOf(domain.getAvatar()));
        if (domain.getOwnerId() != null) {
            po.setOwnerId(domain.getOwnerId());
        }
        // joinPolicy 是 NOT NULL 字段，保留 if-null 守卫
        if (domain.getJoinPolicy() != null) {
            po.setJoinPolicy(domain.getJoinPolicy());
        }
        // status 是 NOT NULL 字段，保留 if-null 守卫
        if (domain.getStatus() != null) {
            po.setStatus(domain.getStatus());
        }
        // inviteCode 是 NOT NULL 字段，保留 if-null 守卫
        if (domain.getInviteCode() != null) {
            po.setInviteCode(domain.getInviteCode().getValue());
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

    default InviteCode toInviteCode(String value) {
        if (value == null) {
            return null;
        }
        return InviteCode.of(value);
    }

    default String inviteCodeOf(InviteCode inviteCode) {
        return inviteCode != null ? inviteCode.getValue() : null;
    }
}
