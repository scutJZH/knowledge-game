package com.knowledgegame.app.application.service;

import com.knowledgegame.app.api.dto.response.ActiveIpSeriesResponse;
import com.knowledgegame.core.domain.model.domainenum.IpSeriesStatus;
import com.knowledgegame.core.domain.model.entity.IpSeries;
import com.knowledgegame.core.domain.model.vo.FileRef;
import com.knowledgegame.core.domain.port.outbound.IpSeriesRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IpSeriesAppServiceTest {

    @Mock
    private IpSeriesRepositoryPort ipSeriesRepositoryPort;

    @InjectMocks
    private IpSeriesAppService appService;

    @Test
    @DisplayName("listActive → 仅返回 ACTIVE 列表，含 null coverImage 的 IP 字段映射正确")
    void shouldReturnActiveIpSeriesWithCorrectMapping() {
        IpSeries ip1 = IpSeries.reconstruct(
                1L, "PKM", "宝可梦", null,
                FileRef.of(100L, "https://example.com/cover.png"),
                IpSeriesStatus.ACTIVE,
                null, null);
        IpSeries ip2 = IpSeries.reconstruct(
                2L, "DM", "数码宝贝", null,
                null,
                IpSeriesStatus.ACTIVE,
                null, null);
        when(ipSeriesRepositoryPort.findAllActive()).thenReturn(List.of(ip1, ip2));

        List<ActiveIpSeriesResponse> result = appService.listActive();

        assertEquals(2, result.size());
        // ip1: 有 coverImage
        assertEquals(1L, result.get(0).getId());
        assertEquals("PKM", result.get(0).getCode());
        assertEquals(100L, result.get(0).getCoverImageFileId());
        assertEquals("https://example.com/cover.png", result.get(0).getCoverImageUrl());
        // ip2: 无 coverImage
        assertEquals(2L, result.get(1).getId());
        assertEquals("DM", result.get(1).getCode());
        assertNull(result.get(1).getCoverImageFileId());
        assertNull(result.get(1).getCoverImageUrl());
    }

    @Test
    @DisplayName("listActive → 空列表返回空 List")
    void shouldReturnEmptyListWhenNoActiveIpSeries() {
        when(ipSeriesRepositoryPort.findAllActive()).thenReturn(List.of());

        List<ActiveIpSeriesResponse> result = appService.listActive();

        assertTrue(result.isEmpty());
    }
}
