package com.knowledgegame.core.domain.model.entity;

import com.knowledgegame.core.domain.model.domainenum.CardRarity;
import com.knowledgegame.core.domain.model.domainenum.CardTemplateStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * CardTemplate 聚合根单元测试
 * <p>
 * 覆盖工厂方法（create / reconstruct）、更新、软删除等核心行为。
 * REQ-92 简化后：移除 CardStarImage，改用单一 imageUrl 字段。
 */
class CardTemplateTest {

    // ==================== create() 工厂方法 ====================

    /**
     * 验证 create() 正确设置所有业务字段（含 imageUrl）
     */
    @Test
    @DisplayName("create() 应正确设置所有字段，包括 imageUrl")
    void create_shouldSetAllBusinessFields() {
        // 执行
        CardTemplate template = CardTemplate.create(
                100L,
                "CARD_001",
                "火影忍者卡牌",
                CardRarity.SR,
                "一张稀有的火影忍者卡牌",
                CardTemplateStatus.ACTIVE,
                "https://example.com/card.png"
        );

        // 断言
        assertEquals(100L, template.getIpSeriesId(), "ipSeriesId 应为 100");
        assertEquals("CARD_001", template.getCode(), "code 应为 CARD_001");
        assertEquals("火影忍者卡牌", template.getName(), "name 应为 火影忍者卡牌");
        assertEquals(CardRarity.SR, template.getRarity(), "rarity 应为 SR");
        assertEquals("一张稀有的火影忍者卡牌", template.getDescription(), "description 应正确设置");
        assertEquals(CardTemplateStatus.ACTIVE, template.getStatus(), "status 应为 ACTIVE");
        assertEquals("https://example.com/card.png", template.getImageUrl(), "imageUrl 应正确设置");
    }

    /**
     * 验证 create() 传入 null imageUrl
     */
    @Test
    @DisplayName("create() 传入 null imageUrl 应正常创建，imageUrl 为 null")
    void create_shouldAllowNullImageUrl() {
        // 执行
        CardTemplate template = CardTemplate.create(
                1L, "CODE", "名称", CardRarity.N, "描述",
                CardTemplateStatus.ACTIVE, null
        );

        // 断言
        assertNull(template.getImageUrl(), "imageUrl 应为 null");
        assertEquals("CODE", template.getCode(), "code 应正确设置");
        assertEquals(CardTemplateStatus.ACTIVE, template.getStatus(), "status 应为 ACTIVE");
    }

    /**
     * 验证 create() 自动设置 createdAt/updatedAt，且 id 为 null
     */
    @Test
    @DisplayName("create() 应自动设置 createdAt 和 updatedAt，且 id 为 null")
    void create_shouldSetTimestampsAndIdNull() {
        // 记录调用前时刻
        LocalDateTime before = LocalDateTime.now();

        // 执行
        CardTemplate template = CardTemplate.create(
                1L, "CODE", "名称", CardRarity.N, "描述",
                CardTemplateStatus.ACTIVE, "https://example.com/img.png"
        );

        // 记录调用后时刻
        LocalDateTime after = LocalDateTime.now();

        // id 在 create 时应为 null（由持久化层生成）
        assertNull(template.getId(), "create() 创建的对象 id 应为 null");

        // createdAt 和 updatedAt 应不为 null
        assertNotNull(template.getCreatedAt(), "createdAt 不应为 null");
        assertNotNull(template.getUpdatedAt(), "updatedAt 不应为 null");
        // 时间戳应在合理范围内
        assertEquals(false, template.getCreatedAt().isBefore(before),
                "createdAt 不应早于调用前时刻");
        assertEquals(false, template.getCreatedAt().isAfter(after),
                "createdAt 不应晚于调用后时刻");
    }

    // ==================== reconstruct() 工厂方法 ====================

    /**
     * 验证 reconstruct() 完整还原所有字段（含 id、时间戳、imageUrl）
     */
    @Test
    @DisplayName("reconstruct() 应完整还原所有字段（含 id、时间戳、imageUrl）")
    void reconstruct_shouldRestoreAllFields() {
        // 准备数据
        LocalDateTime created = LocalDateTime.of(2025, 3, 15, 10, 0, 0);
        LocalDateTime updated = LocalDateTime.of(2025, 6, 1, 15, 30, 0);

        // 执行
        CardTemplate template = CardTemplate.reconstruct(
                99L,
                10L,
                "RECON_CODE",
                "重建卡牌",
                CardRarity.SSR,
                "重建描述",
                CardTemplateStatus.INACTIVE,
                "https://example.com/recon.png",
                created,
                updated
        );

        // 断言全部字段
        assertEquals(99L, template.getId(), "id 应为 99");
        assertEquals(10L, template.getIpSeriesId(), "ipSeriesId 应为 10");
        assertEquals("RECON_CODE", template.getCode(), "code 应为 RECON_CODE");
        assertEquals("重建卡牌", template.getName(), "name 应为 重建卡牌");
        assertEquals(CardRarity.SSR, template.getRarity(), "rarity 应为 SSR");
        assertEquals("重建描述", template.getDescription(), "description 应正确还原");
        assertEquals(CardTemplateStatus.INACTIVE, template.getStatus(), "status 应为 INACTIVE");
        assertEquals("https://example.com/recon.png", template.getImageUrl(),
                "imageUrl 应正确还原");
        assertEquals(created, template.getCreatedAt(), "createdAt 应精确还原");
        assertEquals(updated, template.getUpdatedAt(), "updatedAt 应精确还原");
    }

    /**
     * 验证 reconstruct() 传入 null imageUrl
     */
    @Test
    @DisplayName("reconstruct() 传入 null imageUrl 应正常还原")
    void reconstruct_shouldAllowNullImageUrl() {
        // 执行
        CardTemplate template = CardTemplate.reconstruct(
                1L, 10L, "CODE", "名称", CardRarity.N,
                "描述", CardTemplateStatus.ACTIVE, null,
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 1, 1, 0, 0)
        );

        // 断言
        assertNull(template.getImageUrl(), "imageUrl 应为 null");
        assertEquals(1L, template.getId(), "id 应为 1");
    }

    // ==================== update() 方法 ====================

    /**
     * 验证 update() 传入非 null 值时更新所有字段（含 imageUrl）
     */
    @Test
    @DisplayName("update() 传入非 null 值时应更新所有字段（含 imageUrl）")
    void update_shouldModifyAllFieldsWhenNonNull() {
        // 准备：构造一个已有对象
        CardTemplate template = CardTemplate.reconstruct(
                1L, 10L, "OLD_CODE", "旧名称", CardRarity.N,
                "旧描述", CardTemplateStatus.ACTIVE, "https://old.com/img.png",
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 1, 1, 0, 0)
        );

        // 执行：传入新的非 null 值
        template.update("NEW_CODE", "新名称", CardRarity.SSR, "新描述",
                CardTemplateStatus.INACTIVE, "https://new.com/img.png");

        // 断言字段已更新
        assertEquals("NEW_CODE", template.getCode(), "code 应被更新");
        assertEquals("新名称", template.getName(), "name 应被更新");
        assertEquals(CardRarity.SSR, template.getRarity(), "rarity 应被更新为 SSR");
        assertEquals("新描述", template.getDescription(), "description 应被更新");
        assertEquals(CardTemplateStatus.INACTIVE, template.getStatus(), "status 应被更新为 INACTIVE");
        assertEquals("https://new.com/img.png", template.getImageUrl(), "imageUrl 应被更新");
    }

    /**
     * 验证 update() 传入 null 时所有字段保持原值不变（null-skip）
     */
    @Test
    @DisplayName("update() 传入 null 时所有字段保持原值不变")
    void update_shouldKeepOriginalWhenNullPassed() {
        // 准备
        CardTemplate template = CardTemplate.reconstruct(
                1L, 10L, "ORIGINAL_CODE", "原始名称", CardRarity.SR,
                "原始描述", CardTemplateStatus.ACTIVE, "https://original.com/img.png",
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 1, 1, 0, 0)
        );

        // 执行：所有字段传 null
        template.update(null, null, null, null, null, null);

        // 断言：所有有 null 守卫的字段保持不变
        assertEquals("ORIGINAL_CODE", template.getCode(), "code 传 null 时应保持原值");
        assertEquals("原始名称", template.getName(), "name 传 null 时应保持原值");
        assertEquals(CardRarity.SR, template.getRarity(), "rarity 传 null 时应保持原值");
        assertEquals("原始描述", template.getDescription(), "description 传 null 时应保持原值");
        assertEquals(CardTemplateStatus.ACTIVE, template.getStatus(), "status 传 null 时应保持原值");
        assertEquals("https://original.com/img.png", template.getImageUrl(),
                "imageUrl 传 null 时应保持原值");
    }

    /**
     * 验证 update() 部分更新：只更新 imageUrl，其他字段不变
     */
    @Test
    @DisplayName("update() 部分更新：只传 imageUrl，其他字段不变")
    void update_shouldOnlyUpdateImageUrlWhenThatsTheOnlyNonNull() {
        // 准备
        CardTemplate template = CardTemplate.reconstruct(
                1L, 10L, "CODE", "名称", CardRarity.N,
                "描述", CardTemplateStatus.ACTIVE, null,
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 1, 1, 0, 0)
        );

        // 执行：只传 imageUrl
        template.update(null, null, null, null, null, "https://new.com/only-img.png");

        // 断言：只有 imageUrl 变了
        assertEquals("CODE", template.getCode(), "code 应保持不变");
        assertEquals("名称", template.getName(), "name 应保持不变");
        assertEquals(CardRarity.N, template.getRarity(), "rarity 应保持不变");
        assertEquals("描述", template.getDescription(), "description 应保持不变");
        assertEquals(CardTemplateStatus.ACTIVE, template.getStatus(), "status 应保持不变");
        assertEquals("https://new.com/only-img.png", template.getImageUrl(),
                "imageUrl 应被更新");
    }

    /**
     * 验证 update() 刷新 updatedAt
     */
    @Test
    @DisplayName("update() 应刷新 updatedAt 时间戳")
    void update_shouldRefreshUpdatedAt() {
        // 准备：构造一个 updatedAt 在过去的时间点
        LocalDateTime oldUpdated = LocalDateTime.of(2020, 1, 1, 0, 0);
        CardTemplate template = CardTemplate.reconstruct(
                1L, 10L, "CODE", "名称", CardRarity.N,
                "描述", CardTemplateStatus.ACTIVE, null,
                LocalDateTime.of(2020, 1, 1, 0, 0),
                oldUpdated
        );

        // 执行
        template.update("NEW_CODE", null, null, null, null, null);

        // 断言：updatedAt 应该比旧的晚
        assertNotNull(template.getUpdatedAt(), "updatedAt 不应为 null");
        assertNotEquals(oldUpdated, template.getUpdatedAt(), "updatedAt 应被刷新，不再等于旧值");
        assertEquals(false, template.getUpdatedAt().isBefore(oldUpdated),
                "新的 updatedAt 不应早于旧的 updatedAt");
    }

    // ==================== deactivate() 方法 ====================

    /**
     * 验证 deactivate() 将 status 设为 INACTIVE
     */
    @Test
    @DisplayName("deactivate() 应将 status 变为 INACTIVE")
    void deactivate_shouldSetStatusToInactive() {
        // 准备：构造一个 ACTIVE 状态的模板
        CardTemplate template = CardTemplate.reconstruct(
                1L, 10L, "CODE", "名称", CardRarity.SR,
                "描述", CardTemplateStatus.ACTIVE, "https://example.com/img.png",
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 1, 1, 0, 0)
        );

        // 执行
        template.deactivate();

        // 断言
        assertEquals(CardTemplateStatus.INACTIVE, template.getStatus(), "status 应变为 INACTIVE");
        // 其他字段不变
        assertEquals("CODE", template.getCode(), "deactivate 不应改动 code");
        assertEquals("https://example.com/img.png", template.getImageUrl(),
                "deactivate 不应改动 imageUrl");
    }

    /**
     * 验证 deactivate() 同时刷新 updatedAt 时间戳
     */
    @Test
    @DisplayName("deactivate() 应刷新 updatedAt 时间戳")
    void deactivate_shouldRefreshUpdatedAt() {
        // 准备：构造一个 updatedAt 在过去的时间点
        LocalDateTime oldUpdated = LocalDateTime.of(2020, 1, 1, 0, 0);
        CardTemplate template = CardTemplate.reconstruct(
                1L, 10L, "CODE", "名称", CardRarity.SR,
                "描述", CardTemplateStatus.ACTIVE, null,
                LocalDateTime.of(2020, 1, 1, 0, 0),
                oldUpdated
        );

        // 执行
        template.deactivate();

        // 断言：updatedAt 应被刷新
        assertNotNull(template.getUpdatedAt(), "updatedAt 不应为 null");
        assertNotEquals(oldUpdated, template.getUpdatedAt(), "updatedAt 应被刷新");
    }
}
