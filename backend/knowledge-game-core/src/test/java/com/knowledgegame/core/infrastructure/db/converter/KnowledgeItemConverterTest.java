package com.knowledgegame.core.infrastructure.db.converter;

import com.knowledgegame.core.domain.model.domainenum.KnowledgeItemStatus;
import com.knowledgegame.core.domain.model.entity.KnowledgeItem;
import com.knowledgegame.core.domain.model.vo.FileRef;
import com.knowledgegame.core.domain.model.vo.KnowledgeItemSummary;
import com.knowledgegame.core.infrastructure.db.entity.KnowledgeItemPO;
import jakarta.persistence.Tuple;
import jakarta.persistence.TupleElement;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * KnowledgeItemConverter 单元测试
 */
class KnowledgeItemConverterTest {

    /**
     * PO → 领域模型 双向转换
     */
    @Test
    void toDomain_shouldConvert() {
        KnowledgeItemPO po = KnowledgeItemPO.builder()
                .id(1L)
                .title("标题")
                .content("内容")
                .contentHtml("<p>内容</p>")
                .coverImageFileId(1L)
                .coverImageUrl("https://example.com/cover.png")
                .tags("[\"Java\",\"Spring\"]")
                .sortOrder(5)
                .status(KnowledgeItemStatus.ACTIVE)
                .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .updatedAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                .build();

        KnowledgeItem domain = KnowledgeItemConverter.INSTANCE.toDomain(po);

        assertNotNull(domain);
        assertEquals(1L, domain.getId());
        assertEquals("标题", domain.getTitle());
        assertEquals("内容", domain.getContent());
        assertEquals("<p>内容</p>", domain.getContentHtml());
        assertEquals(1L, domain.getCoverImage().fileId());
        assertEquals("https://example.com/cover.png", domain.getCoverImage().url());
        assertEquals(List.of("Java", "Spring"), domain.getTags());
        assertEquals(5, domain.getSortOrder());
        assertEquals(KnowledgeItemStatus.ACTIVE, domain.getStatus());
    }

    /**
     * PO → 领域模型 - null PO 返回 null
     */
    @Test
    void toDomain_shouldReturnNull_whenNullPO() {
        assertNull(KnowledgeItemConverter.INSTANCE.toDomain(null));
    }

    /**
     * 领域模型 → PO
     */
    @Test
    void toPO_shouldConvert() {
        KnowledgeItem domain = KnowledgeItem.create(
                "标题", "内容",
                FileRef.of(1L, "https://example.com/cover.png"),
                List.of("Java", "Spring"), 5
        );

        KnowledgeItemPO po = KnowledgeItemConverter.INSTANCE.toPO(domain);

        assertNotNull(po);
        assertNull(po.getId());
        assertEquals("标题", po.getTitle());
        assertEquals(1L, po.getCoverImageFileId());
        assertEquals("https://example.com/cover.png", po.getCoverImageUrl());
        assertEquals(List.of("Java", "Spring"), parseTags(po.getTags()));
        assertEquals(5, po.getSortOrder());
    }

    /**
     * updatePO - 更新字段
     */
    @Test
    void updatePO_shouldUpdateFields() {
        KnowledgeItemPO po = KnowledgeItemPO.builder()
                .id(1L)
                .title("旧标题")
                .content("旧内容")
                .coverImageFileId(1L)
                .coverImageUrl("https://example.com/old.png")
                .tags("[\"旧标签\"]")
                .sortOrder(0)
                .status(KnowledgeItemStatus.ACTIVE)
                .build();

        KnowledgeItem domain = KnowledgeItem.reconstruct(
                1L, "新标题", "新内容", "<p>新</p>",
                FileRef.of(2L, "https://example.com/new.png"),
                List.of("新标签"), 10,
                KnowledgeItemStatus.INACTIVE,
                LocalDateTime.now(), LocalDateTime.now()
        );

        KnowledgeItemConverter.INSTANCE.updatePO(po, domain);

        assertEquals("新标题", po.getTitle());
        assertEquals("新内容", po.getContent());
        assertEquals("<p>新</p>", po.getContentHtml());
        assertEquals(2L, po.getCoverImageFileId());
        assertEquals("https://example.com/new.png", po.getCoverImageUrl());
        assertEquals(10, po.getSortOrder());
        assertEquals(KnowledgeItemStatus.INACTIVE, po.getStatus());
    }

    /**
     * updatePO - FileRef 为 null 时不更新图片双字段（保留原值）
     */
    @Test
    void updatePO_shouldPreserveCoverImage_whenDomainFileRefNull() {
        KnowledgeItemPO po = KnowledgeItemPO.builder()
                .id(1L)
                .title("旧标题")
                .content("旧内容")
                .coverImageFileId(1L)
                .coverImageUrl("https://example.com/old.png")
                .sortOrder(0)
                .status(KnowledgeItemStatus.ACTIVE)
                .build();

        KnowledgeItem domain = KnowledgeItem.reconstruct(
                1L, null, null, null,
                null, null, 0,
                null, null, null
        );

        KnowledgeItemConverter.INSTANCE.updatePO(po, domain);

        assertEquals(1L, po.getCoverImageFileId());
        assertEquals("https://example.com/old.png", po.getCoverImageUrl());
    }

    /**
     * toDomain - null coverImage (fileId 和 url 都为 null)
     */
    @Test
    void toDomain_shouldReturnNullCoverImage_whenBothNull() {
        KnowledgeItemPO po = KnowledgeItemPO.builder()
                .id(1L)
                .title("标题")
                .content("内容")
                .sortOrder(0)
                .status(KnowledgeItemStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        KnowledgeItem domain = KnowledgeItemConverter.INSTANCE.toDomain(po);

        assertNull(domain.getCoverImage());
    }

    /**
     * toDomain - tags 为 null 时返回 null
     */
    @Test
    void toDomain_shouldReturnNullTags_whenJsonNull() {
        KnowledgeItemPO po = KnowledgeItemPO.builder()
                .id(1L)
                .title("标题")
                .content("内容")
                .tags(null)
                .sortOrder(0)
                .status(KnowledgeItemStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        KnowledgeItem domain = KnowledgeItemConverter.INSTANCE.toDomain(po);

        assertNull(domain.getTags());
    }

    /**
     * toSummaryDomain — Tuple → KnowledgeItemSummary 转换，不含 content/contentHtml
     */
    @Test
    void toSummaryDomain_shouldConvertTuple() {
        Tuple tuple = mock(Tuple.class);
        when(tuple.get("id", Long.class)).thenReturn(1L);
        when(tuple.get("title", String.class)).thenReturn("标题");
        when(tuple.get("coverImageFileId", Long.class)).thenReturn(1L);
        when(tuple.get("coverImageUrl", String.class)).thenReturn("https://example.com/cover.png");
        when(tuple.get("tags", String.class)).thenReturn("[\"Java\",\"Spring\"]");
        when(tuple.get("sortOrder", Integer.class)).thenReturn(5);
        when(tuple.get("status", KnowledgeItemStatus.class)).thenReturn(KnowledgeItemStatus.ACTIVE);
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
        when(tuple.get("createdAt", LocalDateTime.class)).thenReturn(now);
        when(tuple.get("updatedAt", LocalDateTime.class)).thenReturn(now);

        KnowledgeItemSummary summary = KnowledgeItemConverter.INSTANCE.toSummaryDomain(tuple);

        assertNotNull(summary);
        assertEquals(1L, summary.getId());
        assertEquals("标题", summary.getTitle());
        assertEquals(1L, summary.getCoverImage().fileId());
        assertEquals("https://example.com/cover.png", summary.getCoverImage().url());
        assertEquals(List.of("Java", "Spring"), summary.getTags());
        assertEquals(5, summary.getSortOrder());
        assertEquals(KnowledgeItemStatus.ACTIVE, summary.getStatus());
        assertEquals(now, summary.getCreatedAt());
        assertEquals(now, summary.getUpdatedAt());
    }

    /**
     * toSummaryDomain — null Tuple 返回 null
     */
    @Test
    void toSummaryDomain_shouldReturnNull_whenNullTuple() {
        assertNull(KnowledgeItemConverter.INSTANCE.toSummaryDomain(null));
    }

    /**
     * toSummaryDomain — coverImage 双字段为 null 时返回 null FileRef
     */
    @Test
    void toSummaryDomain_shouldReturnNullCoverImage_whenBothNull() {
        Tuple tuple = mock(Tuple.class);
        when(tuple.get("id", Long.class)).thenReturn(1L);
        when(tuple.get("title", String.class)).thenReturn("标题");
        when(tuple.get("coverImageFileId", Long.class)).thenReturn(null);
        when(tuple.get("coverImageUrl", String.class)).thenReturn(null);
        when(tuple.get("tags", String.class)).thenReturn(null);
        when(tuple.get("sortOrder", Integer.class)).thenReturn(0);
        when(tuple.get("status", KnowledgeItemStatus.class)).thenReturn(KnowledgeItemStatus.ACTIVE);
        when(tuple.get("createdAt", LocalDateTime.class)).thenReturn(LocalDateTime.now());
        when(tuple.get("updatedAt", LocalDateTime.class)).thenReturn(LocalDateTime.now());

        KnowledgeItemSummary summary = KnowledgeItemConverter.INSTANCE.toSummaryDomain(tuple);

        assertNull(summary.getCoverImage());
        assertNull(summary.getTags());
    }

    private List<String> parseTags(String json) {
        try {
            return KnowledgeItemConverter.OBJECT_MAPPER.readValue(
                    json, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (Exception e) {
            return null;
        }
    }
}
