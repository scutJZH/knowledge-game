package com.knowledgegame.core.domain.model.entity;

import com.knowledgegame.core.domain.model.domainenum.KnowledgeCategoryStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * KnowledgeCategory 领域实体单元测试
 */
class KnowledgeCategoryTest {

    /**
     * 工厂方法 - 正常创建顶级分类
     */
    @Test
    void create_shouldReturnActiveCategory() {
        KnowledgeCategory category = KnowledgeCategory.create(
                "编程", "编程知识", null,
                "icon.png", "#FF5500", "cover.jpg", 0);

        assertNull(category.getId());
        assertNull(category.getParentId());
        assertEquals("编程", category.getName());
        assertEquals("编程知识", category.getDescription());
        assertEquals("icon.png", category.getIconUrl());
        assertEquals("#FF5500", category.getColor());
        assertEquals("cover.jpg", category.getCoverImageUrl());
        assertEquals(0, category.getSortOrder());
        assertEquals(KnowledgeCategoryStatus.ACTIVE, category.getStatus());
        assertNotNull(category.getCreatedAt());
        assertNotNull(category.getUpdatedAt());
    }

    /**
     * 工厂方法 - 创建子分类
     */
    @Test
    void create_shouldSetParentId() {
        KnowledgeCategory category = KnowledgeCategory.create(
                "Java", null, 1L,
                null, null, null, 0);

        assertEquals(1L, category.getParentId());
    }

    /**
     * 更新 - 部分字段更新（null 不覆盖）
     */
    @Test
    void update_shouldOnlyUpdateNonNullFields() {
        KnowledgeCategory category = KnowledgeCategory.create(
                "编程", "旧描述", null,
                "old.png", "#FF5500", "old.jpg", 0);

        category.update("新名称", "新描述", "new.png", null, null, 1);

        assertEquals("新名称", category.getName());
        assertEquals("新描述", category.getDescription());
        assertEquals("new.png", category.getIconUrl());
        assertEquals("#FF5500", category.getColor());
        assertEquals("old.jpg", category.getCoverImageUrl());
        assertEquals(1, category.getSortOrder());
    }

    /**
     * 更新 - sortOrder 为 null 时不覆盖
     */
    @Test
    void update_shouldKeepSortOrder_whenNull() {
        KnowledgeCategory category = KnowledgeCategory.create(
                "编程", null, null, null, null, null, 5);

        category.update("新名称", null, null, null, null, null);

        assertEquals(5, category.getSortOrder());
    }

    /**
     * 更新 - 所有字段传 null 时不覆盖任何字段
     */
    @Test
    void update_shouldKeepAllFields_whenAllNull() {
        KnowledgeCategory category = KnowledgeCategory.create(
                "编程", "描述", null, "icon.png", "#FF5500", "cover.jpg", 3);

        category.update(null, null, null, null, null, null);

        assertEquals("编程", category.getName());
        assertEquals("描述", category.getDescription());
        assertEquals("icon.png", category.getIconUrl());
        assertEquals("#FF5500", category.getColor());
        assertEquals("cover.jpg", category.getCoverImageUrl());
        assertEquals(3, category.getSortOrder());
    }

    /**
     * 移动 - 更新 parentId
     */
    @Test
    void moveTo_shouldUpdateParentId() {
        KnowledgeCategory category = KnowledgeCategory.create(
                "Java", null, 1L, null, null, null, 0);

        category.moveTo(5L);

        assertEquals(5L, category.getParentId());
    }

    /**
     * 移动 - 移到顶级（parentId = null）
     */
    @Test
    void moveTo_shouldSupportNullParentId() {
        KnowledgeCategory category = KnowledgeCategory.create(
                "Java", null, 1L, null, null, null, 0);

        category.moveTo(null);

        assertNull(category.getParentId());
    }

    /**
     * 软删除 - status 变为 INACTIVE
     */
    @Test
    void deactivate_shouldSetStatusToInactive() {
        KnowledgeCategory category = KnowledgeCategory.create(
                "编程", null, null, null, null, null, 0);

        category.deactivate();

        assertEquals(KnowledgeCategoryStatus.INACTIVE, category.getStatus());
    }

    /**
     * 重建 - 所有字段完整还原
     */
    @Test
    void reconstruct_shouldRestoreAllFields() {
        KnowledgeCategory category = KnowledgeCategory.reconstruct(
                1L, 10L, "Java", "描述", "icon.png",
                "#FF5500", "cover.jpg", 3,
                KnowledgeCategoryStatus.INACTIVE,
                java.time.LocalDateTime.of(2026, 1, 1, 0, 0),
                java.time.LocalDateTime.of(2026, 6, 1, 0, 0));

        assertEquals(1L, category.getId());
        assertEquals(10L, category.getParentId());
        assertEquals("Java", category.getName());
        assertEquals("描述", category.getDescription());
        assertEquals("icon.png", category.getIconUrl());
        assertEquals("#FF5500", category.getColor());
        assertEquals("cover.jpg", category.getCoverImageUrl());
        assertEquals(3, category.getSortOrder());
        assertEquals(KnowledgeCategoryStatus.INACTIVE, category.getStatus());
    }
}
