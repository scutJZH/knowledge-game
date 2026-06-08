package com.knowledgegame.core.domain.model.entity;

import com.knowledgegame.core.domain.model.domainenum.UserRole;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 用户领域实体（无框架注解）
 */
@Getter
public class User {

    private Long id;
    private String username;
    private String passwordHash;
    private String nickname;
    private String avatar;
    private UserRole role;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 创建新用户（工厂方法）
     */
    public static User create(String username, String passwordHash, String nickname) {
        User user = new User();
        user.username = username;
        user.passwordHash = passwordHash;
        user.nickname = nickname;
        user.role = UserRole.USER;
        user.createdAt = LocalDateTime.now();
        user.updatedAt = LocalDateTime.now();
        return user;
    }

    /**
     * 从持久化重建（用于 Repository 加载）
     */
    public static User reconstruct(Long id, String username, String passwordHash, String nickname,
                                   String avatar, UserRole role,
                                   LocalDateTime createdAt, LocalDateTime updatedAt) {
        User user = new User();
        user.id = id;
        user.username = username;
        user.passwordHash = passwordHash;
        user.nickname = nickname;
        user.avatar = avatar;
        user.role = role;
        user.createdAt = createdAt;
        user.updatedAt = updatedAt;
        return user;
    }

    /**
     * 更新用户信息
     */
    public void updateProfile(String nickname, String avatar) {
        this.nickname = nickname;
        this.avatar = avatar;
        this.updatedAt = LocalDateTime.now();
    }
}
