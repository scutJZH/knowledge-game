package com.knowledgegame.core.domain.model.entity;

import com.knowledgegame.core.domain.model.domainenum.UserRole;
import com.knowledgegame.core.domain.model.vo.FileRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserTest {

    private static final FileRef AVATAR_OLD = FileRef.of(1L, "https://example.com/old-avatar.png");
    private static final FileRef AVATAR_NEW = FileRef.of(2L, "https://example.com/new-avatar.png");

    @Test
    @DisplayName("create() 应正确设置业务字段、角色和时间戳")
    void create_shouldSetAllBusinessFields() {
        LocalDateTime before = LocalDateTime.now();
        User user = User.create("newuser", "$2a$10$hash", "新用户");
        LocalDateTime after = LocalDateTime.now();

        assertNull(user.getId());
        assertEquals("newuser", user.getUsername());
        assertEquals("$2a$10$hash", user.getPasswordHash());
        assertEquals("新用户", user.getNickname());
        assertNull(user.getAvatar());
        assertEquals(UserRole.USER, user.getRole());
        assertNotNull(user.getCreatedAt());
        assertNotNull(user.getUpdatedAt());
        assertEquals(false, user.getCreatedAt().isBefore(before));
        assertEquals(false, user.getCreatedAt().isAfter(after));
    }

    @Test
    @DisplayName("reconstruct() 应完整还原所有字段")
    void reconstruct_shouldRestoreAllFields() {
        LocalDateTime created = LocalDateTime.of(2025, 3, 15, 10, 0, 0);
        LocalDateTime updated = LocalDateTime.of(2025, 6, 1, 15, 30, 0);

        User user = User.reconstruct(
                99L, "reconuser", "$2a$10$hash", "重建用户",
                AVATAR_OLD, UserRole.ADMIN, created, updated);

        assertEquals(99L, user.getId());
        assertEquals("reconuser", user.getUsername());
        assertEquals("$2a$10$hash", user.getPasswordHash());
        assertEquals("重建用户", user.getNickname());
        assertEquals(AVATAR_OLD, user.getAvatar());
        assertEquals(UserRole.ADMIN, user.getRole());
        assertEquals(created, user.getCreatedAt());
        assertEquals(updated, user.getUpdatedAt());
    }

    @Test
    @DisplayName("reconstruct() 传入 null avatar 应正常还原")
    void reconstruct_shouldAllowNullAvatar() {
        User user = User.reconstruct(
                1L, "user", "hash", "昵称",
                null, UserRole.USER,
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 1, 1, 0, 0));

        assertNull(user.getAvatar());
        assertEquals(1L, user.getId());
    }

    @Test
    @DisplayName("updateProfile() 传入非 null 时应更新昵称")
    void updateProfile_shouldWriteNewValue() {
        User user = User.reconstruct(1L, "user", "hash", "旧昵称",
                null, UserRole.USER,
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 1, 1, 0, 0));

        user.updateProfile("新昵称");

        assertEquals("新昵称", user.getNickname());
    }

    @Test
    @DisplayName("updateProfile(null) 应保持原昵称（NOT NULL 保护）")
    void updateProfile_shouldKeepOriginalWhenNull() {
        User user = User.reconstruct(1L, "user", "hash", "原昵称",
                null, UserRole.USER,
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 1, 1, 0, 0));

        user.updateProfile(null);

        assertEquals("原昵称", user.getNickname());
    }

    @Test
    @DisplayName("updateProfile() 应刷新 updatedAt 时间戳")
    void updateProfile_shouldRefreshUpdatedAt() {
        LocalDateTime oldUpdated = LocalDateTime.of(2020, 1, 1, 0, 0);
        User user = User.reconstruct(1L, "user", "hash", "昵称",
                null, UserRole.USER,
                LocalDateTime.of(2020, 1, 1, 0, 0), oldUpdated);

        user.updateProfile("新昵称");

        assertNotNull(user.getUpdatedAt());
        assertNotEquals(oldUpdated, user.getUpdatedAt());
    }

    @Test
    @DisplayName("updateAvatar() 应写入新头像")
    void updateAvatar_shouldWriteNewValue() {
        User user = User.reconstruct(1L, "user", "hash", "昵称",
                AVATAR_OLD, UserRole.USER,
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 1, 1, 0, 0));

        user.updateAvatar(AVATAR_NEW);

        assertEquals(AVATAR_NEW, user.getAvatar());
    }

    @Test
    @DisplayName("updateAvatar(null) 应抛 IllegalArgumentException")
    void updateAvatar_shouldThrowWhenNull() {
        User user = User.reconstruct(1L, "user", "hash", "昵称",
                AVATAR_OLD, UserRole.USER,
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 1, 1, 0, 0));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> user.updateAvatar(null));
        assertEquals("avatar 清空请用 clearAvatar()", ex.getMessage());
    }

    @Test
    @DisplayName("clearAvatar() 应清空头像")
    void clearAvatar_shouldSetNull() {
        User user = User.reconstruct(1L, "user", "hash", "昵称",
                AVATAR_OLD, UserRole.USER,
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 1, 1, 0, 0));

        user.clearAvatar();

        assertNull(user.getAvatar());
    }

    @Test
    @DisplayName("clearAvatar() 应刷新 updatedAt 时间戳")
    void clearAvatar_shouldRefreshUpdatedAt() {
        LocalDateTime oldUpdated = LocalDateTime.of(2020, 1, 1, 0, 0);
        User user = User.reconstruct(1L, "user", "hash", "昵称",
                AVATAR_OLD, UserRole.USER,
                LocalDateTime.of(2020, 1, 1, 0, 0), oldUpdated);

        user.clearAvatar();

        assertNotNull(user.getUpdatedAt());
        assertNotEquals(oldUpdated, user.getUpdatedAt());
    }
}
