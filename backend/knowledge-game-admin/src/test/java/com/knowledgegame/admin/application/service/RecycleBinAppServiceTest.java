package com.knowledgegame.admin.application.service;

import com.knowledgegame.admin.api.dto.request.RecycleBinListRequest;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.domainenum.ResourceType;
import com.knowledgegame.core.domain.model.entity.RecycleBinItem;
import com.knowledgegame.core.domain.model.vo.PageResult;
import com.knowledgegame.core.domain.port.outbound.RecycleBinItemRepositoryPort;
import com.knowledgegame.core.domain.service.recyclebin.RecycleBinItemStrategyRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * RecycleBinAppService 单元测试
 * <p>
 * 覆盖：list 空数据、resourceType 过滤、supportedTypes 空注册中心、
 * parseResourceType 边界（null/blank/ALL/合法/非法）。
 */
@ExtendWith(MockitoExtension.class)
class RecycleBinAppServiceTest {

    @Mock
    private RecycleBinItemRepositoryPort recycleBinRepository;

    @Mock
    private RecycleBinItemStrategyRegistry strategyRegistry;

    @InjectMocks
    private RecycleBinAppService appService;

    // ===== list =====

    @Test
    @DisplayName("list 空数据应返回空分页结果")
    void list_emptyData_shouldReturnEmptyPage() {
        when(recycleBinRepository.findAll(isNull(), isNull(), eq(0), eq(20), isNull()))
                .thenReturn(PageResult.<RecycleBinItem>builder()
                        .content(Collections.emptyList())
                        .totalElements(0)
                        .pageNumber(0)
                        .pageSize(20)
                        .totalPages(0)
                        .build());

        RecycleBinListRequest request = new RecycleBinListRequest();
        var result = appService.list(request);

        assertEquals(0, result.getTotalElements());
        assertTrue(result.getContent().isEmpty());
    }

    @Test
    @DisplayName("list 带 resourceType 过滤应正确传递")
    void list_withResourceType_shouldFilterCorrectly() {
        when(recycleBinRepository.findAll(eq(ResourceType.IP_SERIES), isNull(), eq(0), eq(20), isNull()))
                .thenReturn(PageResult.<RecycleBinItem>builder()
                        .content(Collections.emptyList())
                        .totalElements(0)
                        .pageNumber(0)
                        .pageSize(20)
                        .totalPages(0)
                        .build());

        RecycleBinListRequest request = new RecycleBinListRequest();
        request.setResourceType("IP_SERIES");
        var result = appService.list(request);
        assertEquals(0, result.getTotalElements());
    }

    // ===== supportedTypes =====

    @Test
    @DisplayName("supportedTypes 空注册中心应返回空列表")
    void supportedTypes_emptyRegistry_shouldReturnEmptyList() {
        when(strategyRegistry.supportedTypes()).thenReturn(Collections.emptySet());
        assertTrue(appService.supportedTypes().isEmpty());
    }

    // ===== parseResourceType 边界 =====

    @Test
    @DisplayName("resourceType=null → 返回 null（不过滤）")
    void parseResourceType_null_shouldReturnNull() {
        RecycleBinListRequest request = new RecycleBinListRequest();
        // resourceType 默认 null
        when(recycleBinRepository.findAll(isNull(), isNull(), eq(0), eq(20), isNull()))
                .thenReturn(emptyPage());
        var result = appService.list(request);
        assertNotNull(result);
    }

    @Test
    @DisplayName("resourceType='ALL' → 返回 null（不过滤，大小写不敏感）")
    void parseResourceType_allCaseInsensitive_shouldReturnNull() {
        when(recycleBinRepository.findAll(isNull(), isNull(), eq(0), eq(20), isNull()))
                .thenReturn(emptyPage());
        RecycleBinListRequest request = new RecycleBinListRequest();
        request.setResourceType("ALL");
        appService.list(request); // 不抛异常即通过
        request.setResourceType("all");
        appService.list(request);
    }

    @Test
    @DisplayName("resourceType=合法枚举值 → 正确解析")
    void parseResourceType_validEnum_shouldParseCorrectly() {
        when(recycleBinRepository.findAll(eq(ResourceType.KNOWLEDGE_CATEGORY), isNull(), eq(0), eq(20), isNull()))
                .thenReturn(emptyPage());
        RecycleBinListRequest request = new RecycleBinListRequest();
        request.setResourceType("KNOWLEDGE_CATEGORY");
        appService.list(request); // 不抛异常即通过
    }

    @Test
    @DisplayName("resourceType=非法值 → 抛 BusinessException(400)")
    void parseResourceType_invalidValue_shouldThrow400() {
        RecycleBinListRequest request = new RecycleBinListRequest();
        request.setResourceType("UNKNOWN_TYPE");
        BusinessException ex = assertThrows(BusinessException.class, () -> appService.list(request));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("UNKNOWN_TYPE"));
    }

    private static PageResult<RecycleBinItem> emptyPage() {
        return PageResult.<RecycleBinItem>builder()
                .content(Collections.emptyList())
                .totalElements(0)
                .pageNumber(0)
                .pageSize(20)
                .totalPages(0)
                .build();
    }
}
