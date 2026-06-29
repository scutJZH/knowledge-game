package com.knowledgegame.app.api.assembler;

import com.knowledgegame.app.api.dto.response.ActiveIpSeriesResponse;
import com.knowledgegame.core.domain.model.domainenum.IpSeriesStatus;
import com.knowledgegame.core.domain.model.entity.IpSeries;
import com.knowledgegame.core.domain.model.vo.FileRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class IpSeriesAssemblerTest {

    @Test
    @DisplayName("完整字段映射 → 所有字段正确转换")
    void shouldMapAllFields() {
        IpSeries ipSeries = IpSeries.reconstruct(
                1L, "PKM", "宝可梦", "经典IP系列",
                FileRef.of(100L, "https://example.com/cover.png"),
                IpSeriesStatus.ACTIVE,
                null, null);

        ActiveIpSeriesResponse response = IpSeriesAssembler.INSTANCE.toActiveResponse(ipSeries);

        assertEquals(1L, response.getId());
        assertEquals("PKM", response.getCode());
        assertEquals("宝可梦", response.getName());
        assertEquals(100L, response.getCoverImageFileId());
        assertEquals("https://example.com/cover.png", response.getCoverImageUrl());
    }

    @Test
    @DisplayName("coverImage 为 null → coverImageFileId 和 coverImageUrl 均为 null")
    void shouldReturnNullCoverImageFieldsWhenCoverImageIsNull() {
        IpSeries ipSeries = IpSeries.reconstruct(
                1L, "PKM", "宝可梦", null,
                null,
                IpSeriesStatus.ACTIVE,
                null, null);

        ActiveIpSeriesResponse response = IpSeriesAssembler.INSTANCE.toActiveResponse(ipSeries);

        assertNull(response.getCoverImageFileId());
        assertNull(response.getCoverImageUrl());
    }

    @Test
    @DisplayName("字段精简 → DTO 不含 description/status/createdAt/updatedAt")
    void shouldNotExposeManagementFields() {
        IpSeries ipSeries = IpSeries.reconstruct(
                1L, "PKM", "宝可梦", "不应出现的描述",
                FileRef.of(100L, "https://example.com/cover.png"),
                IpSeriesStatus.ACTIVE,
                null, null);

        ActiveIpSeriesResponse response = IpSeriesAssembler.INSTANCE.toActiveResponse(ipSeries);

        assertEquals(1L, response.getId());
        assertEquals("PKM", response.getCode());
        assertEquals("宝可梦", response.getName());
        assertEquals(100L, response.getCoverImageFileId());
        assertEquals("https://example.com/cover.png", response.getCoverImageUrl());
        // DTO 只有 5 个字段，无 description/status/createdAt/updatedAt getter
    }
}
