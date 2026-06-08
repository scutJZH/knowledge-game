package com.knowledgegame.core.domain.model.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * CardStarImage 值对象单元测试
 * <p>
 * 覆盖工厂方法、相等性（由 starLevel 决定）、hashCode 一致性。
 */
class CardStarImageTest {

    // ==================== create() 工厂方法 ====================

    /**
     * 验证 create() 工厂方法正确设置 starLevel 和 imageUrl
     */
    @Test
    @DisplayName("create() 应正确设置 starLevel 和 imageUrl")
    void create_shouldSetStarLevelAndImageUrl() {
        // 执行
        CardStarImage image = CardStarImage.create(3, "https://example.com/star3.png");

        // 断言
        assertEquals(3, image.getStarLevel(), "starLevel 应为 3");
        assertEquals("https://example.com/star3.png", image.getImageUrl(), "imageUrl 应正确设置");
    }

    // ==================== equals() 相等性 ====================

    /**
     * 验证相同 starLevel 的两个 CardStarImage 相等
     */
    @Test
    @DisplayName("equals() 相同 starLevel 应判为相等")
    void equals_shouldReturnTrueWhenStarLevelIsSame() {
        // 准备
        CardStarImage image1 = CardStarImage.create(2, "https://a.com/img.png");
        CardStarImage image2 = CardStarImage.create(2, "https://b.com/other.png");

        // 断言：starLevel 相同即相等
        assertEquals(image1, image2, "starLevel 相同的 CardStarImage 应相等");
    }

    /**
     * 验证不同 starLevel 的两个 CardStarImage 不相等
     */
    @Test
    @DisplayName("equals() 不同 starLevel 应判为不相等")
    void equals_shouldReturnFalseWhenStarLevelIsDifferent() {
        // 准备
        CardStarImage image1 = CardStarImage.create(1, "https://example.com/img.png");
        CardStarImage image2 = CardStarImage.create(2, "https://example.com/img.png");

        // 断言：starLevel 不同则不相等
        assertNotEquals(image1, image2, "starLevel 不同的 CardStarImage 不应相等");
    }

    /**
     * 验证不同 imageUrl 但相同 starLevel 时仍相等
     */
    @Test
    @DisplayName("equals() 不同 imageUrl 但相同 starLevel 仍相等")
    void equals_shouldReturnTrueWhenImageUrlDiffersButStarLevelSame() {
        // 准备
        CardStarImage image1 = CardStarImage.create(5, "https://a.com/first.png");
        CardStarImage image2 = CardStarImage.create(5, "https://b.com/second.png");

        // 断言：相等性只由 starLevel 决定，与 imageUrl 无关
        assertEquals(image1, image2, "starLevel 相同时，即使 imageUrl 不同也应相等");
    }

    // ==================== hashCode() 一致性 ====================

    /**
     * 验证 equals() 相等的对象 hashCode() 也相同
     */
    @Test
    @DisplayName("hashCode() 与 equals() 保持一致：相等的对象 hashCode 必须相同")
    void hashCode_shouldBeConsistentWithEquals() {
        // 准备：两个 starLevel 相同但 imageUrl 不同的对象
        CardStarImage image1 = CardStarImage.create(4, "https://a.com/img.png");
        CardStarImage image2 = CardStarImage.create(4, "https://b.com/other.png");

        // 断言：equals 相等 => hashCode 必须相同
        assertEquals(image1.hashCode(), image2.hashCode(),
                "equals 相等的对象，hashCode 必须相同");
    }

    /**
     * 验证不同 starLevel 的对象 hashCode 通常不同
     */
    @Test
    @DisplayName("hashCode() 不同 starLevel 通常产生不同 hashCode")
    void hashCode_shouldDifferForDifferentStarLevel() {
        // 准备
        CardStarImage image1 = CardStarImage.create(1, "https://example.com/img.png");
        CardStarImage image2 = CardStarImage.create(2, "https://example.com/img.png");

        // 断言：equals 不相等 => hashCode 通常不同（非强制但符合预期）
        assertNotEquals(image1.hashCode(), image2.hashCode(),
                "starLevel 不同时，hashCode 通常应不同");
    }
}
