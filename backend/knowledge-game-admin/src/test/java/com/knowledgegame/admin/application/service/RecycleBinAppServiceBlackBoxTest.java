package com.knowledgegame.admin.application.service;

import com.knowledgegame.admin.api.dto.response.BatchPurgeResult;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.domainenum.ResourceType;
import com.knowledgegame.core.domain.model.entity.RecycleBinItem;
import com.knowledgegame.core.domain.port.outbound.RecycleBinItemRepositoryPort;
import com.knowledgegame.core.domain.service.recyclebin.RecycleBinItemStrategyRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RecycleBinAppService purge/batchPurge 黑盒测试（REQ-102）
 * <p>
 * 仅根据公开接口签名和 PRD 行为描述编写，不依赖具体实现细节。
 * 测试边界条件与异常路径，与已有白盒测试不重叠。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RecycleBinAppService purge/batchPurge 黑盒测试")
class RecycleBinAppServiceBlackBoxTest {

    @Mock
    private RecycleBinItemRepositoryPort recycleBinRepository;

    @Mock
    private RecycleBinItemStrategyRegistry strategyRegistry;

    @Mock
    private RecycleBinAppService self;

    private RecycleBinAppService appService;

    @BeforeEach
    void setUp() {
        appService = new RecycleBinAppService(recycleBinRepository, strategyRegistry, self);
    }

    // ===== 辅助方法 =====

    private static RecycleBinItem createItem(Long id, ResourceType type) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return new RecycleBinItem(
                id, type, id, "blackbox-test-item",
                now, now, null, null, "admin", now, now.plusDays(30)
        );
    }

    // ===== 测试用例 =====

    @Test
    @DisplayName("a) 全部 id 都不存在 → successes 为空、failures 含所有 id、strategyRegistry 未调用")
    void batchPurge_allIdsNotFound_shouldReturnAllAsFailures() {
        // findAllById 返回空列表（999 和 888 都不存在）
        when(recycleBinRepository.findAllById(anyList())).thenReturn(Collections.emptyList());

        BatchPurgeResult result = appService.batchPurge(List.of(999L, 888L));

        // 断言无成功条目
        assertTrue(result.getSuccessIds().isEmpty(), "successIds 应为空");
        // 断言 failures 有 2 条
        assertEquals(2, result.getFailures().size(), "failures 应有 2 条");
        // 断言失败条目 ID 和消息
        BatchPurgeResult.Failure f1 = result.getFailures().get(0);
        assertEquals(999L, f1.getId());
        assertTrue(f1.getErrorMessage().contains("回收站记录不存在"),
                "消息应包含'回收站记录不存在'");
        BatchPurgeResult.Failure f2 = result.getFailures().get(1);
        assertEquals(888L, f2.getId());
        assertTrue(f2.getErrorMessage().contains("回收站记录不存在"),
                "消息应包含'回收站记录不存在'");

        // 断言 strategyRegistry.get() 从未被调用
        verify(strategyRegistry, never()).get(any());
    }

    @Test
    @DisplayName("b) purgeInNewTransaction 方法应有 @Transactional(propagation = REQUIRES_NEW) 注解")
    void purgeInNewTransaction_shouldHaveTransactionalAnnotation() throws NoSuchMethodException {
        Method method = RecycleBinAppService.class.getMethod(
                "purgeInNewTransaction", RecycleBinItem.class);

        Transactional annotation = method.getAnnotation(Transactional.class);
        assertNotNull(annotation, "purgeInNewTransaction 方法应有 @Transactional 注解");
        assertEquals(Propagation.REQUIRES_NEW, annotation.propagation(),
                "@Transactional propagation 应为 REQUIRES_NEW");
    }

    @Test
    @DisplayName("c) BatchPurgeResult 应不可变（无 setter），构造后字段可正确读取")
    void batchPurgeResult_shouldBeImmutable() {
        // 通过反射检查 BatchPurgeResult 没有 setter 方法
        Method[] methods = BatchPurgeResult.class.getMethods();
        for (Method m : methods) {
            if (m.getName().startsWith("set") && m.getParameterCount() == 1) {
                // Lombok @Getter 不会生成 setter，若出现 setter 则不可变假设被破坏
                throw new AssertionError("BatchPurgeResult 不应有 setter 方法: " + m.getName());
            }
        }

        // 验证构造后字段可正确读取
        List<Long> successIds = List.of(1L, 3L);
        List<BatchPurgeResult.Failure> failures = List.of(
                new BatchPurgeResult.Failure(2L, "测试错误消息")
        );
        BatchPurgeResult result = new BatchPurgeResult(successIds, failures);

        assertEquals(successIds, result.getSuccessIds(), "getSuccessIds 应返回构造时传入的值");
        assertEquals(failures, result.getFailures(), "getFailures 应返回构造时传入的值");
        assertEquals(1, failures.size());
        assertEquals(2L, failures.get(0).getId());
        assertEquals("测试错误消息", failures.get(0).getErrorMessage());
    }

    @Test
    @DisplayName("d) 部分 ID 不存在 + 部分存在 → successes 含存在的、failures 含不存在的")
    void batchPurge_mixedExistAndNotFound_shouldReturnBoth() {
        RecycleBinItem item1 = createItem(1L, ResourceType.IP_SERIES);
        RecycleBinItem item2 = createItem(2L, ResourceType.IP_SERIES);
        // findAllById 返回 [item1, item2]（999 静默跳过）
        when(recycleBinRepository.findAllById(anyList()))
                .thenReturn(List.of(item1, item2));
        doNothing().when(self).purgeInNewTransaction(any());

        BatchPurgeResult result = appService.batchPurge(List.of(1L, 999L, 2L));

        assertEquals(List.of(1L, 2L), result.getSuccessIds(),
                "successIds 应为 [1, 2]");
        assertEquals(1, result.getFailures().size(),
                "failures 应有 1 条");
        assertEquals(999L, result.getFailures().get(0).getId(),
                "失败的 ID 应为 999");
        assertTrue(result.getFailures().get(0).getErrorMessage().contains("回收站记录不存在"),
                "消息应包含'回收站记录不存在'");
    }

    @Test
    @DisplayName("e) batchPurge 空列表 → 返回空 BatchPurgeResult（不去重/不查 DB 单条）")
    void batchPurge_emptyList_shouldReturnEmptyResult() {
        when(recycleBinRepository.findAllById(anyList())).thenReturn(Collections.emptyList());

        BatchPurgeResult result = appService.batchPurge(Collections.emptyList());

        assertNotNull(result, "结果不应为 null");
        assertTrue(result.getSuccessIds().isEmpty(), "successIds 应为空");
        assertTrue(result.getFailures().isEmpty(), "failures 应为空");

        // self.purgeInNewTransaction 不应被调用（无条目可 purge）
        verify(self, never()).purgeInNewTransaction(any());
    }

    @Test
    @DisplayName("f) self.purgeInNewTransaction 抛 BusinessException → batchPurge 归入 failures（不抛到调用方）")
    void batchPurge_businessExceptionFromSelf_shouldCatchAndReturnFailure() {
        RecycleBinItem item = createItem(1L, ResourceType.KNOWLEDGE_CATEGORY);
        when(recycleBinRepository.findAllById(anyList()))
                .thenReturn(List.of(item));
        doThrow(new BusinessException(501, "资源类型 KNOWLEDGE_CATEGORY 暂未接入回收站"))
                .when(self).purgeInNewTransaction(any());

        // batchPurge 不应抛异常，总是返回 200 语义
        BatchPurgeResult result = appService.batchPurge(List.of(1L));

        assertTrue(result.getSuccessIds().isEmpty(), "successIds 应为空");
        assertEquals(1, result.getFailures().size(), "failures 应有 1 条");
        assertEquals(1L, result.getFailures().get(0).getId());
        assertTrue(result.getFailures().get(0).getErrorMessage()
                        .contains("暂未接入回收站"),
                "失败消息应包含原始 BusinessException 的 message");
    }

    @Test
    @DisplayName("g) self.purgeInNewTransaction 抛 RuntimeException → batchPurge 使用兜底消息（不泄露内部异常）")
    void batchPurge_runtimeExceptionFromSelf_shouldUseFallbackMessage() {
        RecycleBinItem item = createItem(1L, ResourceType.IP_SERIES);
        when(recycleBinRepository.findAllById(anyList()))
                .thenReturn(List.of(item));
        doThrow(new RuntimeException("DB connection timeout"))
                .when(self).purgeInNewTransaction(any());

        BatchPurgeResult result = appService.batchPurge(List.of(1L));

        assertTrue(result.getSuccessIds().isEmpty(), "successIds 应为空");
        assertEquals(1, result.getFailures().size(), "failures 应有 1 条");
        assertEquals(1L, result.getFailures().get(0).getId());
        assertEquals("永久删除失败，请联系管理员",
                result.getFailures().get(0).getErrorMessage(),
                "RuntimeException 的失败消息应为兜底消息，不应泄露原始异常信息");
    }
}
