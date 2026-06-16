package com.knowledgegame.core.domain.model.entity;

import com.knowledgegame.core.domain.model.domainenum.KnowledgeCategoryStatus;
import com.knowledgegame.core.domain.model.vo.FileRef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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

    @Test
    void update_shouldOnlyUpdateNonNullFields() {
        FileRef newIcon = FileRef.of(10L, "/static/new.png");
        KnowledgeCategory category = KnowledgeCategory.create(
                "编程", "旧描述", null, ICON, "#FF5500", COVER, 0);

        category.update("新名称", "新描述", newIcon, null, null, 1);

        assertEquals("新名称", category.getName());
        assertEquals("新描述", category.getDescription());
        assertEquals(newIcon, category.getIcon());
        assertEquals("#FF5500", category.getColor());
        assertEquals(COVER, category.getCoverImage());
        assertEquals(1, category.getSortOrder());
    }

    @Test
    void update_shouldKeepSortOrder_whenNull() {
        KnowledgeCategory category = KnowledgeCategory.create(
                "编程", null, null, null, null, null, 5);
        category.update("新名称", null, null, null, null, null);
        assertEquals(5, category.getSortOrder());
    }

    @Test
    void update_shouldKeepAllFields_whenAllNull() {
        KnowledgeCategory category = KnowledgeCategory.create(
                "编程", "描述", null, ICON, "#FF5500", COVER, 3);

        category.update(null, null, null, null, null, null);

        assertEquals("编程", category.getName());
        assertEquals("描述", category.getDescription());
        assertEquals(ICON, category.getIcon());
        assertEquals("#FF5500", category.getColor());
        assertEquals(COVER, category.getCoverImage());
        assertEquals(3, category.getSortOrder());
    }

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

    @Test
    void reconstruct_shouldRestoreAllFields() {
        KnowledgeCategory category = KnowledgeCategory.reconstruct(
                1L, 10L, "Java", "描述", ICON, "#FF5500", COVER, 3,
                KnowledgeCategoryStatus.INACTIVE,
                java.time.LocalDateTime.of(2026, 1, 1, 0, 0),
                java.time.LocalDateTime.of(2026, 6, 1, 0, 0));

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
