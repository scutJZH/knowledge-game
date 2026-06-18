package com.knowledgegame.core.domain.model.entity;

import com.knowledgegame.core.domain.model.domainenum.KnowledgeItemStatus;
import com.knowledgegame.core.domain.model.vo.FileRef;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * KnowledgeItem 领域实体单元测试
 */
class KnowledgeItemTest {

    /**
     * 创建 - 正常
     */
    @Test
    void create_shouldSucceed() {
        KnowledgeItem item = KnowledgeItem.create(
                "测试标题", "Markdown 内容",
                FileRef.of(1L, "https://example.com/cover.png"),
                List.of("Java"), 0
        );

        assertNotNull(item);
        assertEquals("测试标题", item.getTitle());
        assertEquals("Markdown 内容", item.getContent());
        assertEquals(1L, item.getCoverImage().fileId());
        assertEquals(List.of("Java"), item.getTags());
        assertEquals(0, item.getSortOrder());
        assertEquals(KnowledgeItemStatus.ACTIVE, item.getStatus());
    }

    /**
     * 创建 - coverImage 为 null 正常
     */
    @Test
    void create_shouldSucceed_whenCoverImageNull() {
        KnowledgeItem item = KnowledgeItem.create(
                "标题", "内容", null, null, 0
        );

        assertNull(item.getCoverImage());
    }

    /**
     * 创建 - 验证时间戳被设置
     */
    @Test
    void create_shouldSetTimestamps() {
        LocalDateTime before = LocalDateTime.now();

        KnowledgeItem item = KnowledgeItem.create(
                "标题", "内容", null, null, 0
        );

        LocalDateTime after = LocalDateTime.now();

        assertNotNull(item.getCreatedAt());
        assertNotNull(item.getUpdatedAt());
        assertTrue(!item.getCreatedAt().isBefore(before));
        assertTrue(!item.getCreatedAt().isAfter(after));
    }

    /**
     * reconstruct - 从持久化重建
     */
    @Test
    void reconstruct_shouldRestore() {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
        KnowledgeItem item = KnowledgeItem.reconstruct(
                1L, "标题", "内容", "<p>内容</p>",
                FileRef.of(1L, "https://example.com/cover.png"),
                List.of("Java"), 5,
                KnowledgeItemStatus.ACTIVE, now, now
        );

        assertEquals(1L, item.getId());
        assertEquals("标题", item.getTitle());
        assertEquals("内容", item.getContent());
        assertEquals("<p>内容</p>", item.getContentHtml());
        assertEquals(1L, item.getCoverImage().fileId());
        assertEquals(5, item.getSortOrder());
        assertEquals(KnowledgeItemStatus.ACTIVE, item.getStatus());
    }

    /**
     * reconstruct - INACTIVE 状态
     */
    @Test
    void reconstruct_shouldRestoreInactiveStatus() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 1, 12, 0);
        KnowledgeItem item = KnowledgeItem.reconstruct(
                99L, "标题", "内容", null, null, List.of(),
                0, KnowledgeItemStatus.INACTIVE, now, now
        );

        assertEquals(KnowledgeItemStatus.INACTIVE, item.getStatus());
    }

    /**
     * update - 更新所有字段
     */
    @Test
    void update_shouldModifyFields() {
        KnowledgeItem item = KnowledgeItem.create(
                "旧标题", "旧内容", null, List.of("旧标签"), 0
        );

        item.update("新标题", "新内容",
                FileRef.of(2L, "https://example.com/new.png"),
                List.of("新标签"), 10);

        assertEquals("新标题", item.getTitle());
        assertEquals("新内容", item.getContent());
        assertEquals(2L, item.getCoverImage().fileId());
        assertEquals(List.of("新标签"), item.getTags());
        assertEquals(10, item.getSortOrder());
    }

    /**
     * update - null 字段不修改原值
     */
    @Test
    void update_shouldNotModify_whenNullFields() {
        KnowledgeItem item = KnowledgeItem.create(
                "原始标题", "原始内容",
                FileRef.of(1L, "https://example.com/cover.png"),
                List.of("原始标签"), 0
        );

        item.update(null, null, null, null, null);

        assertEquals("原始标题", item.getTitle());
        assertEquals("原始内容", item.getContent());
        assertEquals(1L, item.getCoverImage().fileId());
        assertEquals(List.of("原始标签"), item.getTags());
        assertEquals(0, item.getSortOrder());
    }

    /**
     * update - 仅更新部分字段
     */
    @Test
    void update_shouldModifyPartialFields() {
        KnowledgeItem item = KnowledgeItem.create(
                "原始标题", "原始内容", null, List.of("原始标签"), 0
        );

        item.update("新标题", null, null, null, null);

        assertEquals("新标题", item.getTitle());
        assertEquals("原始内容", item.getContent());
    }

    /**
     * update - 传入空标签列表覆盖原标签
     */
    @Test
    void update_shouldReplaceTags_whenEmptyList() {
        KnowledgeItem item = KnowledgeItem.create(
                "标题", "内容", null, List.of("旧标签"), 0
        );

        item.update(null, null, null, List.of(), null);

        assertEquals(List.of(), item.getTags());
    }

    /**
     * update - coverImage=null 保留原封面图
     */
    @Test
    void update_shouldPreserveCoverImage_whenNull() {
        FileRef original = FileRef.of(1L, "https://example.com/cover.png");
        KnowledgeItem item = KnowledgeItem.create(
                "标题", "内容", original, List.of("标签"), 0
        );

        item.update(null, null, null, null, null);

        assertEquals(original, item.getCoverImage());
    }

    /**
     * moveToSortOrder - 更新排序号
     */
    @Test
    void moveToSortOrder_shouldUpdateSortOrder() {
        KnowledgeItem item = KnowledgeItem.create(
                "标题", "内容", null, null, 0
        );

        item.moveToSortOrder(5);

        assertEquals(5, item.getSortOrder());
    }

    /**
     * deactivate - 软删除
     */
    @Test
    void deactivate_shouldSetInactive() {
        KnowledgeItem item = KnowledgeItem.create(
                "标题", "内容", null, null, 0
        );

        item.deactivate();

        assertEquals(KnowledgeItemStatus.INACTIVE, item.getStatus());
    }

    /**
     * activate - 重新启用
     */
    @Test
    void activate_shouldSetActive() {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
        KnowledgeItem item = KnowledgeItem.reconstruct(
                1L, "标题", "内容", null, null, List.of(),
                0, KnowledgeItemStatus.INACTIVE, now, now
        );

        item.activate();

        assertEquals(KnowledgeItemStatus.ACTIVE, item.getStatus());
    }

    /**
     * updateContentHtml - 更新渲染后 HTML
     */
    @Test
    void updateContentHtml_shouldSetContentHtml() {
        KnowledgeItem item = KnowledgeItem.create(
                "标题", "内容", null, null, 0
        );

        item.updateContentHtml("<h1>内容</h1>");

        assertEquals("<h1>内容</h1>", item.getContentHtml());
    }
}
