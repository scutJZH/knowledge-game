package com.knowledgegame.core.domain.service;

import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.domainenum.KnowledgeCategoryStatus;
import com.knowledgegame.core.domain.model.entity.KnowledgeCategory;
import com.knowledgegame.core.domain.port.outbound.KnowledgeCategoryRepositoryPort;
import com.knowledgegame.core.domain.port.outbound.KnowledgeItemRepository;
import com.knowledgegame.core.domain.port.outbound.QuestionRepository;
import org.junit.jupiter.api.DisplayName;
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

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private KnowledgeItemRepository itemRepository;

    /**
     * 创建 - 正常创建顶级分类
     */
    @Test
    void validateAndCreate_shouldSucceed_whenTopLevel() {
        when(categoryRepositoryPort.existsByNameAndParentId("编程", null)).thenReturn(false);

        KnowledgeCategoryDomainService service = new KnowledgeCategoryDomainService(categoryRepositoryPort, questionRepository, itemRepository);
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

        KnowledgeCategoryDomainService service = new KnowledgeCategoryDomainService(categoryRepositoryPort, questionRepository, itemRepository);
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

        KnowledgeCategoryDomainService service = new KnowledgeCategoryDomainService(categoryRepositoryPort, questionRepository, itemRepository);
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

        KnowledgeCategoryDomainService service = new KnowledgeCategoryDomainService(categoryRepositoryPort, questionRepository, itemRepository);
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

        KnowledgeCategoryDomainService service = new KnowledgeCategoryDomainService(categoryRepositoryPort, questionRepository, itemRepository);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateAndCreate("Java", null, 1L, null, null, null, 0));
        assertEquals("父级分类未启用: 编程", ex.getMessage());
    }

    /**
     * 移动 - 正常移动
     */
    @Test
    void validateMove_shouldSucceed_whenTargetActive() {
        KnowledgeCategory target = KnowledgeCategory.reconstruct(
                5L, null, "目标", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, null, null);
        KnowledgeCategory current = KnowledgeCategory.reconstruct(
                1L, 2L, "Java", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, null, null);
        when(categoryRepositoryPort.findById(5L)).thenReturn(Optional.of(target));
        when(categoryRepositoryPort.findById(1L)).thenReturn(Optional.of(current));
        when(categoryRepositoryPort.findDescendantIds(1L)).thenReturn(Collections.emptyList());
        when(categoryRepositoryPort.existsByNameAndParentId("Java", 5L)).thenReturn(false);

        KnowledgeCategoryDomainService service = new KnowledgeCategoryDomainService(categoryRepositoryPort, questionRepository, itemRepository);
        service.validateMove(1L, 5L);
    }

    /**
     * 移动 - 移到顶级（newParentId=null）通过
     */
    @Test
    void validateMove_shouldSucceed_whenMovingToRoot() {
        KnowledgeCategory current = KnowledgeCategory.reconstruct(
                1L, 2L, "Java", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, null, null);
        when(categoryRepositoryPort.findById(1L)).thenReturn(Optional.of(current));
        when(categoryRepositoryPort.existsByNameAndParentId("Java", null)).thenReturn(false);

        KnowledgeCategoryDomainService service = new KnowledgeCategoryDomainService(categoryRepositoryPort, questionRepository, itemRepository);
        service.validateMove(1L, null);
    }

    /**
     * 移动 - 移到自己抛异常
     */
    @Test
    void validateMove_shouldThrow_whenMoveToSelf() {
        KnowledgeCategoryDomainService service = new KnowledgeCategoryDomainService(categoryRepositoryPort, questionRepository, itemRepository);
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

        KnowledgeCategoryDomainService service = new KnowledgeCategoryDomainService(categoryRepositoryPort, questionRepository, itemRepository);
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

        KnowledgeCategoryDomainService service = new KnowledgeCategoryDomainService(categoryRepositoryPort, questionRepository, itemRepository);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateMove(1L, 5L));
        assertEquals("目标分类未启用: 目标", ex.getMessage());
    }

    /**
     * 移动 - 目标父级下存在同名分类抛异常
     */
    @Test
    void validateMove_shouldThrow_whenDuplicateNameInTargetParent() {
        KnowledgeCategory target = KnowledgeCategory.reconstruct(
                5L, null, "MySQL", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, null, null);
        KnowledgeCategory current = KnowledgeCategory.reconstruct(
                1L, 2L, "Java", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, null, null);
        when(categoryRepositoryPort.findById(5L)).thenReturn(Optional.of(target));
        when(categoryRepositoryPort.findById(1L)).thenReturn(Optional.of(current));
        when(categoryRepositoryPort.existsByNameAndParentId("Java", 5L)).thenReturn(true);

        KnowledgeCategoryDomainService service = new KnowledgeCategoryDomainService(categoryRepositoryPort, questionRepository, itemRepository);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateMove(1L, 5L));
        assertEquals("目标父级下已存在同名分类: Java", ex.getMessage());
    }

    /**
     * 删除校验 - 无 ACTIVE 子分类且无 ACTIVE 题目时通过
     */
    @Test
    @DisplayName("validateDelete 无 ACTIVE 子分类且无 ACTIVE 题目时应通过")
    void validateDelete_shouldPass_whenNoActiveChildrenAndNoActiveQuestions() {
        when(categoryRepositoryPort.countActiveByParentId(1L)).thenReturn(0L);
        when(questionRepository.countActiveByCategoryId(1L)).thenReturn(0L);
        when(itemRepository.countActiveByCategoryId(1L)).thenReturn(0L);

        KnowledgeCategoryDomainService service = new KnowledgeCategoryDomainService(categoryRepositoryPort, questionRepository, itemRepository);
        service.validateDelete(1L);
        // 无异常即通过
    }

    /**
     * 删除校验 - 有 ACTIVE 子分类时抛异常（消息含数量）
     */
    @Test
    @DisplayName("validateDelete 有 ACTIVE 子分类时应抛异常")
    void validateDelete_shouldThrow_whenHasActiveChildren() {
        when(categoryRepositoryPort.countActiveByParentId(1L)).thenReturn(3L);

        KnowledgeCategoryDomainService service = new KnowledgeCategoryDomainService(categoryRepositoryPort, questionRepository, itemRepository);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateDelete(1L));
        assertEquals("知识点分类下存在 3 个 ACTIVE 子分类，无法删除", ex.getMessage());
    }

    /**
     * 删除校验 - 无 ACTIVE 子分类但有 ACTIVE 题目时抛异常
     */
    @Test
    @DisplayName("validateDelete 有 ACTIVE 题目关联时应抛异常")
    void validateDelete_shouldThrow_whenHasActiveQuestions() {
        when(categoryRepositoryPort.countActiveByParentId(1L)).thenReturn(0L);
        when(questionRepository.countActiveByCategoryId(1L)).thenReturn(5L);

        KnowledgeCategoryDomainService service = new KnowledgeCategoryDomainService(categoryRepositoryPort, questionRepository, itemRepository);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateDelete(1L));
        assertEquals("知识点分类关联 5 道 ACTIVE 题目，无法删除", ex.getMessage());
    }

    /**
     * 删除校验 - 无 ACTIVE 子分类和题目但有 ACTIVE 知识条目时抛异常
     */
    @Test
    @DisplayName("validateDelete 有 ACTIVE 知识条目关联时应抛异常")
    void validateDelete_shouldThrow_whenHasActiveItems() {
        when(categoryRepositoryPort.countActiveByParentId(1L)).thenReturn(0L);
        when(questionRepository.countActiveByCategoryId(1L)).thenReturn(0L);
        when(itemRepository.countActiveByCategoryId(1L)).thenReturn(5L);

        KnowledgeCategoryDomainService service = new KnowledgeCategoryDomainService(categoryRepositoryPort, questionRepository, itemRepository);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateDelete(1L));
        assertEquals("知识点分类关联 5 个 ACTIVE 知识条目，无法删除", ex.getMessage());
    }

    /**
     * 删除校验 - 同时存在 ACTIVE 子分类和题目时仅报告第一个失败（子分类）
     */
    @Test
    @DisplayName("validateDelete 同时存在 ACTIVE 子分类和题目时仅报告第一个失败（子分类）")
    void validateDelete_shouldThrowFirst_whenBothActive() {
        when(categoryRepositoryPort.countActiveByParentId(1L)).thenReturn(2L);

        KnowledgeCategoryDomainService service = new KnowledgeCategoryDomainService(categoryRepositoryPort, questionRepository, itemRepository);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateDelete(1L));
        assertEquals("知识点分类下存在 2 个 ACTIVE 子分类，无法删除", ex.getMessage());
    }

    // ============ validateActivate ============

    /**
     * 启用校验 - 顶级分类（无父级）应通过
     */
    @Test
    @DisplayName("validateActivate 顶级分类应通过")
    void validateActivate_shouldPass_whenTopLevel() {
        KnowledgeCategory category = KnowledgeCategory.create(
                "编程", null, null, null, null, null, 0);

        KnowledgeCategoryDomainService service = new KnowledgeCategoryDomainService(categoryRepositoryPort, questionRepository, itemRepository);
        service.validateActivate(category);
        // 无异常即通过
    }

    /**
     * 启用校验 - 父级 ACTIVE 应通过
     */
    @Test
    @DisplayName("validateActivate 父级 ACTIVE 应通过")
    void validateActivate_shouldPass_whenParentActive() {
        KnowledgeCategory parent = KnowledgeCategory.reconstruct(
                1L, null, "编程", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, null, null);
        KnowledgeCategory category = KnowledgeCategory.reconstruct(
                2L, 1L, "Java", null, null, null, null, 0,
                KnowledgeCategoryStatus.INACTIVE, null, null);
        when(categoryRepositoryPort.findById(1L)).thenReturn(Optional.of(parent));

        KnowledgeCategoryDomainService service = new KnowledgeCategoryDomainService(categoryRepositoryPort, questionRepository, itemRepository);
        service.validateActivate(category);
    }

    /**
     * 启用校验 - 父级 INACTIVE 应抛异常
     */
    @Test
    @DisplayName("validateActivate 父级 INACTIVE 应抛异常")
    void validateActivate_shouldThrow_whenParentInactive() {
        KnowledgeCategory parent = KnowledgeCategory.reconstruct(
                1L, null, "编程", null, null, null, null, 0,
                KnowledgeCategoryStatus.INACTIVE, null, null);
        KnowledgeCategory category = KnowledgeCategory.reconstruct(
                2L, 1L, "Java", null, null, null, null, 0,
                KnowledgeCategoryStatus.INACTIVE, null, null);
        when(categoryRepositoryPort.findById(1L)).thenReturn(Optional.of(parent));

        KnowledgeCategoryDomainService service = new KnowledgeCategoryDomainService(categoryRepositoryPort, questionRepository, itemRepository);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateActivate(category));
        assertEquals("父级分类未启用，无法启用该分类", ex.getMessage());
    }

    /**
     * 启用校验 - 父级不存在应抛异常
     */
    @Test
    @DisplayName("validateActivate 父级不存在应抛异常")
    void validateActivate_shouldThrow_whenParentNotFound() {
        KnowledgeCategory category = KnowledgeCategory.reconstruct(
                2L, 99L, "Java", null, null, null, null, 0,
                KnowledgeCategoryStatus.INACTIVE, null, null);
        when(categoryRepositoryPort.findById(99L)).thenReturn(Optional.empty());

        KnowledgeCategoryDomainService service = new KnowledgeCategoryDomainService(categoryRepositoryPort, questionRepository, itemRepository);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateActivate(category));
        assertEquals("父级分类不存在: 99", ex.getMessage());
    }

    /**
     * 创建 - sortOrder 为 null 时自动计算（顶级分类，无同级）
     */
    @Test
    void validateAndCreate_shouldAutoCalculateSortOrder_whenNullAndRootNoSiblings() {
        when(categoryRepositoryPort.existsByNameAndParentId("编程", null)).thenReturn(false);
        when(categoryRepositoryPort.findMaxSortOrderForRoot()).thenReturn(null);

        KnowledgeCategoryDomainService service = new KnowledgeCategoryDomainService(categoryRepositoryPort, questionRepository, itemRepository);
        KnowledgeCategory result = service.validateAndCreate(
                "编程", null, null, null, null, null, null);

        assertNotNull(result);
        assertEquals(0, result.getSortOrder());
    }

    /**
     * 创建 - sortOrder 为 null 时自动计算（顶级分类，有同级）
     */
    @Test
    void validateAndCreate_shouldAutoCalculateSortOrder_whenNullAndRootHasSiblings() {
        when(categoryRepositoryPort.existsByNameAndParentId("数学", null)).thenReturn(false);
        when(categoryRepositoryPort.findMaxSortOrderForRoot()).thenReturn(3);

        KnowledgeCategoryDomainService service = new KnowledgeCategoryDomainService(categoryRepositoryPort, questionRepository, itemRepository);
        KnowledgeCategory result = service.validateAndCreate(
                "数学", null, null, null, null, null, null);

        assertNotNull(result);
        assertEquals(4, result.getSortOrder());
    }

    /**
     * 创建 - sortOrder 为 null 时自动计算（子分类，无同级）
     */
    @Test
    void validateAndCreate_shouldAutoCalculateSortOrder_whenNullAndChildNoSiblings() {
        KnowledgeCategory parent = KnowledgeCategory.reconstruct(
                1L, null, "编程", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, null, null);
        when(categoryRepositoryPort.existsByNameAndParentId("Java", 1L)).thenReturn(false);
        when(categoryRepositoryPort.findById(1L)).thenReturn(Optional.of(parent));
        when(categoryRepositoryPort.findMaxSortOrderByParentId(1L)).thenReturn(null);

        KnowledgeCategoryDomainService service = new KnowledgeCategoryDomainService(categoryRepositoryPort, questionRepository, itemRepository);
        KnowledgeCategory result = service.validateAndCreate(
                "Java", null, 1L, null, null, null, null);

        assertNotNull(result);
        assertEquals(0, result.getSortOrder());
    }

    /**
     * 创建 - sortOrder 为 null 时自动计算（子分类，有同级）
     */
    @Test
    void validateAndCreate_shouldAutoCalculateSortOrder_whenNullAndChildHasSiblings() {
        KnowledgeCategory parent = KnowledgeCategory.reconstruct(
                1L, null, "编程", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, null, null);
        when(categoryRepositoryPort.existsByNameAndParentId("Python", 1L)).thenReturn(false);
        when(categoryRepositoryPort.findById(1L)).thenReturn(Optional.of(parent));
        when(categoryRepositoryPort.findMaxSortOrderByParentId(1L)).thenReturn(5);

        KnowledgeCategoryDomainService service = new KnowledgeCategoryDomainService(categoryRepositoryPort, questionRepository, itemRepository);
        KnowledgeCategory result = service.validateAndCreate(
                "Python", null, 1L, null, null, null, null);

        assertNotNull(result);
        assertEquals(6, result.getSortOrder());
    }
}
