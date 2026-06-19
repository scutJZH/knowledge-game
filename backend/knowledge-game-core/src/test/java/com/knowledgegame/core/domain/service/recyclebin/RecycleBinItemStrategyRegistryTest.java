package com.knowledgegame.core.domain.service.recyclebin;

import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.domainenum.ResourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RecycleBinItemStrategyRegistry 单元测试
 * <p>
 * 覆盖：空列表注入、正常注册、重复注册检测、缺失类型查询。
 */
class RecycleBinItemStrategyRegistryTest {

    @Test
    @DisplayName("注入空列表 → 注册中心为空，supportedTypes 返回空集")
    void emptyStrategyList_shouldCreateEmptyRegistry() {
        RecycleBinItemStrategyRegistry registry = new RecycleBinItemStrategyRegistry(Collections.emptyList());
        assertTrue(registry.supportedTypes().isEmpty());
    }

    @Test
    @DisplayName("注入 2 个不同类型策略 → 注册成功")
    void twoDifferentTypes_shouldRegisterSuccessfully() {
        RecycleBinItemStrategy<?> s1 = mockStrategy(ResourceType.IP_SERIES);
        RecycleBinItemStrategy<?> s2 = mockStrategy(ResourceType.QUESTION);

        RecycleBinItemStrategyRegistry registry = new RecycleBinItemStrategyRegistry(List.of(s1, s2));
        Set<ResourceType> types = registry.supportedTypes();
        assertEquals(2, types.size());
        assertTrue(types.contains(ResourceType.IP_SERIES));
        assertTrue(types.contains(ResourceType.QUESTION));

        assertNotNull(registry.get(ResourceType.IP_SERIES));
        assertNotNull(registry.get(ResourceType.QUESTION));
    }

    @Test
    @DisplayName("注入 2 个同类型策略 → 抛 IllegalStateException")
    void duplicateType_shouldThrowIllegalStateException() {
        RecycleBinItemStrategy<?> s1 = mockStrategy(ResourceType.IP_SERIES);
        RecycleBinItemStrategy<?> s2 = mockStrategy(ResourceType.IP_SERIES);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                new RecycleBinItemStrategyRegistry(List.of(s1, s2)));
        assertTrue(ex.getMessage().contains("IP_SERIES"));
    }

    @Test
    @DisplayName("get 未注册类型 → 抛 BusinessException(501)")
    void getUnregisteredType_shouldThrowBusinessException501() {
        RecycleBinItemStrategyRegistry registry = new RecycleBinItemStrategyRegistry(Collections.emptyList());
        BusinessException ex = assertThrows(BusinessException.class, () ->
                registry.get(ResourceType.KNOWLEDGE_ITEM));
        assertEquals(501, ex.getCode());
        assertTrue(ex.getMessage().contains("KNOWLEDGE_ITEM"));
    }

    @Test
    @DisplayName("supportedTypes 返回不可变 Set")
    void supportedTypes_shouldReturnUnmodifiableSet() {
        RecycleBinItemStrategy<?> s1 = mockStrategy(ResourceType.IP_SERIES);
        RecycleBinItemStrategyRegistry registry = new RecycleBinItemStrategyRegistry(List.of(s1));
        Set<ResourceType> types = registry.supportedTypes();
        assertThrows(UnsupportedOperationException.class, () -> types.add(ResourceType.QUESTION));
    }

    private static RecycleBinItemStrategy<?> mockStrategy(ResourceType type) {
        RecycleBinItemStrategy<?> s = mock(RecycleBinItemStrategy.class);
        when(s.getResourceType()).thenReturn(type);
        return s;
    }
}
