package com.knowledgegame.admin.application.service;

import com.knowledgegame.admin.api.dto.response.IpSeriesResponse;
import com.knowledgegame.common.exception.BusinessException;
import com.knowledgegame.domain.model.domainenum.IpSeriesStatus;
import com.knowledgegame.domain.model.entity.IpSeries;
import com.knowledgegame.domain.model.vo.PageResult;
import com.knowledgegame.domain.port.outbound.IpSeriesRepositoryPort;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IpSeriesAppService 单元测试
 * 使用 Mockito mock RepositoryPort，验证应用服务的业务编排逻辑
 */
@ExtendWith(MockitoExtension.class)
class IpSeriesAppServiceTest {

    @Mock
    private IpSeriesRepositoryPort ipSeriesRepositoryPort;

    @InjectMocks
    private IpSeriesAppService ipSeriesAppService;

    /**
     * 构建测试用的 IpSeriesResponse DTO
     */
    private IpSeriesResponse buildResponse(Long id, String code, String name, String description,
                                           String coverImageUrl, String status) {
        return IpSeriesResponse.builder()
                .id(id)
                .code(code)
                .name(name)
                .description(description)
                .coverImageUrl(coverImageUrl)
                .status(status)
                .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0, 0))
                .updatedAt(LocalDateTime.of(2026, 1, 1, 0, 0, 0))
                .build();
    }

    /**
     * 创建 IP 系列 - 正常创建成功
     */
    @Test
    void createIpSeries_shouldSucceed_whenCodeAndNameAreUnique() {
        // 准备参数
        String code = "MARVEL";
        String name = "漫威宇宙";
        String description = "漫威超级英雄系列";
        String coverImageUrl = "https://example.com/marvel.jpg";
        IpSeriesStatus status = IpSeriesStatus.ACTIVE;

        // code 和 name 均不存在，返回 empty
        when(ipSeriesRepositoryPort.findByCode(code)).thenReturn(Optional.empty());
        when(ipSeriesRepositoryPort.findByName(name)).thenReturn(Optional.empty());
        // save 返回带 id 的领域对象
        IpSeries saved = IpSeries.reconstruct(1L, code, name, description, coverImageUrl, status,
                LocalDateTime.now(), LocalDateTime.now());
        when(ipSeriesRepositoryPort.save(any(IpSeries.class))).thenReturn(saved);

        // 执行（AppService 内部将领域对象转换为 DTO 返回）
        IpSeriesResponse result = ipSeriesAppService.createIpSeries(code, name, description, coverImageUrl, status);

        // 验证返回的 DTO 字段
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(code, result.getCode());
        assertEquals(name, result.getName());
        assertEquals("ACTIVE", result.getStatus());
        verify(ipSeriesRepositoryPort).findByCode(code);
        verify(ipSeriesRepositoryPort).findByName(name);
        verify(ipSeriesRepositoryPort).save(any(IpSeries.class));
    }

    /**
     * 创建 IP 系列 - code 重复抛 BusinessException
     */
    @Test
    void createIpSeries_shouldThrow_whenCodeDuplicate() {
        // 准备参数
        String code = "MARVEL";
        String name = "漫威宇宙";
        String description = "漫威超级英雄系列";
        String coverImageUrl = "https://example.com/marvel.jpg";
        IpSeriesStatus status = IpSeriesStatus.ACTIVE;

        // 模拟 code 已存在
        IpSeries existing = IpSeries.reconstruct(1L, code, "其他名称", description, coverImageUrl, status,
                LocalDateTime.now(), LocalDateTime.now());
        when(ipSeriesRepositoryPort.findByCode(code)).thenReturn(Optional.of(existing));

        // 执行并验证异常
        BusinessException exception = assertThrows(BusinessException.class,
                () -> ipSeriesAppService.createIpSeries(code, name, description, coverImageUrl, status));
        assertEquals("IP 系列编码已存在: " + code, exception.getMessage());
    }

    /**
     * 创建 IP 系列 - name 重复抛 BusinessException
     */
    @Test
    void createIpSeries_shouldThrow_whenNameDuplicate() {
        // 准备参数
        String code = "MARVEL";
        String name = "漫威宇宙";
        String description = "漫威超级英雄系列";
        String coverImageUrl = "https://example.com/marvel.jpg";
        IpSeriesStatus status = IpSeriesStatus.ACTIVE;

        // code 不存在，但 name 已存在
        when(ipSeriesRepositoryPort.findByCode(code)).thenReturn(Optional.empty());
        IpSeries existing = IpSeries.reconstruct(2L, "OTHER", name, description, coverImageUrl, status,
                LocalDateTime.now(), LocalDateTime.now());
        when(ipSeriesRepositoryPort.findByName(name)).thenReturn(Optional.of(existing));

        // 执行并验证异常
        BusinessException exception = assertThrows(BusinessException.class,
                () -> ipSeriesAppService.createIpSeries(code, name, description, coverImageUrl, status));
        assertEquals("IP 系列名称已存在: " + name, exception.getMessage());
    }

    /**
     * 根据 ID 查询 - 正常返回 DTO
     */
    @Test
    void getIpSeriesById_shouldReturn_whenExists() {
        // 准备数据
        Long id = 1L;
        IpSeries ipSeries = IpSeries.reconstruct(id, "MARVEL", "漫威宇宙", "描述",
                "https://example.com/marvel.jpg", IpSeriesStatus.ACTIVE,
                LocalDateTime.now(), LocalDateTime.now());
        when(ipSeriesRepositoryPort.findById(id)).thenReturn(Optional.of(ipSeries));

        // 执行（返回 DTO）
        IpSeriesResponse result = ipSeriesAppService.getIpSeriesById(id);

        // 验证返回的 DTO 字段
        assertNotNull(result);
        assertEquals(id, result.getId());
        assertEquals("MARVEL", result.getCode());
        assertEquals("漫威宇宙", result.getName());
        assertEquals("ACTIVE", result.getStatus());
        verify(ipSeriesRepositoryPort).findById(id);
    }

    /**
     * 根据 ID 查询 - 不存在抛 BusinessException
     */
    @Test
    void getIpSeriesById_shouldThrow_whenNotFound() {
        // 准备数据
        Long id = 999L;
        when(ipSeriesRepositoryPort.findById(id)).thenReturn(Optional.empty());

        // 执行并验证异常
        BusinessException exception = assertThrows(BusinessException.class,
                () -> ipSeriesAppService.getIpSeriesById(id));
        assertEquals("IP 系列不存在: " + id, exception.getMessage());
    }

    /**
     * 分页查询 - 正常分页查询
     */
    @Test
    void listIpSeries_shouldReturnPagedResult() {
        // 准备数据
        String name = "漫威";
        String status = "ACTIVE";
        int pageNumber = 0;
        int pageSize = 10;

        // 构建领域分页结果（port 返回 PageResult<IpSeries>）
        IpSeries series1 = IpSeries.reconstruct(1L, "MARVEL", "漫威宇宙", "描述1",
                "https://example.com/1.jpg", IpSeriesStatus.ACTIVE,
                LocalDateTime.now(), LocalDateTime.now());
        IpSeries series2 = IpSeries.reconstruct(2L, "DC", "漫威和DC", "描述2",
                "https://example.com/2.jpg", IpSeriesStatus.ACTIVE,
                LocalDateTime.now(), LocalDateTime.now());
        PageResult<IpSeries> mockPageResult = PageResult.<IpSeries>builder()
                .content(List.of(series1, series2))
                .totalElements(2)
                .pageNumber(0)
                .pageSize(10)
                .totalPages(1)
                .build();

        when(ipSeriesRepositoryPort.findByConditions(name, IpSeriesStatus.ACTIVE, pageNumber, pageSize))
                .thenReturn(mockPageResult);

        // 执行（AppService 返回 PageResult<IpSeriesResponse>）
        PageResult<IpSeriesResponse> result = ipSeriesAppService.listIpSeries(name, status, pageNumber, pageSize);

        // 验证
        assertNotNull(result);
        assertEquals(2, result.getContent().size());
        assertEquals("MARVEL", result.getContent().get(0).getCode());
        assertEquals("DC", result.getContent().get(1).getCode());
        assertEquals(2L, result.getTotalElements());
        assertEquals(1, result.getTotalPages());
        verify(ipSeriesRepositoryPort).findByConditions(name, IpSeriesStatus.ACTIVE, pageNumber, pageSize);
    }

    /**
     * 更新 IP 系列 - 正常更新
     */
    @Test
    void updateIpSeries_shouldSucceed_whenCodeAndNameNotChanged() {
        // 准备数据：更新 description 和 coverImageUrl，code 和 name 不变
        Long id = 1L;
        String code = "MARVEL";
        String name = "漫威宇宙";
        String newDescription = "新描述";
        String newCoverUrl = "https://example.com/new.jpg";
        IpSeriesStatus status = IpSeriesStatus.ACTIVE;

        IpSeries existing = IpSeries.reconstruct(id, code, name, "旧描述", "旧图片",
                IpSeriesStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now());
        when(ipSeriesRepositoryPort.findById(id)).thenReturn(Optional.of(existing));

        // code 和 name 没变，不需要查重，直接保存
        IpSeries saved = IpSeries.reconstruct(id, code, name, newDescription, newCoverUrl, status,
                LocalDateTime.now(), LocalDateTime.now());
        when(ipSeriesRepositoryPort.save(any(IpSeries.class))).thenReturn(saved);

        // 执行（返回 DTO）
        IpSeriesResponse result = ipSeriesAppService.updateIpSeries(id, code, name, newDescription, newCoverUrl, status);

        // 验证返回的 DTO
        assertNotNull(result);
        assertEquals(id, result.getId());
        assertEquals(newDescription, result.getDescription());
        verify(ipSeriesRepositoryPort).findById(id);
        verify(ipSeriesRepositoryPort).save(any(IpSeries.class));
    }

    /**
     * 更新 IP 系列 - code 重复（排除自身）抛异常
     */
    @Test
    void updateIpSeries_shouldThrow_whenCodeDuplicateExcludingSelf() {
        // 准备数据：修改 code 为一个已被其他记录占用的值
        Long id = 1L;
        String newCode = "DC";
        String name = "漫威宇宙";

        IpSeries existing = IpSeries.reconstruct(id, "MARVEL", name, "描述", "图片",
                IpSeriesStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now());
        when(ipSeriesRepositoryPort.findById(id)).thenReturn(Optional.of(existing));

        // 模拟 newCode 已被其他记录占用
        IpSeries conflict = IpSeries.reconstruct(2L, newCode, "DC宇宙", "描述", "图片",
                IpSeriesStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now());
        when(ipSeriesRepositoryPort.findByCode(newCode)).thenReturn(Optional.of(conflict));

        // 执行并验证异常
        BusinessException exception = assertThrows(BusinessException.class,
                () -> ipSeriesAppService.updateIpSeries(id, newCode, name, "描述", "图片", IpSeriesStatus.ACTIVE));
        assertEquals("IP 系列编码已存在: " + newCode, exception.getMessage());
    }

    /**
     * 更新 IP 系列 - name 重复（排除自身）抛异常
     */
    @Test
    void updateIpSeries_shouldThrow_whenNameDuplicateExcludingSelf() {
        // 准备数据：修改 name 为一个已被其他记录占用的值
        Long id = 1L;
        String code = "MARVEL";
        String newName = "DC宇宙";

        IpSeries existing = IpSeries.reconstruct(id, code, "漫威宇宙", "描述", "图片",
                IpSeriesStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now());
        when(ipSeriesRepositoryPort.findById(id)).thenReturn(Optional.of(existing));

        // code 没变，不会触发 code 查重；但 name 改了，需要查重
        // 模拟 newName 已被其他记录占用
        IpSeries conflict = IpSeries.reconstruct(2L, "DC", newName, "描述", "图片",
                IpSeriesStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now());
        when(ipSeriesRepositoryPort.findByName(newName)).thenReturn(Optional.of(conflict));

        // 执行并验证异常
        BusinessException exception = assertThrows(BusinessException.class,
                () -> ipSeriesAppService.updateIpSeries(id, code, newName, "描述", "图片", IpSeriesStatus.ACTIVE));
        assertEquals("IP 系列名称已存在: " + newName, exception.getMessage());
    }

    /**
     * 软删除 - status 变为 INACTIVE
     */
    @Test
    void deleteIpSeries_shouldDeactivate() {
        // 准备数据
        Long id = 1L;
        IpSeries existing = IpSeries.reconstruct(id, "MARVEL", "漫威宇宙", "描述",
                "https://example.com/marvel.jpg", IpSeriesStatus.ACTIVE,
                LocalDateTime.now(), LocalDateTime.now());
        when(ipSeriesRepositoryPort.findById(id)).thenReturn(Optional.of(existing));
        when(ipSeriesRepositoryPort.save(any(IpSeries.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // 执行
        ipSeriesAppService.deleteIpSeries(id);

        // 验证 save 被调用，且传入的对象 status 已变为 INACTIVE
        verify(ipSeriesRepositoryPort).save(argThat(series ->
                series.getStatus() == IpSeriesStatus.INACTIVE
        ));
    }
}
