package com.knowledgegame.core.domain.service;

import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.domainenum.KnowledgeCategoryStatus;
import com.knowledgegame.core.domain.model.entity.KnowledgeCategory;
import com.knowledgegame.core.domain.port.outbound.KnowledgeCategoryRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * KnowledgeCategoryDomainService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeCategoryDomainServiceTest {

    @Mock
    private KnowledgeCategoryRepositoryPort categoryRepositoryPort;

    /**
     * 创建 - 正常创建顶级分类
     */
    @Test
    void validateAndCreate_shouldSucceed_whenTopLevel() {
        when(categoryRepositoryPort.existsByNameAndParentId("编程", null)).thenReturn(false);

        KnowledgeCategoryDomainService service = new KnowledgeCategoryDomainService(categoryRepositoryPort);
        KnowledgeCategory result = service.validateAndCreate(
                "编程", null, null, null, null, null, 0);

        assertNotNull(result);
        assertEquals("编程", result.getName());
        assertEquals(KnowledgeCategoryStatus.ACTIVE, result.getStatus());
    }

    /**
     * 创建 - 父级 ACTIVE 时正常创建子分类
     */
    @Test
    void validateAndCreate_shouldSucceed_whenParentActive() {
        KnowledgeCategory parent = KnowledgeCategory.reconstruct(
                1L, null, "编程", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, null, null);
        when(categoryRepositoryPort.existsByNameAndParentId("Java", 1L)).thenReturn(false);
        when(categoryRepositoryPort.findById(1L)).thenReturn(Optional.of(parent));

        KnowledgeCategoryDomainService service = new KnowledgeCategoryDomainService(categoryRepositoryPort);
        KnowledgeCategory result = service.validateAndCreate(
                "Java", null, 1L, null, null, null, 0);

        assertNotNull(result);
        assertEquals("Java", result.getName());
        assertEquals(1L, result.getParentId());
    }

    /**
     * 创建 - 同名已存在抛异常
     */
    @Test
    void validateAndCreate_shouldThrow_whenNameDuplicate() {
        when(categoryRepositoryPort.existsByNameAndParentId("编程", null)).thenReturn(true);

        KnowledgeCategoryDomainService service = new KnowledgeCategoryDomainService(categoryRepositoryPort);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateAndCreate("编程", null, null, null, null, null, 0));
        assertEquals("同一父级下已存在同名分类: 编程", ex.getMessage());
    }

    /**
     * 创建 - 父级不存在抛异常
     */
    @Test
    void validateAndCreate_shouldThrow_whenParentNotFound() {
        when(categoryRepositoryPort.existsByNameAndParentId("Java", 99L)).thenReturn(false);
        when(categoryRepositoryPort.findById(99L)).thenReturn(Optional.empty());

        KnowledgeCategoryDomainService service = new KnowledgeCategoryDomainService(categoryRepositoryPort);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateAndCreate("Java", null, 99L, null, null, null, 0));
        assertEquals("父级分类不存在: 99", ex.getMessage());
    }

    /**
     * 创建 - 父级 INACTIVE 抛异常
     */
    @Test
    void validateAndCreate_shouldThrow_whenParentInactive() {
        KnowledgeCategory parent = KnowledgeCategory.reconstruct(
                1L, null, "编程", null, null, null, null, 0,
                KnowledgeCategoryStatus.INACTIVE, null, null);
        when(categoryRepositoryPort.existsByNameAndParentId("Java", 1L)).thenReturn(false);
        when(categoryRepositoryPort.findById(1L)).thenReturn(Optional.of(parent));

        KnowledgeCategoryDomainService service = new KnowledgeCategoryDomainService(categoryRepositoryPort);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateAndCreate("Java", null, 1L, null, null, null, 0));
        assertEquals("父级分类未启用: 1", ex.getMessage());
    }

    /**
     * 移动 - 正常移动
     */
    @Test
    void validateMove_shouldSucceed_whenTargetActive() {
        KnowledgeCategory target = KnowledgeCategory.reconstruct(
                5L, null, "目标", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, null, null);
        when(categoryRepositoryPort.findById(5L)).thenReturn(Optional.of(target));
        when(categoryRepositoryPort.findDescendantIds(1L)).thenReturn(Collections.emptyList());

        KnowledgeCategoryDomainService service = new KnowledgeCategoryDomainService(categoryRepositoryPort);
        service.validateMove(1L, 5L);
    }

    /**
     * 移动 - 移到顶级（newParentId=null）通过
     */
    @Test
    void validateMove_shouldSucceed_whenMovingToRoot() {
        KnowledgeCategoryDomainService service = new KnowledgeCategoryDomainService(categoryRepositoryPort);
        service.validateMove(1L, null);
    }

    /**
     * 移动 - 移到自己抛异常
     */
    @Test
    void validateMove_shouldThrow_whenMoveToSelf() {
        KnowledgeCategoryDomainService service = new KnowledgeCategoryDomainService(categoryRepositoryPort);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateMove(1L, 1L));
        assertEquals("不能将分类移动到自身下", ex.getMessage());
    }

    /**
     * 移动 - 移到后代下抛异常
     */
    @Test
    void validateMove_shouldThrow_whenMoveToDescendant() {
        KnowledgeCategory target = KnowledgeCategory.reconstruct(
                3L, 2L, "后代", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, null, null);
        when(categoryRepositoryPort.findById(3L)).thenReturn(Optional.of(target));
        when(categoryRepositoryPort.findDescendantIds(1L)).thenReturn(List.of(2L, 3L));

        KnowledgeCategoryDomainService service = new KnowledgeCategoryDomainService(categoryRepositoryPort);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateMove(1L, 3L));
        assertEquals("不能将分类移动到自己的后代分类下", ex.getMessage());
    }

    /**
     * 移动 - 目标 INACTIVE 抛异常
     */
    @Test
    void validateMove_shouldThrow_whenTargetInactive() {
        KnowledgeCategory target = KnowledgeCategory.reconstruct(
                5L, null, "目标", null, null, null, null, 0,
                KnowledgeCategoryStatus.INACTIVE, null, null);
        when(categoryRepositoryPort.findById(5L)).thenReturn(Optional.of(target));

        KnowledgeCategoryDomainService service = new KnowledgeCategoryDomainService(categoryRepositoryPort);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateMove(1L, 5L));
        assertEquals("目标分类未启用: 5", ex.getMessage());
    }
}
