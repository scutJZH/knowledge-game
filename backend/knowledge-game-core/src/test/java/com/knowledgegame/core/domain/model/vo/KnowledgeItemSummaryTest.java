package com.knowledgegame.core.domain.model.vo;

import com.knowledgegame.core.domain.model.domainenum.KnowledgeItemStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * KnowledgeItemSummary 值对象单元测试
 */
class KnowledgeItemSummaryTest {

    /**
     * reconstruct 工厂方法 — 全部字段正确赋值
     */
    @Test
    void reconstruct_shouldSetAllFields() {
        LocalDateTime now = LocalDateTime.now();
        FileRef coverImage = FileRef.of(1L, "https://example.com/cover.png");

        KnowledgeItemSummary summary = KnowledgeItemSummary.reconstruct(
                1L, "标题", coverImage, List.of("Java", "Spring"),
                5, KnowledgeItemStatus.ACTIVE, now, now
        );

        assertNotNull(summary);
        assertEquals(1L, summary.getId());
        assertEquals("标题", summary.getTitle());
        assertEquals(coverImage, summary.getCoverImage());
        assertEquals(List.of("Java", "Spring"), summary.getTags());
        assertEquals(5, summary.getSortOrder());
        assertEquals(KnowledgeItemStatus.ACTIVE, summary.getStatus());
        assertEquals(now, summary.getCreatedAt());
        assertEquals(now, summary.getUpdatedAt());
    }

    /**
     * tags 为 null 时保持 null（与 KnowledgeItem 语义一致：null = 未设置标签）
     */
    @Test
    void reconstruct_shouldAllowNullTags() {
        LocalDateTime now = LocalDateTime.now();

        KnowledgeItemSummary summary = KnowledgeItemSummary.reconstruct(
                1L, "标题", null, null,
                0, KnowledgeItemStatus.ACTIVE, now, now
        );

        assertNull(summary.getTags());
    }

    /**
     * coverImage 为 null 时保持不变
     */
    @Test
    void reconstruct_shouldAllowNullCoverImage() {
        LocalDateTime now = LocalDateTime.now();

        KnowledgeItemSummary summary = KnowledgeItemSummary.reconstruct(
                1L, "标题", null, List.of("Java"),
                0, KnowledgeItemStatus.ACTIVE, now, now
        );

        assertNull(summary.getCoverImage());
    }
}
