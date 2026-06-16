package com.knowledgegame.core.domain.model.entity;

import com.knowledgegame.core.domain.model.domainenum.IpSeriesStatus;
import com.knowledgegame.core.domain.model.vo.FileRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * IpSeries 领域实体单元测试
 */
class IpSeriesTest {

    private static final FileRef COVER = FileRef.of(1L, "/static/test.jpg");

    @Test
    @DisplayName("create() 应正确设置所有业务字段")
    void create_shouldSetAllBusinessFields() {
        IpSeries series = IpSeries.create(
                "MARVEL", "漫威宇宙", "漫威超级英雄系列", COVER, IpSeriesStatus.ACTIVE);

        assertEquals("MARVEL", series.getCode());
        assertEquals("漫威宇宙", series.getName());
        assertEquals("漫威超级英雄系列", series.getDescription());
        assertEquals(COVER, series.getCoverImage());
        assertEquals(IpSeriesStatus.ACTIVE, series.getStatus());
    }

    @Test
    @DisplayName("create() 应自动设置时间戳且 id 为 null")
    void create_shouldSetTimestampsAndIdNull() {
        IpSeries series = IpSeries.create(
                "DC", "DC宇宙", "DC超级英雄", null, IpSeriesStatus.ACTIVE);

        assertNull(series.getId());
        assertNull(series.getCoverImage());
        assertNotNull(series.getCreatedAt());
        assertNotNull(series.getUpdatedAt());
    }

    @Test
    @DisplayName("reconstruct() 应完整还原所有字段")
    void reconstruct_shouldRestoreAllFields() {
        LocalDateTime created = LocalDateTime.of(2025, 1, 1, 10, 0, 0);
        LocalDateTime updated = LocalDateTime.of(2025, 6, 1, 15, 30, 0);

        IpSeries series = IpSeries.reconstruct(
                42L, "NARUTO", "火影忍者", "忍者世界系列", COVER,
                IpSeriesStatus.ACTIVE, created, updated);

        assertEquals(42L, series.getId());
        assertEquals("NARUTO", series.getCode());
        assertEquals("火影忍者", series.getName());
        assertEquals("忍者世界系列", series.getDescription());
        assertEquals(COVER, series.getCoverImage());
        assertEquals(IpSeriesStatus.ACTIVE, series.getStatus());
        assertEquals(created, series.getCreatedAt());
        assertEquals(updated, series.getUpdatedAt());
    }

    @Test
    @DisplayName("update() 传入非 null 值时应更新字段")
    void update_shouldModifyFieldsWhenNonNull() {
        IpSeries series = IpSeries.reconstruct(
                1L, "OLD_CODE", "旧名称", "旧描述", COVER,
                IpSeriesStatus.ACTIVE,
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 1, 1, 0, 0));

        FileRef newCover = FileRef.of(2L, "/static/new.jpg");
        series.update("NEW_CODE", "新名称", "新描述", newCover, IpSeriesStatus.INACTIVE);

        assertEquals("NEW_CODE", series.getCode());
        assertEquals("新名称", series.getName());
        assertEquals("新描述", series.getDescription());
        assertEquals(newCover, series.getCoverImage());
        assertEquals(IpSeriesStatus.INACTIVE, series.getStatus());
    }

    @Test
    @DisplayName("update() 传入 null code/name/status 时保持原值，description/coverImage 被覆盖")
    void update_shouldKeepOriginalWhenNullPassed() {
        IpSeries series = IpSeries.reconstruct(
                1L, "ORIGINAL", "原始名称", "原始描述", COVER,
                IpSeriesStatus.ACTIVE,
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 1, 1, 0, 0));

        series.update(null, null, null, null, null);

        assertEquals("ORIGINAL", series.getCode());
        assertEquals("原始名称", series.getName());
        assertEquals(IpSeriesStatus.ACTIVE, series.getStatus());
        assertNull(series.getDescription());
        assertNull(series.getCoverImage());
    }

    @Test
    @DisplayName("update() 应刷新 updatedAt 时间戳")
    void update_shouldRefreshUpdatedAt() {
        LocalDateTime oldUpdated = LocalDateTime.of(2020, 1, 1, 0, 0);
        IpSeries series = IpSeries.reconstruct(
                1L, "CODE", "名称", "描述", COVER,
                IpSeriesStatus.ACTIVE,
                LocalDateTime.of(2020, 1, 1, 0, 0), oldUpdated);

        series.update("NEW_CODE", null, null, null, null);

        assertNotNull(series.getUpdatedAt());
        assertNotEquals(oldUpdated, series.getUpdatedAt());
    }

    @Test
    @DisplayName("deactivate() 应将 status 变为 INACTIVE")
    void deactivate_shouldSetStatusToInactive() {
        IpSeries series = IpSeries.reconstruct(
                1L, "CODE", "名称", "描述", COVER,
                IpSeriesStatus.ACTIVE,
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 1, 1, 0, 0));

        series.deactivate();

        assertEquals(IpSeriesStatus.INACTIVE, series.getStatus());
    }

    @Test
    @DisplayName("deactivate() 应刷新 updatedAt 时间戳")
    void deactivate_shouldRefreshUpdatedAt() {
        LocalDateTime oldUpdated = LocalDateTime.of(2020, 1, 1, 0, 0);
        IpSeries series = IpSeries.reconstruct(
                1L, "CODE", "名称", "描述", COVER,
                IpSeriesStatus.ACTIVE,
                LocalDateTime.of(2020, 1, 1, 0, 0), oldUpdated);

        series.deactivate();

        assertNotNull(series.getUpdatedAt());
        assertNotEquals(oldUpdated, series.getUpdatedAt());
    }
}
