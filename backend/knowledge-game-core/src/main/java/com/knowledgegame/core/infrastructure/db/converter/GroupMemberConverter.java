package com.knowledgegame.core.infrastructure.db.converter;

import com.knowledgegame.core.domain.model.entity.GroupMember;
import com.knowledgegame.core.infrastructure.db.entity.GroupMemberPO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

/**
 * PO ↔ 领域模型转换器（群组成员，MapStruct 自动生成实现）
 */
@Mapper
public interface GroupMemberConverter {

    GroupMemberConverter INSTANCE = Mappers.getMapper(GroupMemberConverter.class);

    /**
     * PO 转领域模型（使用 reconstruct 工厂方法）
     */
    default GroupMember toDomain(GroupMemberPO po) {
        if (po == null) {
            return null;
        }
        return GroupMember.reconstruct(
                po.getId(),
                po.getGroupId(),
                po.getUserId(),
                po.getRole(),
                po.getPoints(),
                po.getJoinedAt()
        );
    }

    /**
     * 领域模型转 PO（新增用）
     */
    default GroupMemberPO toPO(GroupMember domain) {
        if (domain == null) {
            return null;
        }
        return GroupMemberPO.builder()
                .groupId(domain.getGroupId())
                .userId(domain.getUserId())
                .role(domain.getRole())
                .points(domain.getPoints())
                .joinedAt(domain.getJoinedAt())
                .build();
    }

    /**
     * 用领域模型更新已有 PO
     */
    default void updatePO(@MappingTarget GroupMemberPO po, GroupMember domain) {
        if (domain.getGroupId() != null) {
            po.setGroupId(domain.getGroupId());
        }
        if (domain.getUserId() != null) {
            po.setUserId(domain.getUserId());
        }
        if (domain.getRole() != null) {
            po.setRole(domain.getRole());
        }
        // points 是 int 基本类型，直接设值
        po.setPoints(domain.getPoints());
        if (domain.getJoinedAt() != null) {
            po.setJoinedAt(domain.getJoinedAt());
        }
    }
}
