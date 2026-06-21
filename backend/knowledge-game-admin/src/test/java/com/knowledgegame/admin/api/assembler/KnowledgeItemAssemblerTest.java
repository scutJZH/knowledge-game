package com.knowledgegame.admin.api.assembler;

import com.knowledgegame.admin.api.dto.response.KnowledgeItemListResponse;
import com.knowledgegame.core.domain.model.domainenum.KnowledgeItemStatus;
import com.knowledgegame.core.domain.model.vo.FileRef;
import com.knowledgegame.core.domain.model.vo.KnowledgeItemSummary;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * KnowledgeItemAssembler 单元测试
 */
class KnowledgeItemAssemblerTest {

    /**
     * toListResponse(KnowledgeItemSummary) — 基本转换（无 categoryIds）
     */
    @Test
    void toListResponse_shouldConvertSummary() {
        LocalDateTime now = LocalDateTime.now();
        KnowledgeItemSummary summary = KnowledgeItemSummary.reconstruct(
                1L, "标题",
                FileRef.of(1L, "https://example.com/cover.png"),
                List.of("Java", "Spring"),
                5, KnowledgeItemStatus.ACTIVE,
                now, now
        );

        KnowledgeItemListResponse response = KnowledgeItemAssembler.INSTANCE.toListResponse(summary);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("标题", response.getTitle());
        assertEquals(1L, response.getCoverImageFileId());
        assertEquals("https://example.com/cover.png", response.getCoverImageUrl());
        assertEquals(List.of("Java", "Spring"), response.getTags());
        assertNull(response.getCategoryIds());
        assertEquals(5, response.getSortOrder());
        assertEquals("ACTIVE", response.getStatus());
        assertEquals(now.atZone(ZoneOffset.UTC).toInstant().toEpochMilli(), response.getCreatedAt());
        assertEquals(now.atZone(ZoneOffset.UTC).toInstant().toEpochMilli(), response.getUpdatedAt());
    }

    /**
     * toListResponse(KnowledgeItemSummary, categoryIds) — 含分类 ID
     */
    @Test
    void toListResponse_shouldIncludeCategoryIds() {
        LocalDateTime now = LocalDateTime.now();
        KnowledgeItemSummary summary = KnowledgeItemSummary.reconstruct(
                1L, "标题", null, null, 0,
                KnowledgeItemStatus.ACTIVE, now, now
        );

        KnowledgeItemListResponse response = KnowledgeItemAssembler.INSTANCE.toListResponse(
                summary, List.of(10L, 20L));

        assertEquals(List.of(10L, 20L), response.getCategoryIds());
    }

    /**
     * toListResponse — coverImage 为 null 时 fileId/url 为 null
     */
    @Test
    void toListResponse_shouldHandleNullCoverImage() {
        LocalDateTime now = LocalDateTime.now();
        KnowledgeItemSummary summary = KnowledgeItemSummary.reconstruct(
                1L, "标题", null, null, 0,
                KnowledgeItemStatus.ACTIVE, now, now
        );

        KnowledgeItemListResponse response = KnowledgeItemAssembler.INSTANCE.toListResponse(summary);

        assertNull(response.getCoverImageFileId());
        assertNull(response.getCoverImageUrl());
    }
}
