package com.knowledgegame.core.domain.model.entity;

import com.knowledgegame.core.domain.model.vo.FileRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class StudyGroupTest {

    private static final FileRef AVATAR = FileRef.of(1L, "https://example.com/avatar.png");

    @Test
    @DisplayName("create() 应正确设置所有字段")
    void create_shouldSetAllFields() {
        StudyGroup group = StudyGroup.create("测试群组", "描述", AVATAR, 100L);

        assertEquals("测试群组", group.getName());
        assertEquals("描述", group.getDescription());
        assertEquals(AVATAR, group.getAvatar());
        assertEquals(100L, group.getOwnerId());
    }

    @Test
    @DisplayName("create() description 为 null 应正常创建")
    void create_shouldAllowNullDescription() {
        StudyGroup group = StudyGroup.create("群组", null, AVATAR, 100L);

        assertNull(group.getDescription());
    }

    @Test
    @DisplayName("create() avatar 为 null 应正常创建")
    void create_shouldAllowNullAvatar() {
        StudyGroup group = StudyGroup.create("群组", "描述", null, 100L);

        assertNull(group.getAvatar());
    }

    @Test
    @DisplayName("create() id 应为 null，createdAt/updatedAt 非空")
    void create_shouldSetTimestampsAndIdNull() {
        LocalDateTime before = LocalDateTime.now();
        StudyGroup group = StudyGroup.create("群组", "描述", AVATAR, 100L);
        LocalDateTime after = LocalDateTime.now();

        assertNull(group.getId());
        assertNotNull(group.getCreatedAt());
        assertNotNull(group.getUpdatedAt());
        assertEquals(false, group.getCreatedAt().isBefore(before));
        assertEquals(false, group.getCreatedAt().isAfter(after));
    }

    @Test
    @DisplayName("reconstruct() 应完整还原所有字段")
    void reconstruct_shouldRestoreAllFields() {
        LocalDateTime created = LocalDateTime.of(2025, 3, 15, 10, 0, 0);
        LocalDateTime updated = LocalDateTime.of(2025, 6, 1, 15, 30, 0);

        StudyGroup group = StudyGroup.reconstruct(
                99L, "重建群组", "重建描述", AVATAR, 100L, created, updated);

        assertEquals(99L, group.getId());
        assertEquals("重建群组", group.getName());
        assertEquals("重建描述", group.getDescription());
        assertEquals(AVATAR, group.getAvatar());
        assertEquals(100L, group.getOwnerId());
        assertEquals(created, group.getCreatedAt());
        assertEquals(updated, group.getUpdatedAt());
    }

    @Test
    @DisplayName("reconstruct() avatar 为 null 应正常还原")
    void reconstruct_shouldAllowNullAvatar() {
        StudyGroup group = StudyGroup.reconstruct(
                1L, "群组", null, null, 100L,
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 1, 1, 0, 0));

        assertNull(group.getAvatar());
        assertNull(group.getDescription());
    }
}
