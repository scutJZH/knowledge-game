package com.knowledgegame.admin.application.service;

import com.knowledgegame.admin.api.dto.response.IpSeriesResponse;
import com.knowledgegame.components.feign.client.FileServiceClient;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.domainenum.IpSeriesStatus;
import com.knowledgegame.core.domain.model.entity.IpSeries;
import com.knowledgegame.core.domain.model.vo.FileRef;
import com.knowledgegame.core.domain.model.vo.PageResult;
import com.knowledgegame.core.domain.model.vo.SortField;
import com.knowledgegame.core.domain.port.outbound.IpSeriesRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IpSeriesAppServiceTest {

    @Mock
    private IpSeriesRepositoryPort ipSeriesRepositoryPort;

    @Mock
    private FileServiceClient fileServiceClient;

    @InjectMocks
    private IpSeriesAppService ipSeriesAppService;

    private static IpSeries buildIpSeries(Long id, String code, String name, String desc,
                                          IpSeriesStatus status) {
        return IpSeries.reconstruct(id, code, name, desc, null, status,
                LocalDateTime.now(), LocalDateTime.now());
    }

    @Test
    void createIpSeries_shouldSucceed_whenCodeAndNameAreUnique() {
        String code = "MARVEL";
        String name = "漫威宇宙";
        String description = "漫威超级英雄系列";
        IpSeriesStatus status = IpSeriesStatus.ACTIVE;

        when(ipSeriesRepositoryPort.findByCode(code)).thenReturn(Optional.empty());
        when(ipSeriesRepositoryPort.findByName(name)).thenReturn(Optional.empty());
        IpSeries saved = buildIpSeries(1L, code, name, description, status);
        when(ipSeriesRepositoryPort.save(any(IpSeries.class))).thenReturn(saved);

        IpSeriesResponse result = ipSeriesAppService.createIpSeries(
                code, name, description, null, status);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(code, result.getCode());
        assertEquals(name, result.getName());
        assertEquals("ACTIVE", result.getStatus());
        verify(ipSeriesRepositoryPort).findByCode(code);
        verify(ipSeriesRepositoryPort).findByName(name);
        verify(ipSeriesRepositoryPort).save(any(IpSeries.class));
    }

    @Test
    void createIpSeries_shouldThrow_whenCodeDuplicate() {
        String code = "MARVEL";
        String name = "漫威宇宙";
        String description = "漫威超级英雄系列";
        IpSeriesStatus status = IpSeriesStatus.ACTIVE;

        IpSeries existing = buildIpSeries(1L, code, "其他名称", description, status);
        when(ipSeriesRepositoryPort.findByCode(code)).thenReturn(Optional.of(existing));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> ipSeriesAppService.createIpSeries(code, name, description, null, status));
        assertEquals("IP 系列编码已存在: " + code, exception.getMessage());
    }

    @Test
    void createIpSeries_shouldThrow_whenNameDuplicate() {
        String code = "MARVEL";
        String name = "漫威宇宙";
        String description = "漫威超级英雄系列";
        IpSeriesStatus status = IpSeriesStatus.ACTIVE;

        when(ipSeriesRepositoryPort.findByCode(code)).thenReturn(Optional.empty());
        IpSeries existing = buildIpSeries(2L, "OTHER", name, description, status);
        when(ipSeriesRepositoryPort.findByName(name)).thenReturn(Optional.of(existing));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> ipSeriesAppService.createIpSeries(code, name, description, null, status));
        assertEquals("IP 系列名称已存在: " + name, exception.getMessage());
    }

    @Test
    void getIpSeriesById_shouldReturn_whenExists() {
        Long id = 1L;
        IpSeries ipSeries = buildIpSeries(id, "MARVEL", "漫威宇宙", "描述", IpSeriesStatus.ACTIVE);
        when(ipSeriesRepositoryPort.findById(id)).thenReturn(Optional.of(ipSeries));

        IpSeriesResponse result = ipSeriesAppService.getIpSeriesById(id);

        assertNotNull(result);
        assertEquals(id, result.getId());
        assertEquals("MARVEL", result.getCode());
        assertEquals("漫威宇宙", result.getName());
        assertEquals("ACTIVE", result.getStatus());
        verify(ipSeriesRepositoryPort).findById(id);
    }

    @Test
    void getIpSeriesById_shouldThrow_whenNotFound() {
        Long id = 999L;
        when(ipSeriesRepositoryPort.findById(id)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> ipSeriesAppService.getIpSeriesById(id));
        assertEquals("IP 系列不存在: " + id, exception.getMessage());
    }

    @Test
    void listIpSeries_shouldReturnPagedResult() {
        IpSeries series1 = buildIpSeries(1L, "MARVEL", "漫威宇宙", "描述1", IpSeriesStatus.ACTIVE);
        IpSeries series2 = buildIpSeries(2L, "DC", "漫威和DC", "描述2", IpSeriesStatus.ACTIVE);
        PageResult<IpSeries> mockPageResult = PageResult.<IpSeries>builder()
                .content(List.of(series1, series2))
                .totalElements(2).pageNumber(0).pageSize(10).totalPages(1).build();

        when(ipSeriesRepositoryPort.findByConditions(eq("漫威"), eq(null), eq(IpSeriesStatus.ACTIVE),
                eq(null), eq(0), eq(10)))
                .thenReturn(mockPageResult);

        PageResult<IpSeriesResponse> result = ipSeriesAppService.listIpSeries(
                "漫威", null, "ACTIVE", null, null, 0, 10);

        assertNotNull(result);
        assertEquals(2, result.getContent().size());
        assertEquals("MARVEL", result.getContent().get(0).getCode());
        assertEquals("DC", result.getContent().get(1).getCode());
    }

    /**
     * sort/order 透传到 Port：SortField.parse("code","asc") 应构造 SortField("code", ASC)
     */
    @Test
    void listIpSeries_shouldPassSortFieldToPort() {
        IpSeries series = buildIpSeries(1L, "MARVEL", "漫威宇宙", "描述", IpSeriesStatus.ACTIVE);
        PageResult<IpSeries> mockPage = PageResult.<IpSeries>builder()
                .content(List.of(series))
                .totalElements(1).pageNumber(0).pageSize(20).totalPages(1).build();

        when(ipSeriesRepositoryPort.findByConditions(eq(null), eq(null), eq(null),
                argThat(sf -> sf != null
                        && sf.getField().equals("code")
                        && sf.getDirection() == SortField.Direction.ASC),
                eq(0), eq(20)))
                .thenReturn(mockPage);

        ipSeriesAppService.listIpSeries(null, null, null, "code", "asc", 0, 20);

        verify(ipSeriesRepositoryPort).findByConditions(eq(null), eq(null), eq(null),
                argThat(sf -> sf != null
                        && sf.getField().equals("code")
                        && sf.getDirection() == SortField.Direction.ASC),
                eq(0), eq(20));
    }

    @Test
    void updateIpSeries_shouldSucceed_whenCodeAndNameNotChanged() {
        Long id = 1L;
        String code = "MARVEL";
        String name = "漫威宇宙";
        String newDescription = "新描述";
        IpSeriesStatus status = IpSeriesStatus.ACTIVE;

        IpSeries existing = buildIpSeries(id, code, name, "旧描述", IpSeriesStatus.ACTIVE);
        when(ipSeriesRepositoryPort.findById(id)).thenReturn(Optional.of(existing));
        IpSeries saved = buildIpSeries(id, code, name, newDescription, status);
        when(ipSeriesRepositoryPort.save(any(IpSeries.class))).thenReturn(saved);

        IpSeriesResponse result = ipSeriesAppService.updateIpSeries(
                id, code, name, newDescription, null, status);

        assertNotNull(result);
        assertEquals(id, result.getId());
        assertEquals(newDescription, result.getDescription());
        verify(ipSeriesRepositoryPort).findById(id);
        verify(ipSeriesRepositoryPort).save(any(IpSeries.class));
    }

    @Test
    void updateIpSeries_shouldThrow_whenCodeDuplicateExcludingSelf() {
        Long id = 1L;
        String newCode = "DC";
        String name = "漫威宇宙";
        IpSeriesStatus status = IpSeriesStatus.ACTIVE;

        IpSeries existing = buildIpSeries(id, "MARVEL", name, "描述", status);
        when(ipSeriesRepositoryPort.findById(id)).thenReturn(Optional.of(existing));
        IpSeries conflict = buildIpSeries(2L, newCode, "DC宇宙", "描述", status);
        when(ipSeriesRepositoryPort.findByCode(newCode)).thenReturn(Optional.of(conflict));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> ipSeriesAppService.updateIpSeries(id, newCode, name, "描述", null, status));
        assertEquals("IP 系列编码已存在: " + newCode, exception.getMessage());
    }

    @Test
    void updateIpSeries_shouldThrow_whenNameDuplicateExcludingSelf() {
        Long id = 1L;
        String code = "MARVEL";
        String newName = "DC宇宙";
        IpSeriesStatus status = IpSeriesStatus.ACTIVE;

        IpSeries existing = buildIpSeries(id, code, "漫威宇宙", "描述", status);
        when(ipSeriesRepositoryPort.findById(id)).thenReturn(Optional.of(existing));
        IpSeries conflict = buildIpSeries(2L, "DC", newName, "描述", status);
        when(ipSeriesRepositoryPort.findByName(newName)).thenReturn(Optional.of(conflict));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> ipSeriesAppService.updateIpSeries(id, code, newName, "描述", null, status));
        assertEquals("IP 系列名称已存在: " + newName, exception.getMessage());
    }

    @Test
    void updateIpSeries_shouldAllowCaseChangeOnName() {
        Long id = 1L;
        String code = "MARVEL";
        String originalName = "pokemon";
        IpSeriesStatus status = IpSeriesStatus.ACTIVE;

        IpSeries existing = buildIpSeries(id, code, originalName, "描述", status);
        when(ipSeriesRepositoryPort.findById(id)).thenReturn(Optional.of(existing));
        when(ipSeriesRepositoryPort.findByName("POKEMON")).thenReturn(Optional.of(existing));
        when(ipSeriesRepositoryPort.save(any(IpSeries.class))).thenAnswer(inv -> inv.getArgument(0));

        IpSeriesResponse result = ipSeriesAppService.updateIpSeries(
                id, code, "POKEMON", "描述", null, status);

        assertNotNull(result);
        verify(ipSeriesRepositoryPort).save(any(IpSeries.class));
    }

    @Test
    void deleteIpSeries_shouldDeactivate() {
        Long id = 1L;
        IpSeries existing = buildIpSeries(id, "MARVEL", "漫威宇宙", "描述", IpSeriesStatus.ACTIVE);
        when(ipSeriesRepositoryPort.findById(id)).thenReturn(Optional.of(existing));
        when(ipSeriesRepositoryPort.save(any(IpSeries.class))).thenAnswer(inv -> inv.getArgument(0));

        ipSeriesAppService.deleteIpSeries(id);

        verify(ipSeriesRepositoryPort).save(argThat(series ->
                series.getStatus() == IpSeriesStatus.INACTIVE));
    }

    @Test
    void deleteIpSeries_shouldNotSave_whenNotFound() {
        Long id = 999L;
        when(ipSeriesRepositoryPort.findById(id)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> ipSeriesAppService.deleteIpSeries(id));
        assertEquals("IP 系列不存在: " + id, ex.getMessage());

        verify(ipSeriesRepositoryPort, never()).save(any());
    }
}
