package com.knowledgegame.domain.model.entity;

import com.knowledgegame.domain.model.domainenum.CardRarity;
import com.knowledgegame.domain.model.domainenum.CardTemplateStatus;
import com.knowledgegame.domain.model.vo.CardStarImage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CardTemplate 聚合根单元测试
 * <p>
 * 覆盖工厂方法（create / reconstruct）、更新、星级图片管理、软删除等核心行为。
 */
class CardTemplateTest {

    // ==================== create() 工厂方法 ====================

    /**
     * 验证 create() 正确设置所有业务字段
     */
    @Test
    @DisplayName("create() 应正确设置 ipSeriesId、code、name、rarity、description、status")
    void create_shouldSetAllBusinessFields() {
        // 执行
        List<CardStarImage> images = new ArrayList<>();
        images.add(CardStarImage.create(1, "https://example.com/star1.png"));
        images.add(CardStarImage.create(2, "https://example.com/star2.png"));

        CardTemplate template = CardTemplate.create(
                100L,
                "CARD_001",
                "火影忍者卡牌",
                CardRarity.SR,
                "一张稀有的火影忍者卡牌",
                CardTemplateStatus.ACTIVE,
                images
        );

        // 断言
        assertEquals(100L, template.getIpSeriesId(), "ipSeriesId 应为 100");
        assertEquals("CARD_001", template.getCode(), "code 应为 CARD_001");
        assertEquals("火影忍者卡牌", template.getName(), "name 应为 火影忍者卡牌");
        assertEquals(CardRarity.SR, template.getRarity(), "rarity 应为 SR");
        assertEquals("一张稀有的火影忍者卡牌", template.getDescription(), "description 应正确设置");
        assertEquals(CardTemplateStatus.ACTIVE, template.getStatus(), "status 应为 ACTIVE");
        assertEquals(2, template.getStarImages().size(), "starImages 应包含 2 个元素");
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
                CardTemplateStatus.ACTIVE, null
        );

        // 记录调用后时刻
        LocalDateTime after = LocalDateTime.now();

        // id 在 create 时应为 null（由持久化层生成）
        assertNull(template.getId(), "create() 创建的对象 id 应为 null");

        // createdAt 和 updatedAt 应在 [before, after] 区间内
        assertNotNull(template.getCreatedAt(), "createdAt 不应为 null");
        assertNotNull(template.getUpdatedAt(), "updatedAt 不应为 null");
        assertNotEquals(true, template.getCreatedAt().isBefore(before), "createdAt 不应早于调用前时刻");
        assertNotEquals(true, template.getCreatedAt().isAfter(after), "createdAt 不应晚于调用后时刻");
    }

    /**
     * 验证 create() 传入空 starImages 时自动生成 1 星默认图片
     */
    @Test
    @DisplayName("create() 传入空 starImages 时应自动生成 1 星默认图片（starLevel=1, imageUrl=''）")
    void create_shouldGenerateDefaultStarImageWhenStarImagesEmpty() {
        // 执行：传入 null
        CardTemplate templateWithNull = CardTemplate.create(
                1L, "CODE", "名称", CardRarity.N, "描述",
                CardTemplateStatus.ACTIVE, null
        );

        // 断言：自动生成 1 星默认图片
        assertEquals(1, templateWithNull.getStarImages().size(), "传入 null 时应自动生成 1 个星级图片");
        assertEquals(1, templateWithNull.getStarImages().get(0).getStarLevel(), "默认星级应为 1");
        assertEquals("", templateWithNull.getStarImages().get(0).getImageUrl(), "默认 imageUrl 应为空字符串");

        // 执行：传入空列表
        CardTemplate templateWithEmpty = CardTemplate.create(
                1L, "CODE", "名称", CardRarity.N, "描述",
                CardTemplateStatus.ACTIVE, new ArrayList<>()
        );

        // 断言：同样自动生成 1 星默认图片
        assertEquals(1, templateWithEmpty.getStarImages().size(), "传入空列表时也应自动生成 1 个星级图片");
        assertEquals(1, templateWithEmpty.getStarImages().get(0).getStarLevel(), "默认星级应为 1");
    }

    /**
     * 验证 create() 传入非空 starImages 时保留传入列表
     */
    @Test
    @DisplayName("create() 传入非空 starImages 时应保留传入列表")
    void create_shouldKeepStarImagesWhenProvided() {
        // 准备
        List<CardStarImage> images = new ArrayList<>();
        images.add(CardStarImage.create(1, "https://example.com/1.png"));
        images.add(CardStarImage.create(2, "https://example.com/2.png"));
        images.add(CardStarImage.create(3, "https://example.com/3.png"));

        // 执行
        CardTemplate template = CardTemplate.create(
                1L, "CODE", "名称", CardRarity.SSR, "描述",
                CardTemplateStatus.ACTIVE, images
        );

        // 断言：保留传入的列表
        assertEquals(3, template.getStarImages().size(), "应保留传入的 3 个星级图片");
        assertEquals(1, template.getStarImages().get(0).getStarLevel(), "第一个星级应为 1");
        assertEquals(2, template.getStarImages().get(1).getStarLevel(), "第二个星级应为 2");
        assertEquals(3, template.getStarImages().get(2).getStarLevel(), "第三个星级应为 3");
    }

    // ==================== reconstruct() 工厂方法 ====================

    /**
     * 验证 reconstruct() 完整还原所有字段（含 id 和时间戳）
     */
    @Test
    @DisplayName("reconstruct() 应完整还原所有字段（含 id 和时间戳）")
    void reconstruct_shouldRestoreAllFields() {
        // 准备数据
        LocalDateTime created = LocalDateTime.of(2025, 3, 15, 10, 0, 0);
        LocalDateTime updated = LocalDateTime.of(2025, 6, 1, 15, 30, 0);
        List<CardStarImage> images = new ArrayList<>();
        images.add(CardStarImage.create(1, "https://example.com/1.png"));
        images.add(CardStarImage.create(2, "https://example.com/2.png"));

        // 执行
        CardTemplate template = CardTemplate.reconstruct(
                99L,
                10L,
                "RECON_CODE",
                "重建卡牌",
                CardRarity.SSR,
                "重建描述",
                CardTemplateStatus.INACTIVE,
                images,
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
        assertEquals(2, template.getStarImages().size(), "starImages 应包含 2 个元素");
        assertEquals(created, template.getCreatedAt(), "createdAt 应精确还原");
        assertEquals(updated, template.getUpdatedAt(), "updatedAt 应精确还原");
    }

    // ==================== update() 方法 ====================

    /**
     * 验证 update() 传入非 null 值时更新对应字段
     */
    @Test
    @DisplayName("update() 传入非 null 值时应更新 code、name、rarity、status 等字段")
    void update_shouldModifyFieldsWhenNonNull() {
        // 准备：构造一个已有对象
        CardTemplate template = CardTemplate.reconstruct(
                1L, 10L, "OLD_CODE", "旧名称", CardRarity.N,
                "旧描述", CardTemplateStatus.ACTIVE,
                new ArrayList<>(),
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 1, 1, 0, 0)
        );

        // 执行：传入新的非 null 值
        template.update("NEW_CODE", "新名称", CardRarity.SSR, "新描述", CardTemplateStatus.INACTIVE);

        // 断言字段已更新
        assertEquals("NEW_CODE", template.getCode(), "code 应被更新");
        assertEquals("新名称", template.getName(), "name 应被更新");
        assertEquals(CardRarity.SSR, template.getRarity(), "rarity 应被更新为 SSR");
        assertEquals("新描述", template.getDescription(), "description 应被更新");
        assertEquals(CardTemplateStatus.INACTIVE, template.getStatus(), "status 应被更新为 INACTIVE");
    }

    /**
     * 验证 update() 传入 null 时，有守卫的字段（code, name, rarity, status）保持不变
     */
    @Test
    @DisplayName("update() 传入 null 时，code、name、rarity、status 保持原值不变")
    void update_shouldKeepOriginalWhenNullPassed() {
        // 准备
        CardTemplate template = CardTemplate.reconstruct(
                1L, 10L, "ORIGINAL_CODE", "原始名称", CardRarity.SR,
                "原始描述", CardTemplateStatus.ACTIVE,
                new ArrayList<>(),
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 1, 1, 0, 0)
        );

        // 执行：所有字段传 null
        template.update(null, null, null, null, null);

        // 断言：有 null 守卫的字段保持不变
        assertEquals("ORIGINAL_CODE", template.getCode(), "code 传 null 时应保持原值");
        assertEquals("原始名称", template.getName(), "name 传 null 时应保持原值");
        assertEquals(CardRarity.SR, template.getRarity(), "rarity 传 null 时应保持原值");
        assertEquals(CardTemplateStatus.ACTIVE, template.getStatus(), "status 传 null 时应保持原值");

        // description 无 null 守卫，传 null 会被覆盖
        assertNull(template.getDescription(), "description 传 null 时会被覆盖为 null");
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
                "描述", CardTemplateStatus.ACTIVE,
                new ArrayList<>(),
                LocalDateTime.of(2020, 1, 1, 0, 0),
                oldUpdated
        );

        // 执行
        template.update("NEW_CODE", null, null, null, null);

        // 断言：updatedAt 应该比旧的晚
        assertNotNull(template.getUpdatedAt(), "updatedAt 不应为 null");
        assertNotEquals(oldUpdated, template.getUpdatedAt(), "updatedAt 应被刷新，不再等于旧值");
        assertNotEquals(true, template.getUpdatedAt().isBefore(oldUpdated),
                "新的 updatedAt 不应早于旧的 updatedAt");
    }

    // ==================== replaceStarImages() 方法 ====================

    /**
     * 验证 replaceStarImages() 全量替换星级图片
     */
    @Test
    @DisplayName("replaceStarImages() 应全量替换星级图片列表")
    void replaceStarImages_shouldReplaceAllImages() {
        // 准备：构造已有星级图片的模板
        List<CardStarImage> oldImages = new ArrayList<>();
        oldImages.add(CardStarImage.create(1, "https://old.com/1.png"));
        CardTemplate template = CardTemplate.reconstruct(
                1L, 10L, "CODE", "名称", CardRarity.N,
                "描述", CardTemplateStatus.ACTIVE,
                oldImages,
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 1, 1, 0, 0)
        );

        // 新的星级图片列表
        List<CardStarImage> newImages = new ArrayList<>();
        newImages.add(CardStarImage.create(2, "https://new.com/2.png"));
        newImages.add(CardStarImage.create(3, "https://new.com/3.png"));

        // 执行
        template.replaceStarImages(newImages);

        // 断言
        assertEquals(2, template.getStarImages().size(), "替换后应有 2 个星级图片");
        assertEquals(2, template.getStarImages().get(0).getStarLevel(), "第一个星级应为 2");
        assertEquals(3, template.getStarImages().get(1).getStarLevel(), "第二个星级应为 3");
    }

    /**
     * 验证 replaceStarImages() 传 null 时清空列表
     */
    @Test
    @DisplayName("replaceStarImages() 传 null 时应清空星级图片列表")
    void replaceStarImages_shouldClearWhenNullPassed() {
        // 准备：构造已有星级图片的模板
        List<CardStarImage> oldImages = new ArrayList<>();
        oldImages.add(CardStarImage.create(1, "https://old.com/1.png"));
        oldImages.add(CardStarImage.create(2, "https://old.com/2.png"));
        CardTemplate template = CardTemplate.reconstruct(
                1L, 10L, "CODE", "名称", CardRarity.N,
                "描述", CardTemplateStatus.ACTIVE,
                oldImages,
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 1, 1, 0, 0)
        );

        // 执行：传 null
        template.replaceStarImages(null);

        // 断言
        assertTrue(template.getStarImages().isEmpty(), "传 null 后星级图片列表应为空");
    }

    // ==================== addOrUpdateStarImage() 方法 ====================

    /**
     * 验证 addOrUpdateStarImage() 添加新星级图片
     */
    @Test
    @DisplayName("addOrUpdateStarImage() 应添加新的星级图片")
    void addOrUpdateStarImage_shouldAddNewStarImage() {
        // 准备：构造空星级图片的模板
        CardTemplate template = CardTemplate.reconstruct(
                1L, 10L, "CODE", "名称", CardRarity.N,
                "描述", CardTemplateStatus.ACTIVE,
                new ArrayList<>(),
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 1, 1, 0, 0)
        );

        // 执行：添加一个新星级图片
        CardStarImage newImage = CardStarImage.create(3, "https://example.com/3.png");
        template.addOrUpdateStarImage(newImage);

        // 断言
        assertEquals(1, template.getStarImages().size(), "添加后应包含 1 个星级图片");
        assertEquals(3, template.getStarImages().get(0).getStarLevel(), "新星级应为 3");
        assertEquals("https://example.com/3.png", template.getStarImages().get(0).getImageUrl(),
                "imageUrl 应正确设置");
    }

    /**
     * 验证 addOrUpdateStarImage() 替换已有 starLevel 的图片（保持只有一条）
     */
    @Test
    @DisplayName("addOrUpdateStarImage() 应替换已有 starLevel 的图片")
    void addOrUpdateStarImage_shouldReplaceExistingStarLevel() {
        // 准备：构造已有星级图片的模板
        List<CardStarImage> oldImages = new ArrayList<>();
        oldImages.add(CardStarImage.create(1, "https://old.com/1.png"));
        oldImages.add(CardStarImage.create(2, "https://old.com/2.png"));
        CardTemplate template = CardTemplate.reconstruct(
                1L, 10L, "CODE", "名称", CardRarity.N,
                "描述", CardTemplateStatus.ACTIVE,
                oldImages,
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 1, 1, 0, 0)
        );

        // 执行：替换 starLevel=2 的图片
        CardStarImage updatedImage = CardStarImage.create(2, "https://new.com/2.png");
        template.addOrUpdateStarImage(updatedImage);

        // 断言：总数不变（1 个旧的 + 1 个新的替换 = 2），但 starLevel=2 的 imageUrl 已更新
        assertEquals(2, template.getStarImages().size(), "替换后总数应保持 2");
        // 找到 starLevel=2 的图片，验证 imageUrl 已更新
        CardStarImage level2Image = template.getStarImages().stream()
                .filter(img -> img.getStarLevel() == 2)
                .findFirst()
                .orElseThrow();
        assertEquals("https://new.com/2.png", level2Image.getImageUrl(),
                "starLevel=2 的 imageUrl 应被更新");
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
                "描述", CardTemplateStatus.ACTIVE,
                new ArrayList<>(),
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 1, 1, 0, 0)
        );

        // 执行
        template.deactivate();

        // 断言
        assertEquals(CardTemplateStatus.INACTIVE, template.getStatus(), "status 应变为 INACTIVE");
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
                "描述", CardTemplateStatus.ACTIVE,
                new ArrayList<>(),
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
