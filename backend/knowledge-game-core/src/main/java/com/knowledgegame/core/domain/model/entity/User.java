package com.knowledgegame.core.domain.model.entity;

import com.knowledgegame.core.domain.model.domainenum.UserRole;
import com.knowledgegame.core.domain.model.vo.FileRef;
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
    private FileRef avatar;
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
                                   FileRef avatar, UserRole role,
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
     * 更新 nickname（必填，NOT NULL）。nickname 传 null 时保持原值（不更新）
     * <p>
     * 注意：当前方法接收 String nickname 而非 JsonNullable，因为 nickname 是必填字段
     * 不需要"清空"语义。AppService 应在调用此方法前用 if-null 守卫避免误清空。
     */
    public void updateProfile(String nickname) {
        if (nickname != null) {
            this.nickname = nickname;
        }
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 更新头像（清空请用 clearAvatar）
     *
     * @throws IllegalArgumentException avatar 为 null 时抛出
     */
    public void updateAvatar(FileRef avatar) {
        if (avatar == null) {
            throw new IllegalArgumentException("avatar 清空请用 clearAvatar()");
        }
        this.avatar = avatar;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 清空头像
     */
    public void clearAvatar() {
        this.avatar = null;
        this.updatedAt = LocalDateTime.now();
    }
}
