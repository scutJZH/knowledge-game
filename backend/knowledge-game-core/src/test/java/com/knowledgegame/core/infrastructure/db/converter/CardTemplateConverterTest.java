package com.knowledgegame.core.infrastructure.db.converter;

import com.knowledgegame.core.domain.model.domainenum.CardRarity;
import com.knowledgegame.core.domain.model.domainenum.CardTemplateStatus;
import com.knowledgegame.core.domain.model.entity.CardTemplate;
import com.knowledgegame.core.domain.model.vo.FileRef;
import com.knowledgegame.core.infrastructure.db.entity.CardTemplatePO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CardTemplateConverterTest {

    static FileRef img = FileRef.of(1L, "/static/card.png");

    @Nested
    @DisplayName("toDomain（PO → 领域模型）")
    class ToDomainTests {

        @Test
        @DisplayName("双字段 PO 应映射为 FileRef image")
        void shouldMapDualFieldsToFileRef() {
            CardTemplatePO po = buildPO(1L, 10L, "/static/card.png");
            CardTemplate domain = CardTemplateConverter.INSTANCE.toDomain(po);
            assertNotNull(domain.getImage());
            assertEquals(10L, domain.getImage().fileId());
        }

        @Test
        @DisplayName("双字段均为 null 时 FileRef 为 null")
        void shouldReturnNullFileRefWhenBothNull() {
            CardTemplatePO po = buildPO(1L, null, null);
            CardTemplate domain = CardTemplateConverter.INSTANCE.toDomain(po);
            assertNull(domain.getImage());
        }
    }

    @Nested
    @DisplayName("toPO（领域模型 → PO）")
    class ToPOTests {

        @Test
        @DisplayName("FileRef 应映射为双字段 PO")
        void shouldMapFileRefToDualFields() {
            CardTemplate domain = buildDomain(img);
            CardTemplatePO po = CardTemplateConverter.INSTANCE.toPO(domain);
            assertEquals(1L, po.getImageFileId());
            assertEquals("/static/card.png", po.getImageUrl());
        }

        @Test
        @DisplayName("null FileRef 应映射为双 null PO 字段")
        void shouldMapNullFileRefToNullFields() {
            CardTemplate domain = buildDomain(null);
            CardTemplatePO po = CardTemplateConverter.INSTANCE.toPO(domain);
            assertNull(po.getImageFileId());
            assertNull(po.getImageUrl());
        }
    }

    @Nested
    @DisplayName("updatePO（领域模型 → 已有 PO 更新）")
    class UpdatePOTests {

        @Test
        @DisplayName("非 null FileRef 应显式赋值 PO 双字段")
        void shouldSetDualFieldsWhenFileRefNonNull() {
            CardTemplatePO po = buildPO(1L, 9L, "/old.png");
            CardTemplate domain = buildDomain(img);
            CardTemplateConverter.INSTANCE.updatePO(po, domain);
            assertEquals(1L, po.getImageFileId());
            assertEquals("/static/card.png", po.getImageUrl());
        }

        @Test
        @DisplayName("null FileRef 应清空 PO 双字段")
        void shouldClearDualFieldsWhenFileRefNull() {
            CardTemplatePO po = buildPO(1L, 9L, "/old.png");
            CardTemplate domain = buildDomain(null);
            CardTemplateConverter.INSTANCE.updatePO(po, domain);
            assertNull(po.getImageFileId());
            assertNull(po.getImageUrl());
        }
    }

    private CardTemplatePO buildPO(Long id, Long fileId, String url) {
        return CardTemplatePO.builder()
                .id(id).ipSeriesId(1L).code("CODE").name("名称")
                .rarity(CardRarity.N).description("描述").status(CardTemplateStatus.ACTIVE)
                .imageFileId(fileId).imageUrl(url)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
    }

    private CardTemplate buildDomain(FileRef image) {
        return CardTemplate.reconstruct(1L, 1L, "CODE", "名称",
                CardRarity.N, "描述", CardTemplateStatus.ACTIVE, image,
                LocalDateTime.now(), LocalDateTime.now());
    }
}
