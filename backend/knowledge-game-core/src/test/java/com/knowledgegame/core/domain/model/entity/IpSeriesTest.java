package com.knowledgegame.core.domain.model.entity;

import com.knowledgegame.core.domain.model.domainenum.IpSeriesStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * IpSeries 领域实体单元测试
 * <p>
 * 覆盖工厂方法（create / reconstruct）、更新、软删除等核心行为。
 */
class IpSeriesTest {

    // ==================== create() 工厂方法 ====================

    /**
     * 验证 create() 工厂方法正确设置所有业务字段
     */
    @Test
    @DisplayName("create() 应正确设置 code、name、description、coverImageUrl、status")
    void create_shouldSetAllBusinessFields() {
        // 执行
        IpSeries series = IpSeries.create(
                "MARVEL",
                "漫威宇宙",
                "漫威超级英雄系列",
                "https://example.com/marvel.jpg",
                IpSeriesStatus.ACTIVE
        );

        // 断言业务字段
        assertEquals("MARVEL", series.getCode(), "code 应为 MARVEL");
        assertEquals("漫威宇宙", series.getName(), "name 应为 漫威宇宙");
        assertEquals("漫威超级英雄系列", series.getDescription(), "description 应正确设置");
        assertEquals("https://example.com/marvel.jpg", series.getCoverImageUrl(), "coverImageUrl 应正确设置");
        assertEquals(IpSeriesStatus.ACTIVE, series.getStatus(), "status 应为 ACTIVE");
    }

    /**
     * 验证 create() 工厂方法自动填充时间戳且 id 为 null
     */
    @Test
    @DisplayName("create() 应自动设置 createdAt 和 updatedAt，且 id 为 null")
    void create_shouldSetTimestampsAndIdNull() {
        // 记录调用前时刻
        LocalDateTime before = LocalDateTime.now();

        // 执行
        IpSeries series = IpSeries.create(
                "DC", "DC宇宙", "DC超级英雄", null, IpSeriesStatus.ACTIVE
        );

        // 记录调用后时刻
        LocalDateTime after = LocalDateTime.now();

        // id 在 create 时应为 null（由持久化层生成）
        assertNull(series.getId(), "create() 创建的对象 id 应为 null");

        // createdAt 和 updatedAt 应在 [before, after] 区间内
        assertNotNull(series.getCreatedAt(), "createdAt 不应为 null");
        assertNotNull(series.getUpdatedAt(), "updatedAt 不应为 null");
        // 粗略校验时间在合理范围内（不早于调用前，不晚于调用后）
        assertNotEquals(true, series.getCreatedAt().isBefore(before), "createdAt 不应早于调用前时刻");
        assertNotEquals(true, series.getCreatedAt().isAfter(after), "createdAt 不应晚于调用后时刻");
    }

    // ==================== reconstruct() 工厂方法 ====================

    /**
     * 验证 reconstruct() 从持久化数据完整重建领域对象
     */
    @Test
    @DisplayName("reconstruct() 应完整还原所有字段（含 id 和时间戳）")
    void reconstruct_shouldRestoreAllFields() {
        // 准备数据
        LocalDateTime created = LocalDateTime.of(2025, 1, 1, 10, 0, 0);
        LocalDateTime updated = LocalDateTime.of(2025, 6, 1, 15, 30, 0);

        // 执行
        IpSeries series = IpSeries.reconstruct(
                42L,
                "NARUTO",
                "火影忍者",
                "忍者世界系列",
                "https://example.com/naruto.jpg",
                IpSeriesStatus.ACTIVE,
                created,
                updated
        );

        // 断言全部字段
        assertEquals(42L, series.getId(), "id 应为 42");
        assertEquals("NARUTO", series.getCode(), "code 应为 NARUTO");
        assertEquals("火影忍者", series.getName(), "name 应为 火影忍者");
        assertEquals("忍者世界系列", series.getDescription(), "description 应正确还原");
        assertEquals("https://example.com/naruto.jpg", series.getCoverImageUrl(), "coverImageUrl 应正确还原");
        assertEquals(IpSeriesStatus.ACTIVE, series.getStatus(), "status 应为 ACTIVE");
        assertEquals(created, series.getCreatedAt(), "createdAt 应精确还原");
        assertEquals(updated, series.getUpdatedAt(), "updatedAt 应精确还原");
    }

    // ==================== update() 方法 ====================

    /**
     * 验证 update() 传入非 null 值时更新对应字段
     */
    @Test
    @DisplayName("update() 传入非 null 值时应更新 code、name、status 等字段")
    void update_shouldModifyFieldsWhenNonNull() {
        // 准备：构造一个已有对象
        IpSeries series = IpSeries.reconstruct(
                1L, "OLD_CODE", "旧名称", "旧描述", "old.jpg",
                IpSeriesStatus.ACTIVE,
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 1, 1, 0, 0)
        );

        // 执行：传入新的非 null 值
        series.update("NEW_CODE", "新名称", "新描述", "new.jpg", IpSeriesStatus.INACTIVE);

        // 断言字段已更新
        assertEquals("NEW_CODE", series.getCode(), "code 应被更新");
        assertEquals("新名称", series.getName(), "name 应被更新");
        assertEquals("新描述", series.getDescription(), "description 应被更新");
        assertEquals("new.jpg", series.getCoverImageUrl(), "coverImageUrl 应被更新");
        assertEquals(IpSeriesStatus.INACTIVE, series.getStatus(), "status 应被更新");
    }

    /**
     * 验证 update() 传入 null 时，code / name / status 保持不变
     * （注：description 和 coverImageUrl 在 update 中无 null 判断，传 null 会覆盖）
     */
    @Test
    @DisplayName("update() 传入 null 时，code、name、status 保持原值不变")
    void update_shouldKeepOriginalWhenNullPassed() {
        // 准备
        IpSeries series = IpSeries.reconstruct(
                1L, "ORIGINAL", "原始名称", "原始描述", "original.jpg",
                IpSeriesStatus.ACTIVE,
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 1, 1, 0, 0)
        );

        // 执行：code、name、status 传 null
        series.update(null, null, null, null, null);

        // 断言：有 null 守卫的字段保持不变
        assertEquals("ORIGINAL", series.getCode(), "code 传 null 时应保持原值");
        assertEquals("原始名称", series.getName(), "name 传 null 时应保持原值");
        assertEquals(IpSeriesStatus.ACTIVE, series.getStatus(), "status 传 null 时应保持原值");

        // description 和 coverImageUrl 无 null 守卫，传 null 会被覆盖为 null
        assertNull(series.getDescription(), "description 传 null 时会被覆盖为 null");
        assertNull(series.getCoverImageUrl(), "coverImageUrl 传 null 时会被覆盖为 null");
    }

    /**
     * 验证 update() 每次调用都会刷新 updatedAt
     */
    @Test
    @DisplayName("update() 应刷新 updatedAt 时间戳")
    void update_shouldRefreshUpdatedAt() {
        // 准备：构造一个 updatedAt 在过去的时间点
        LocalDateTime oldUpdated = LocalDateTime.of(2020, 1, 1, 0, 0);
        IpSeries series = IpSeries.reconstruct(
                1L, "CODE", "名称", "描述", "cover.jpg",
                IpSeriesStatus.ACTIVE,
                LocalDateTime.of(2020, 1, 1, 0, 0),
                oldUpdated
        );

        // 执行
        series.update("NEW_CODE", null, null, null, null);

        // 断言：updatedAt 应该比旧的晚
        assertNotNull(series.getUpdatedAt(), "updatedAt 不应为 null");
        assertNotEquals(oldUpdated, series.getUpdatedAt(), "updatedAt 应被刷新，不再等于旧值");
        assertNotEquals(true, series.getUpdatedAt().isBefore(oldUpdated),
                "新的 updatedAt 不应早于旧的 updatedAt");
    }

    // ==================== deactivate() 方法 ====================

    /**
     * 验证 deactivate() 将 status 设置为 INACTIVE
     */
    @Test
    @DisplayName("deactivate() 应将 status 变为 INACTIVE")
    void deactivate_shouldSetStatusToInactive() {
        // 准备：构造一个 ACTIVE 状态的对象
        IpSeries series = IpSeries.reconstruct(
                1L, "CODE", "名称", "描述", "cover.jpg",
                IpSeriesStatus.ACTIVE,
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 1, 1, 0, 0)
        );

        // 执行
        series.deactivate();

        // 断言
        assertEquals(IpSeriesStatus.INACTIVE, series.getStatus(), "status 应变为 INACTIVE");
    }

    /**
     * 验证 deactivate() 同时刷新 updatedAt 时间戳
     */
    @Test
    @DisplayName("deactivate() 应刷新 updatedAt 时间戳")
    void deactivate_shouldRefreshUpdatedAt() {
        // 准备：构造一个 updatedAt 在过去的时间点
        LocalDateTime oldUpdated = LocalDateTime.of(2020, 1, 1, 0, 0);
        IpSeries series = IpSeries.reconstruct(
                1L, "CODE", "名称", "描述", "cover.jpg",
                IpSeriesStatus.ACTIVE,
                LocalDateTime.of(2020, 1, 1, 0, 0),
                oldUpdated
        );

        // 执行
        series.deactivate();

        // 断言：updatedAt 应被刷新
        assertNotNull(series.getUpdatedAt(), "updatedAt 不应为 null");
        assertNotEquals(oldUpdated, series.getUpdatedAt(), "updatedAt 应被刷新");
    }
}
