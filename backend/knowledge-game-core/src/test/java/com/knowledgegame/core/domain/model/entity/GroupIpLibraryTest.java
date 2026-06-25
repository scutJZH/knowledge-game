package com.knowledgegame.core.domain.model.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class GroupIpLibraryTest {

    @Test
    @DisplayName("create() 应设置 groupId / ipSeriesId / addedAt")
    void create_shouldSetFields() {
        GroupIpLibrary item = GroupIpLibrary.create(1L, 10L);

        assertNull(item.getId());
        assertEquals(1L, item.getGroupId());
        assertEquals(10L, item.getIpSeriesId());
        assertNotNull(item.getAddedAt());
    }

    @Test
    @DisplayName("create() addedAt 应在调用前后时间之间")
    void create_addedAtShouldBeWithinNow() {
        LocalDateTime before = LocalDateTime.now();
        GroupIpLibrary item = GroupIpLibrary.create(1L, 10L);
        LocalDateTime after = LocalDateTime.now();

        assertEquals(false, item.getAddedAt().isBefore(before));
        assertEquals(false, item.getAddedAt().isAfter(after));
    }

    @Test
    @DisplayName("reconstruct() 应完整还原所有字段")
    void reconstruct_shouldRestoreAllFields() {
        LocalDateTime addedAt = LocalDateTime.of(2025, 6, 1, 12, 0, 0);

        GroupIpLibrary item = GroupIpLibrary.reconstruct(
                99L, 1L, 10L, addedAt);

        assertEquals(99L, item.getId());
        assertEquals(1L, item.getGroupId());
        assertEquals(10L, item.getIpSeriesId());
        assertEquals(addedAt, item.getAddedAt());
    }
}
