package com.knowledgegame.core.domain.model.entity;

import com.knowledgegame.core.domain.model.domainenum.KnowledgeCategoryStatus;
import com.knowledgegame.core.domain.model.vo.FileRef;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KnowledgeCategoryTest {

    private static final FileRef ICON = FileRef.of(1L, "/static/icon.png");
    private static final FileRef COVER = FileRef.of(2L, "/static/cover.jpg");

    @Test
    void create_shouldReturnActiveCategory() {
        KnowledgeCategory category = KnowledgeCategory.create(
                "编程", "编程知识", null, ICON, "#FF5500", COVER, 0);

        assertNull(category.getId());
        assertNull(category.getParentId());
        assertEquals("编程", category.getName());
        assertEquals("编程知识", category.getDescription());
        assertEquals(ICON, category.getIcon());
        assertEquals("#FF5500", category.getColor());
        assertEquals(COVER, category.getCoverImage());
        assertEquals(0, category.getSortOrder());
        assertEquals(KnowledgeCategoryStatus.ACTIVE, category.getStatus());
        assertNotNull(category.getCreatedAt());
        assertNotNull(category.getUpdatedAt());
    }

    @Test
    void create_shouldSetParentId() {
        KnowledgeCategory category = KnowledgeCategory.create(
                "Java", null, 1L, null, null, null, 0);
        assertEquals(1L, category.getParentId());
    }

    // ============ update(String name, Integer sortOrder) 必填字段 ============

    @Test
    void update_shouldUpdateNameAndSortOrderWhenNonNull() {
        KnowledgeCategory category = KnowledgeCategory.create(
                "编程", "描述", null, ICON, "#FF5500", COVER, 0);

        category.update("新名称", 5);

        assertEquals("新名称", category.getName());
        assertEquals(5, category.getSortOrder());
        assertEquals("描述", category.getDescription());
        assertEquals(ICON, category.getIcon());
        assertEquals("#FF5500", category.getColor());
        assertEquals(COVER, category.getCoverImage());
    }

    @Test
    void update_shouldKeepNameAndSortOrderWhenNull() {
        KnowledgeCategory category = KnowledgeCategory.create(
                "编程", "描述", null, ICON, "#FF5500", COVER, 3);

        category.update(null, null);

        assertEquals("编程", category.getName());
        assertEquals(3, category.getSortOrder());
    }

    // ============ updateDescription / clearDescription ============

    @Test
    void updateDescription_shouldWriteNewValue() {
        KnowledgeCategory category = KnowledgeCategory.create(
                "编程", "旧", null, null, null, null, 0);

        category.updateDescription("新描述");

        assertEquals("新描述", category.getDescription());
    }

    @Test
    void updateDescription_shouldThrowWhenNull() {
        KnowledgeCategory category = KnowledgeCategory.create(
                "编程", "旧", null, null, null, null, 0);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> category.updateDescription(null));
        assertEquals("description 清空请用 clearDescription()", ex.getMessage());
    }

    @Test
    void clearDescription_shouldSetNull() {
        KnowledgeCategory category = KnowledgeCategory.create(
                "编程", "旧描述", null, null, null, null, 0);

        category.clearDescription();

        assertNull(category.getDescription());
    }

    // ============ updateColor / clearColor ============

    @Test
    void updateColor_shouldWriteNewValue() {
        KnowledgeCategory category = KnowledgeCategory.create(
                "编程", null, null, null, "#FF5500", null, 0);

        category.updateColor("#00AA00");

        assertEquals("#00AA00", category.getColor());
    }

    @Test
    void updateColor_shouldThrowWhenNull() {
        KnowledgeCategory category = KnowledgeCategory.create(
                "编程", null, null, null, "#FF5500", null, 0);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> category.updateColor(null));
        assertEquals("color 清空请用 clearColor()", ex.getMessage());
    }

    @Test
    void clearColor_shouldSetNull() {
        KnowledgeCategory category = KnowledgeCategory.create(
                "编程", null, null, null, "#FF5500", null, 0);

        category.clearColor();

        assertNull(category.getColor());
    }

    // ============ updateIcon / clearIcon ============

    @Test
    void updateIcon_shouldWriteNewValue() {
        KnowledgeCategory category = KnowledgeCategory.create(
                "编程", null, null, ICON, null, null, 0);
        FileRef newIcon = FileRef.of(99L, "/static/new.png");

        category.updateIcon(newIcon);

        assertEquals(newIcon, category.getIcon());
    }

    @Test
    void updateIcon_shouldThrowWhenNull() {
        KnowledgeCategory category = KnowledgeCategory.create(
                "编程", null, null, ICON, null, null, 0);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> category.updateIcon(null));
        assertEquals("icon 清空请用 clearIcon()", ex.getMessage());
    }

    @Test
    void clearIcon_shouldSetNull() {
        KnowledgeCategory category = KnowledgeCategory.create(
                "编程", null, null, ICON, null, null, 0);

        category.clearIcon();

        assertNull(category.getIcon());
    }

    // ============ updateCoverImage / clearCoverImage ============

    @Test
    void updateCoverImage_shouldWriteNewValue() {
        KnowledgeCategory category = KnowledgeCategory.create(
                "编程", null, null, null, null, COVER, 0);
        FileRef newCover = FileRef.of(88L, "/static/new.jpg");

        category.updateCoverImage(newCover);

        assertEquals(newCover, category.getCoverImage());
    }

    @Test
    void updateCoverImage_shouldThrowWhenNull() {
        KnowledgeCategory category = KnowledgeCategory.create(
                "编程", null, null, null, null, COVER, 0);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> category.updateCoverImage(null));
        assertEquals("coverImage 清空请用 clearCoverImage()", ex.getMessage());
    }

    @Test
    void clearCoverImage_shouldSetNull() {
        KnowledgeCategory category = KnowledgeCategory.create(
                "编程", null, null, null, null, COVER, 0);

        category.clearCoverImage();

        assertNull(category.getCoverImage());
    }

    // ============ updatedAt 守卫 ============

    @Test
    void updateXxx_shouldRefreshUpdatedAt() throws InterruptedException {
        KnowledgeCategory category = KnowledgeCategory.create(
                "编程", null, null, ICON, "#FF5500", COVER, 0);
        LocalDateTime initialUpdatedAt = category.getUpdatedAt();

        Thread.sleep(5);
        category.update("新", 1);
        category.updateDescription("desc");
        category.clearIcon();
        category.clearCoverImage();
        category.clearColor();

        // 至少一次更新发生后 updatedAt 应前进
        assertTrueUpdated(category.getUpdatedAt(), initialUpdatedAt);
    }

    private static void assertTrueUpdated(LocalDateTime after, LocalDateTime before) {
        if (!after.isAfter(before)) {
            throw new AssertionError("updatedAt 未刷新: before=" + before + ", after=" + after);
        }
    }

    // ============ 其他不变 ============

    @Test
    void moveTo_shouldUpdateParentId() {
        KnowledgeCategory category = KnowledgeCategory.create(
                "Java", null, 1L, null, null, null, 0);
        category.moveTo(5L);
        assertEquals(5L, category.getParentId());
    }

    @Test
    void moveTo_shouldSupportNullParentId() {
        KnowledgeCategory category = KnowledgeCategory.create(
                "Java", null, 1L, null, null, null, 0);
        category.moveTo(null);
        assertNull(category.getParentId());
    }

    @Test
    void deactivate_shouldSetStatusToInactive() {
        KnowledgeCategory category = KnowledgeCategory.create(
                "编程", null, null, null, null, null, 0);
        category.deactivate();
        assertEquals(KnowledgeCategoryStatus.INACTIVE, category.getStatus());
    }

    // ============ updateStatus ============

    @Test
    void updateStatus_shouldSetToInactive() {
        KnowledgeCategory category = KnowledgeCategory.create(
                "编程", null, null, null, null, null, 0);
        category.updateStatus(KnowledgeCategoryStatus.INACTIVE);
        assertEquals(KnowledgeCategoryStatus.INACTIVE, category.getStatus());
    }

    @Test
    void updateStatus_shouldSetToActive() {
        KnowledgeCategory category = KnowledgeCategory.create(
                "编程", null, null, null, null, null, 0);
        category.deactivate();
        category.updateStatus(KnowledgeCategoryStatus.ACTIVE);
        assertEquals(KnowledgeCategoryStatus.ACTIVE, category.getStatus());
    }

    @Test
    void updateStatus_shouldThrowWhenNull() {
        KnowledgeCategory category = KnowledgeCategory.create(
                "编程", null, null, null, null, null, 0);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> category.updateStatus(null));
        assertEquals("status must not be null", ex.getMessage());
    }

    @Test
    void updateStatus_shouldRefreshUpdatedAt() throws InterruptedException {
        KnowledgeCategory category = KnowledgeCategory.create(
                "编程", null, null, null, null, null, 0);
        LocalDateTime before = category.getUpdatedAt();
        Thread.sleep(5);
        category.updateStatus(KnowledgeCategoryStatus.INACTIVE);
        assertTrueUpdated(category.getUpdatedAt(), before);
    }

    @Test
    void reconstruct_shouldRestoreAllFields() {
        KnowledgeCategory category = KnowledgeCategory.reconstruct(
                1L, 10L, "Java", "描述", ICON, "#FF5500", COVER, 3,
                KnowledgeCategoryStatus.INACTIVE,
                LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 6, 1, 0, 0));

        assertEquals(1L, category.getId());
        assertEquals(10L, category.getParentId());
        assertEquals("Java", category.getName());
        assertEquals("描述", category.getDescription());
        assertEquals(ICON, category.getIcon());
        assertEquals("#FF5500", category.getColor());
        assertEquals(COVER, category.getCoverImage());
        assertEquals(3, category.getSortOrder());
        assertEquals(KnowledgeCategoryStatus.INACTIVE, category.getStatus());
    }
}
