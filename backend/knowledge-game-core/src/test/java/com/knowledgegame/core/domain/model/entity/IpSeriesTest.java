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
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    @DisplayName("update() 必填字段传入非 null 值时更新；可清空字段保持原值")
    void update_shouldModifyRequiredFieldsWhenNonNull() {
        IpSeries series = IpSeries.reconstruct(
                1L, "OLD_CODE", "旧名称", "旧描述", COVER,
                IpSeriesStatus.ACTIVE,
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 1, 1, 0, 0));

        series.update("NEW_CODE", "新名称", IpSeriesStatus.INACTIVE);

        assertEquals("NEW_CODE", series.getCode());
        assertEquals("新名称", series.getName());
        assertEquals(IpSeriesStatus.INACTIVE, series.getStatus());
        // 可清空字段未触碰
        assertEquals("旧描述", series.getDescription());
        assertEquals(COVER, series.getCoverImage());
    }

    @Test
    @DisplayName("update() 必填字段传入 null 时保持原值")
    void update_shouldKeepRequiredOriginalWhenNull() {
        IpSeries series = IpSeries.reconstruct(
                1L, "ORIGINAL", "原始名称", "原始描述", COVER,
                IpSeriesStatus.ACTIVE,
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 1, 1, 0, 0));

        series.update(null, null, null);

        assertEquals("ORIGINAL", series.getCode());
        assertEquals("原始名称", series.getName());
        assertEquals(IpSeriesStatus.ACTIVE, series.getStatus());
        // 可清空字段未触碰
        assertEquals("原始描述", series.getDescription());
        assertEquals(COVER, series.getCoverImage());
    }

    @Test
    @DisplayName("update() 应刷新 updatedAt 时间戳")
    void update_shouldRefreshUpdatedAt() {
        LocalDateTime oldUpdated = LocalDateTime.of(2020, 1, 1, 0, 0);
        IpSeries series = IpSeries.reconstruct(
                1L, "CODE", "名称", "描述", COVER,
                IpSeriesStatus.ACTIVE,
                LocalDateTime.of(2020, 1, 1, 0, 0), oldUpdated);

        series.update("NEW_CODE", null, null);

        assertNotNull(series.getUpdatedAt());
        assertNotEquals(oldUpdated, series.getUpdatedAt());
    }

    // ============ updateDescription / clearDescription ============

    @Test
    @DisplayName("updateDescription() 应写入新描述")
    void updateDescription_shouldWriteNewValue() {
        IpSeries series = IpSeries.create("CODE", "名称", "旧", null, IpSeriesStatus.ACTIVE);
        series.updateDescription("新描述");
        assertEquals("新描述", series.getDescription());
    }

    @Test
    @DisplayName("updateDescription(null) 应抛 IllegalArgumentException")
    void updateDescription_shouldThrowWhenNull() {
        IpSeries series = IpSeries.create("CODE", "名称", "旧", null, IpSeriesStatus.ACTIVE);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> series.updateDescription(null));
        assertEquals("description 清空请用 clearDescription()", ex.getMessage());
    }

    @Test
    @DisplayName("clearDescription() 应清空描述")
    void clearDescription_shouldSetNull() {
        IpSeries series = IpSeries.create("CODE", "名称", "旧描述", null, IpSeriesStatus.ACTIVE);
        series.clearDescription();
        assertNull(series.getDescription());
    }

    // ============ updateCoverImage / clearCoverImage ============

    @Test
    @DisplayName("updateCoverImage() 应写入新封面")
    void updateCoverImage_shouldWriteNewValue() {
        IpSeries series = IpSeries.create("CODE", "名称", null, COVER, IpSeriesStatus.ACTIVE);
        FileRef newCover = FileRef.of(99L, "/static/new.jpg");
        series.updateCoverImage(newCover);
        assertEquals(newCover, series.getCoverImage());
    }

    @Test
    @DisplayName("updateCoverImage(null) 应抛 IllegalArgumentException")
    void updateCoverImage_shouldThrowWhenNull() {
        IpSeries series = IpSeries.create("CODE", "名称", null, COVER, IpSeriesStatus.ACTIVE);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> series.updateCoverImage(null));
        assertEquals("coverImage 清空请用 clearCoverImage()", ex.getMessage());
    }

    @Test
    @DisplayName("clearCoverImage() 应清空封面图")
    void clearCoverImage_shouldSetNull() {
        IpSeries series = IpSeries.create("CODE", "名称", null, COVER, IpSeriesStatus.ACTIVE);
        series.clearCoverImage();
        assertNull(series.getCoverImage());
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
