package com.knowledgegame.core.infrastructure.db.converter;

import com.knowledgegame.core.domain.model.entity.User;
import com.knowledgegame.core.infrastructure.db.entity.UserPO;

/**
 * PO ↔ 领域模型转换器
 */
public class UserConverter {

    /**
     * PO 转领域模型
     */
    public static User toDomain(UserPO po) {
        if (po == null) {
            return null;
        }
        return User.reconstruct(
                po.getId(),
                po.getUsername(),
                po.getPasswordHash(),
                po.getNickname(),
                po.getAvatar(),
                po.getRole(),
                po.getCreatedAt(),
                po.getUpdatedAt()
        );
    }

    /**
     * 领域模型转 PO（新增）
     */
    public static UserPO toPO(User user) {
        if (user == null) {
            return null;
        }
        return UserPO.builder()
                .username(user.getUsername())
                .passwordHash(user.getPasswordHash())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    /**
     * 用领域模型更新已有 PO
     */
    public static void updatePO(UserPO po, User user) {
        po.setUsername(user.getUsername());
        po.setPasswordHash(user.getPasswordHash());
        po.setNickname(user.getNickname());
        po.setAvatar(user.getAvatar());
        po.setRole(user.getRole());
        po.setUpdatedAt(user.getUpdatedAt());
    }
}
