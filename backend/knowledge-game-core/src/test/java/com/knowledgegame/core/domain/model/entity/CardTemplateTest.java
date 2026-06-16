package com.knowledgegame.core.domain.model.entity;

import com.knowledgegame.core.domain.model.domainenum.CardRarity;
import com.knowledgegame.core.domain.model.domainenum.CardTemplateStatus;
import com.knowledgegame.core.domain.model.vo.FileRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CardTemplateTest {

    private static final FileRef IMG = FileRef.of(1L, "https://example.com/card.png");
    private static final FileRef IMG2 = FileRef.of(2L, "https://example.com/recon.png");
    private static final FileRef IMG3 = FileRef.of(3L, "https://example.com/img.png");
    private static final FileRef IMG_NEW = FileRef.of(9L, "https://new.com/img.png");
    private static final FileRef IMG_OLD = FileRef.of(8L, "https://old.com/img.png");
    private static final FileRef IMG_ORIG = FileRef.of(7L, "https://original.com/img.png");
    private static final FileRef IMG_ONLY = FileRef.of(6L, "https://new.com/only-img.png");

    @Test
    @DisplayName("create() 应正确设置所有字段")
    void create_shouldSetAllBusinessFields() {
        CardTemplate template = CardTemplate.create(
                100L, "CARD_001", "火影忍者卡牌", CardRarity.SR,
                "一张稀有的火影忍者卡牌", CardTemplateStatus.ACTIVE, IMG);

        assertEquals(100L, template.getIpSeriesId());
        assertEquals("CARD_001", template.getCode());
        assertEquals("火影忍者卡牌", template.getName());
        assertEquals(CardRarity.SR, template.getRarity());
        assertEquals(CardTemplateStatus.ACTIVE, template.getStatus());
        assertEquals(IMG, template.getImage());
    }

    @Test
    @DisplayName("create() 传入 null FileRef 应正常创建")
    void create_shouldAllowNullImage() {
        CardTemplate template = CardTemplate.create(
                1L, "CODE", "名称", CardRarity.N, "描述",
                CardTemplateStatus.ACTIVE, null);

        assertNull(template.getImage());
        assertEquals("CODE", template.getCode());
        assertEquals(CardTemplateStatus.ACTIVE, template.getStatus());
    }

    @Test
    @DisplayName("create() 应自动设置时间戳且 id 为 null")
    void create_shouldSetTimestampsAndIdNull() {
        LocalDateTime before = LocalDateTime.now();
        CardTemplate template = CardTemplate.create(
                1L, "CODE", "名称", CardRarity.N, "描述",
                CardTemplateStatus.ACTIVE, IMG3);
        LocalDateTime after = LocalDateTime.now();

        assertNull(template.getId());
        assertNotNull(template.getCreatedAt());
        assertNotNull(template.getUpdatedAt());
        assertEquals(false, template.getCreatedAt().isBefore(before));
        assertEquals(false, template.getCreatedAt().isAfter(after));
    }

    @Test
    @DisplayName("reconstruct() 应完整还原所有字段")
    void reconstruct_shouldRestoreAllFields() {
        LocalDateTime created = LocalDateTime.of(2025, 3, 15, 10, 0, 0);
        LocalDateTime updated = LocalDateTime.of(2025, 6, 1, 15, 30, 0);

        CardTemplate template = CardTemplate.reconstruct(
                99L, 10L, "RECON_CODE", "重建卡牌", CardRarity.SSR,
                "重建描述", CardTemplateStatus.INACTIVE, IMG2, created, updated);

        assertEquals(99L, template.getId());
        assertEquals(10L, template.getIpSeriesId());
        assertEquals("RECON_CODE", template.getCode());
        assertEquals(CardRarity.SSR, template.getRarity());
        assertEquals(CardTemplateStatus.INACTIVE, template.getStatus());
        assertEquals(IMG2, template.getImage());
        assertEquals(created, template.getCreatedAt());
        assertEquals(updated, template.getUpdatedAt());
    }

    @Test
    @DisplayName("reconstruct() 传入 null FileRef 应正常还原")
    void reconstruct_shouldAllowNullImage() {
        CardTemplate template = CardTemplate.reconstruct(
                1L, 10L, "CODE", "名称", CardRarity.N,
                "描述", CardTemplateStatus.ACTIVE, null,
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 1, 1, 0, 0));

        assertNull(template.getImage());
        assertEquals(1L, template.getId());
    }

    @Test
    @DisplayName("update() 传入非 null 值时应更新所有字段")
    void update_shouldModifyAllFieldsWhenNonNull() {
        CardTemplate template = CardTemplate.reconstruct(
                1L, 10L, "OLD_CODE", "旧名称", CardRarity.N,
                "旧描述", CardTemplateStatus.ACTIVE, IMG_OLD,
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 1, 1, 0, 0));

        template.update("NEW_CODE", "新名称", CardRarity.SSR, "新描述",
                CardTemplateStatus.INACTIVE, IMG_NEW);

        assertEquals("NEW_CODE", template.getCode());
        assertEquals("新名称", template.getName());
        assertEquals(CardRarity.SSR, template.getRarity());
        assertEquals("新描述", template.getDescription());
        assertEquals(CardTemplateStatus.INACTIVE, template.getStatus());
        assertEquals(IMG_NEW, template.getImage());
    }

    @Test
    @DisplayName("update() 传入 null 时所有字段保持原值")
    void update_shouldKeepOriginalWhenNullPassed() {
        CardTemplate template = CardTemplate.reconstruct(
                1L, 10L, "ORIGINAL_CODE", "原始名称", CardRarity.SR,
                "原始描述", CardTemplateStatus.ACTIVE, IMG_ORIG,
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 1, 1, 0, 0));

        template.update(null, null, null, null, null, null);

        assertEquals("ORIGINAL_CODE", template.getCode());
        assertEquals("原始名称", template.getName());
        assertEquals(CardRarity.SR, template.getRarity());
        assertEquals("原始描述", template.getDescription());
        assertEquals(CardTemplateStatus.ACTIVE, template.getStatus());
        assertEquals(IMG_ORIG, template.getImage());
    }

    @Test
    @DisplayName("update() 部分更新：只传 FileRef，其他字段不变")
    void update_shouldOnlyUpdateImageWhenThatsTheOnlyNonNull() {
        CardTemplate template = CardTemplate.reconstruct(
                1L, 10L, "CODE", "名称", CardRarity.N,
                "描述", CardTemplateStatus.ACTIVE, null,
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 1, 1, 0, 0));

        template.update(null, null, null, null, null, IMG_ONLY);

        assertEquals("CODE", template.getCode());
        assertEquals("名称", template.getName());
        assertEquals(CardRarity.N, template.getRarity());
        assertEquals("描述", template.getDescription());
        assertEquals(CardTemplateStatus.ACTIVE, template.getStatus());
        assertEquals(IMG_ONLY, template.getImage());
    }

    @Test
    @DisplayName("update() 应刷新 updatedAt 时间戳")
    void update_shouldRefreshUpdatedAt() {
        LocalDateTime oldUpdated = LocalDateTime.of(2020, 1, 1, 0, 0);
        CardTemplate template = CardTemplate.reconstruct(
                1L, 10L, "CODE", "名称", CardRarity.N,
                "描述", CardTemplateStatus.ACTIVE, null,
                LocalDateTime.of(2020, 1, 1, 0, 0), oldUpdated);

        template.update("NEW_CODE", null, null, null, null, null);

        assertNotNull(template.getUpdatedAt());
        assertNotEquals(oldUpdated, template.getUpdatedAt());
    }

    @Test
    @DisplayName("deactivate() 应将 status 变为 INACTIVE")
    void deactivate_shouldSetStatusToInactive() {
        CardTemplate template = CardTemplate.reconstruct(
                1L, 10L, "CODE", "名称", CardRarity.SR,
                "描述", CardTemplateStatus.ACTIVE, IMG3,
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 1, 1, 0, 0));

        template.deactivate();

        assertEquals(CardTemplateStatus.INACTIVE, template.getStatus());
        assertEquals("CODE", template.getCode());
        assertEquals(IMG3, template.getImage());
    }

    @Test
    @DisplayName("deactivate() 应刷新 updatedAt 时间戳")
    void deactivate_shouldRefreshUpdatedAt() {
        LocalDateTime oldUpdated = LocalDateTime.of(2020, 1, 1, 0, 0);
        CardTemplate template = CardTemplate.reconstruct(
                1L, 10L, "CODE", "名称", CardRarity.SR,
                "描述", CardTemplateStatus.ACTIVE, null,
                LocalDateTime.of(2020, 1, 1, 0, 0), oldUpdated);

        template.deactivate();

        assertNotNull(template.getUpdatedAt());
        assertNotEquals(oldUpdated, template.getUpdatedAt());
    }
}
