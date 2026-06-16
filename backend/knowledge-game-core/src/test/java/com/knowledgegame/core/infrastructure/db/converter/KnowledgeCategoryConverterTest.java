package com.knowledgegame.core.infrastructure.db.converter;

import com.knowledgegame.core.domain.model.domainenum.KnowledgeCategoryStatus;
import com.knowledgegame.core.domain.model.entity.KnowledgeCategory;
import com.knowledgegame.core.domain.model.vo.FileRef;
import com.knowledgegame.core.infrastructure.db.entity.KnowledgeCategoryPO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class KnowledgeCategoryConverterTest {

    static FileRef icon = FileRef.of(1L, "/static/icon.png");
    static FileRef cover = FileRef.of(2L, "/static/cover.jpg");

    @Nested
    @DisplayName("toDomain（PO → 领域模型）")
    class ToDomainTests {

        @Test
        @DisplayName("双字段 PO 应映射为 FileRef icon + coverImage")
        void shouldMapDualFieldsToFileRefs() {
            KnowledgeCategoryPO po = buildPO(1L, 1L, "/static/icon.png", 2L, "/static/cover.jpg");
            KnowledgeCategory domain = KnowledgeCategoryConverter.INSTANCE.toDomain(po);
            assertNotNull(domain.getIcon());
            assertEquals(1L, domain.getIcon().fileId());
            assertEquals("/static/icon.png", domain.getIcon().url());
            assertNotNull(domain.getCoverImage());
            assertEquals(2L, domain.getCoverImage().fileId());
        }

        @Test
        @DisplayName("双字段均为 null 时 FileRef 为 null")
        void shouldReturnNullFileRefsWhenBothNull() {
            KnowledgeCategoryPO po = buildPO(1L, null, null, null, null);
            KnowledgeCategory domain = KnowledgeCategoryConverter.INSTANCE.toDomain(po);
            assertNull(domain.getIcon());
            assertNull(domain.getCoverImage());
        }
    }

    @Nested
    @DisplayName("toPO（领域模型 → PO）")
    class ToPOTests {

        @Test
        @DisplayName("FileRef icon + coverImage 应映射为双字段 PO")
        void shouldMapFileRefsToDualFields() {
            KnowledgeCategory domain = buildDomain(icon, cover);
            KnowledgeCategoryPO po = KnowledgeCategoryConverter.INSTANCE.toPO(domain);
            assertEquals(1L, po.getIconFileId());
            assertEquals("/static/icon.png", po.getIconUrl());
            assertEquals(2L, po.getCoverImageFileId());
            assertEquals("/static/cover.jpg", po.getCoverImageUrl());
        }

        @Test
        @DisplayName("null FileRef 应映射为双 null PO 字段")
        void shouldMapNullFileRefsToNullFields() {
            KnowledgeCategory domain = buildDomain(null, null);
            KnowledgeCategoryPO po = KnowledgeCategoryConverter.INSTANCE.toPO(domain);
            assertNull(po.getIconFileId());
            assertNull(po.getIconUrl());
            assertNull(po.getCoverImageFileId());
            assertNull(po.getCoverImageUrl());
        }
    }

    @Nested
    @DisplayName("updatePO（领域模型 → 已有 PO 更新）")
    class UpdatePOTests {

        @Test
        @DisplayName("非 null FileRef 应显式赋值 PO 双字段")
        void shouldSetDualFieldsWhenFileRefNonNull() {
            KnowledgeCategoryPO po = buildPO(1L, 9L, "/old.png", 9L, "/old.jpg");
            KnowledgeCategory domain = buildDomain(icon, cover);
            KnowledgeCategoryConverter.INSTANCE.updatePO(po, domain);
            assertEquals(1L, po.getIconFileId());
            assertEquals("/static/icon.png", po.getIconUrl());
            assertEquals(2L, po.getCoverImageFileId());
        }

        @Test
        @DisplayName("null FileRef 应清空 PO 双字段")
        void shouldClearDualFieldsWhenFileRefNull() {
            KnowledgeCategoryPO po = buildPO(1L, 9L, "/old.png", 9L, "/old.jpg");
            KnowledgeCategory domain = buildDomain(null, null);
            KnowledgeCategoryConverter.INSTANCE.updatePO(po, domain);
            assertNull(po.getIconFileId());
            assertNull(po.getIconUrl());
            assertNull(po.getCoverImageFileId());
            assertNull(po.getCoverImageUrl());
        }
    }

    private KnowledgeCategoryPO buildPO(Long id, Long iconFid, String iconUrl, Long coverFid, String coverUrl) {
        return KnowledgeCategoryPO.builder()
                .id(id).parentId(null).name("测试").description("描述")
                .iconFileId(iconFid).iconUrl(iconUrl).color("#FFF")
                .coverImageFileId(coverFid).coverImageUrl(coverUrl)
                .sortOrder(0).status(KnowledgeCategoryStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
    }

    private KnowledgeCategory buildDomain(FileRef iconRef, FileRef coverRef) {
        return KnowledgeCategory.reconstruct(1L, null, "测试", "描述",
                iconRef, "#FFF", coverRef, 0,
                KnowledgeCategoryStatus.ACTIVE,
                LocalDateTime.now(), LocalDateTime.now());
    }
}
