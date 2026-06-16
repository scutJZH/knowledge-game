package com.knowledgegame.core.infrastructure.db.converter;

import com.knowledgegame.core.domain.model.entity.User;
import com.knowledgegame.core.domain.model.vo.FileRef;
import com.knowledgegame.core.infrastructure.db.entity.UserPO;

public class UserConverter {

    public static User toDomain(UserPO po) {
        if (po == null) return null;
        FileRef avatar = toFileRef(po.getAvatarFileId(), po.getAvatar());
        return User.reconstruct(
                po.getId(), po.getUsername(), po.getPasswordHash(),
                po.getNickname(), avatar, po.getRole(),
                po.getCreatedAt(), po.getUpdatedAt());
    }

    public static UserPO toPO(User user) {
        if (user == null) return null;
        return UserPO.builder()
                .username(user.getUsername())
                .passwordHash(user.getPasswordHash())
                .nickname(user.getNickname())
                .avatarFileId(fileIdOf(user.getAvatar()))
                .avatar(urlOf(user.getAvatar()))
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    public static void updatePO(UserPO po, User user) {
        po.setUsername(user.getUsername());
        po.setPasswordHash(user.getPasswordHash());
        po.setNickname(user.getNickname());
        po.setAvatarFileId(fileIdOf(user.getAvatar()));
        po.setAvatar(urlOf(user.getAvatar()));
        po.setRole(user.getRole());
        po.setUpdatedAt(user.getUpdatedAt());
    }

    private static FileRef toFileRef(Long fileId, String url) {
        if (fileId == null && url == null) return null;
        return FileRef.of(fileId, url);
    }

    private static Long fileIdOf(FileRef ref) {
        return ref != null ? ref.fileId() : null;
    }

    private static String urlOf(FileRef ref) {
        return ref != null ? ref.url() : null;
    }
}
