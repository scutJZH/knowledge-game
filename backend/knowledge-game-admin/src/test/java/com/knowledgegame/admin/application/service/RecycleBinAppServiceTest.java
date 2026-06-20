package com.knowledgegame.admin.application.service;

import com.knowledgegame.admin.api.dto.request.RecycleBinListRequest;
import com.knowledgegame.admin.api.dto.response.BatchPurgeResult;
import com.knowledgegame.admin.api.dto.response.BatchRestoreResult;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.domainenum.ResourceType;
import com.knowledgegame.core.domain.model.entity.RecycleBinItem;
import com.knowledgegame.core.domain.model.vo.PageResult;
import com.knowledgegame.core.domain.port.outbound.RecycleBinItemRepositoryPort;
import com.knowledgegame.core.domain.service.recyclebin.RecycleBinItemStrategyRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
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

    private RecycleBinAppService appService;

    @BeforeEach
    void setUp() throws Exception {
        // 手动构造 + spy 以支持 self 注入（self.restoreInNewTransaction 需经过代理）
        RecycleBinAppService real = new RecycleBinAppService(recycleBinRepository, strategyRegistry, null);
        RecycleBinAppService spyService = spy(real);
        java.lang.reflect.Field selfField = RecycleBinAppService.class.getDeclaredField("self");
        selfField.setAccessible(true);
        selfField.set(spyService, spyService);
        appService = spyService;
    }

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

    // ===== restore (REQ-103) =====

    @Test
    @DisplayName("restore 成功 → verify self.restoreInNewTransaction(item) 调用 1 次")
    void restore_success_shouldDelegateToSelf() {
        RecycleBinItem item = createItem(42L, ResourceType.IP_SERIES);
        when(recycleBinRepository.findById(42L)).thenReturn(Optional.of(item));
        doNothing().when(appService).restoreInNewTransaction(item);

        appService.restore(42L);

        verify(appService).restoreInNewTransaction(item);
    }

    @Test
    @DisplayName("restore 记录不存在 → BusinessException(404)")
    void restore_notFound_shouldThrow404() {
        when(recycleBinRepository.findById(99L)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> appService.restore(99L));
        assertEquals(404, ex.getCode());
        assertTrue(ex.getMessage().contains("回收站记录不存在"));
    }

    @Test
    @DisplayName("restore 无 strategy → BusinessException(501) 向上抛")
    void restore_noStrategy_shouldThrow501() {
        RecycleBinItem item = createItem(42L, ResourceType.KNOWLEDGE_CATEGORY);
        when(recycleBinRepository.findById(42L)).thenReturn(Optional.of(item));
        when(strategyRegistry.get(ResourceType.KNOWLEDGE_CATEGORY))
                .thenThrow(new BusinessException(501, "资源类型 KNOWLEDGE_CATEGORY 暂未接入回收站"));

        BusinessException ex = assertThrows(BusinessException.class, () -> appService.restore(42L));
        assertEquals(501, ex.getCode());
        assertTrue(ex.getMessage().contains("暂未接入回收站"));
    }

    // ===== batchRestore (REQ-103) =====

    @Test
    @DisplayName("batchRestore 全成功 → verify self.restoreInNewTransaction 调用 N 次")
    void batchRestore_allSuccess_shouldCallRestoreInNewTransactionNTimes() {
        RecycleBinItem item1 = createItem(1L, ResourceType.IP_SERIES);
        RecycleBinItem item2 = createItem(2L, ResourceType.KNOWLEDGE_CATEGORY);
        when(recycleBinRepository.findAllById(anyList()))
                .thenReturn(List.of(item1, item2));
        doNothing().when(appService).restoreInNewTransaction(any());

        BatchRestoreResult result = appService.batchRestore(List.of(1L, 2L));

        assertEquals(List.of(1L, 2L), result.getSuccessIds());
        assertEquals(0, result.getFailures().size());
        verify(appService).restoreInNewTransaction(item1);
        verify(appService).restoreInNewTransaction(item2);
    }

    @Test
    @DisplayName("batchRestore 部分预校验失败（findAllById 返回子集）")
    void batchRestore_partialNotFound_shouldReturnFailures() {
        RecycleBinItem item1 = createItem(1L, ResourceType.IP_SERIES);
        when(recycleBinRepository.findAllById(anyList()))
                .thenReturn(List.of(item1));
        doNothing().when(appService).restoreInNewTransaction(any());

        BatchRestoreResult result = appService.batchRestore(List.of(1L, 2L));

        assertEquals(List.of(1L), result.getSuccessIds());
        assertEquals(1, result.getFailures().size());
        assertEquals(2L, result.getFailures().get(0).getId());
        assertTrue(result.getFailures().get(0).getErrorMessage().contains("回收站记录不存在: 2"));
    }

    @Test
    @DisplayName("batchRestore strategy 抛 BusinessException → failures 含 message")
    void batchRestore_strategyThrowsBusinessException_shouldContainMessage() {
        RecycleBinItem item1 = createItem(1L, ResourceType.IP_SERIES);
        RecycleBinItem item2 = createItem(2L, ResourceType.KNOWLEDGE_CATEGORY);
        when(recycleBinRepository.findAllById(anyList()))
                .thenReturn(List.of(item1, item2));
        // item1 的 restoreInNewTransaction 成功（do nothing），item2 的抛 BusinessException
        doNothing().when(appService).restoreInNewTransaction(item1);
        doThrow(new BusinessException(501, "资源类型 KNOWLEDGE_CATEGORY 暂未接入回收站"))
                .when(appService).restoreInNewTransaction(item2);

        BatchRestoreResult result = appService.batchRestore(List.of(1L, 2L));

        assertEquals(List.of(1L), result.getSuccessIds());
        assertEquals(1, result.getFailures().size());
        assertEquals(2L, result.getFailures().get(0).getId());
        assertTrue(result.getFailures().get(0).getErrorMessage().contains("暂未接入回收站"));
    }

    @Test
    @DisplayName("batchRestore 未知异常 → failures 含兜底消息")
    void batchRestore_unknownException_shouldContainFallbackMessage() {
        RecycleBinItem item1 = createItem(1L, ResourceType.IP_SERIES);
        when(recycleBinRepository.findAllById(anyList()))
                .thenReturn(List.of(item1));
        doThrow(new RuntimeException("数据库连接超时"))
                .when(appService).restoreInNewTransaction(item1);

        BatchRestoreResult result = appService.batchRestore(List.of(1L));

        assertEquals(0, result.getSuccessIds().size());
        assertEquals(1, result.getFailures().size());
        assertEquals(1L, result.getFailures().get(0).getId());
        assertEquals("恢复失败，请联系管理员", result.getFailures().get(0).getErrorMessage());
    }

    @Test
    @DisplayName("batchRestore 不重复查 DB（verify findById 0 次）")
    void batchRestore_shouldNotCallFindById() {
        RecycleBinItem item1 = createItem(1L, ResourceType.IP_SERIES);
        when(recycleBinRepository.findAllById(anyList()))
                .thenReturn(List.of(item1));
        doNothing().when(appService).restoreInNewTransaction(any());

        appService.batchRestore(List.of(1L));

        verify(recycleBinRepository, never()).findById(anyLong());
    }

    // ===== purge (REQ-102) =====

    @Test
    @DisplayName("purge 成功 → verify self.purgeInNewTransaction(item) 调用 1 次")
    void purge_success_shouldDelegateToSelf() {
        RecycleBinItem item = createItem(42L, ResourceType.IP_SERIES);
        when(recycleBinRepository.findById(42L)).thenReturn(Optional.of(item));
        doNothing().when(appService).purgeInNewTransaction(item);

        appService.purge(42L);

        verify(appService).purgeInNewTransaction(item);
    }

    @Test
    @DisplayName("purge 记录不存在 → BusinessException(404)")
    void purge_notFound_shouldThrow404() {
        when(recycleBinRepository.findById(99L)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> appService.purge(99L));
        assertEquals(404, ex.getCode());
        assertTrue(ex.getMessage().contains("回收站记录不存在"));
    }

    @Test
    @DisplayName("purge 无 strategy → BusinessException(501) 向上传播")
    void purge_noStrategy_shouldThrow501() {
        RecycleBinItem item = createItem(42L, ResourceType.KNOWLEDGE_CATEGORY);
        when(recycleBinRepository.findById(42L)).thenReturn(Optional.of(item));
        doThrow(new BusinessException(501, "资源类型 KNOWLEDGE_CATEGORY 暂未接入回收站"))
                .when(appService).purgeInNewTransaction(item);

        BusinessException ex = assertThrows(BusinessException.class, () -> appService.purge(42L));
        assertEquals(501, ex.getCode());
        assertTrue(ex.getMessage().contains("暂未接入回收站"));
    }

    // ===== batchPurge (REQ-102) =====

    @Test
    @DisplayName("batchPurge 全成功 → verify self.purgeInNewTransaction 调用 N 次")
    void batchPurge_allSuccess_shouldCallPurgeInNewTransactionNTimes() {
        RecycleBinItem item1 = createItem(1L, ResourceType.IP_SERIES);
        RecycleBinItem item2 = createItem(2L, ResourceType.KNOWLEDGE_CATEGORY);
        when(recycleBinRepository.findAllById(anyList()))
                .thenReturn(List.of(item1, item2));
        doNothing().when(appService).purgeInNewTransaction(any());

        BatchPurgeResult result = appService.batchPurge(List.of(1L, 2L));

        assertEquals(List.of(1L, 2L), result.getSuccessIds());
        assertEquals(0, result.getFailures().size());
        verify(appService).purgeInNewTransaction(item1);
        verify(appService).purgeInNewTransaction(item2);
    }

    @Test
    @DisplayName("batchPurge 部分预校验失败（findAllById 返回子集）")
    void batchPurge_partialNotFound_shouldReturnFailures() {
        RecycleBinItem item1 = createItem(1L, ResourceType.IP_SERIES);
        when(recycleBinRepository.findAllById(anyList()))
                .thenReturn(List.of(item1));
        doNothing().when(appService).purgeInNewTransaction(any());

        BatchPurgeResult result = appService.batchPurge(List.of(1L, 2L));

        assertEquals(List.of(1L), result.getSuccessIds());
        assertEquals(1, result.getFailures().size());
        assertEquals(2L, result.getFailures().get(0).getId());
        assertTrue(result.getFailures().get(0).getErrorMessage().contains("回收站记录不存在: 2"));
    }

    @Test
    @DisplayName("batchPurge strategy 抛 BusinessException → failures 含 message")
    void batchPurge_strategyThrowsBusinessException_shouldContainMessage() {
        RecycleBinItem item1 = createItem(1L, ResourceType.IP_SERIES);
        RecycleBinItem item2 = createItem(2L, ResourceType.KNOWLEDGE_CATEGORY);
        when(recycleBinRepository.findAllById(anyList()))
                .thenReturn(List.of(item1, item2));
        doNothing().when(appService).purgeInNewTransaction(item1);
        doThrow(new BusinessException(501, "文件服务不可达"))
                .when(appService).purgeInNewTransaction(item2);

        BatchPurgeResult result = appService.batchPurge(List.of(1L, 2L));

        assertEquals(List.of(1L), result.getSuccessIds());
        assertEquals(1, result.getFailures().size());
        assertEquals(2L, result.getFailures().get(0).getId());
        assertTrue(result.getFailures().get(0).getErrorMessage().contains("文件服务不可达"));
    }

    @Test
    @DisplayName("batchPurge 未知异常 → failures 含兜底消息")
    void batchPurge_unknownException_shouldContainFallbackMessage() {
        RecycleBinItem item1 = createItem(1L, ResourceType.IP_SERIES);
        when(recycleBinRepository.findAllById(anyList()))
                .thenReturn(List.of(item1));
        doThrow(new RuntimeException("数据库连接超时"))
                .when(appService).purgeInNewTransaction(item1);

        BatchPurgeResult result = appService.batchPurge(List.of(1L));

        assertEquals(0, result.getSuccessIds().size());
        assertEquals(1, result.getFailures().size());
        assertEquals(1L, result.getFailures().get(0).getId());
        assertEquals("永久删除失败，请联系管理员", result.getFailures().get(0).getErrorMessage());
    }

    @Test
    @DisplayName("batchPurge 不重复查 DB（verify findById 0 次）")
    void batchPurge_shouldNotCallFindById() {
        RecycleBinItem item1 = createItem(1L, ResourceType.IP_SERIES);
        when(recycleBinRepository.findAllById(anyList()))
                .thenReturn(List.of(item1));
        doNothing().when(appService).purgeInNewTransaction(any());

        appService.batchPurge(List.of(1L));

        verify(recycleBinRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("batchPurge 重复 id 自动去重 → successIds 不含重复")
    void batchPurge_duplicateIds_shouldDeduplicate() {
        RecycleBinItem item42 = createItem(42L, ResourceType.IP_SERIES);
        RecycleBinItem item43 = createItem(43L, ResourceType.IP_SERIES);
        when(recycleBinRepository.findAllById(anyList()))
                .thenReturn(List.of(item42, item43));
        doNothing().when(appService).purgeInNewTransaction(any());

        BatchPurgeResult result = appService.batchPurge(List.of(42L, 42L, 43L));

        assertEquals(List.of(42L, 43L), result.getSuccessIds());
        assertEquals(0, result.getFailures().size());
        verify(appService).purgeInNewTransaction(item42);
        verify(appService).purgeInNewTransaction(item43);
    }

    private static RecycleBinItem createItem(Long id, ResourceType type) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return new RecycleBinItem(
                id, type, id, "test-item",
                now, now, null, null, "admin", now, now.plusDays(30)
        );
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
