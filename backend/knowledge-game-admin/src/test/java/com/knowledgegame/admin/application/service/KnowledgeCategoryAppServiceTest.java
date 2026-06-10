package com.knowledgegame.admin.application.service;

import com.knowledgegame.admin.api.dto.response.KnowledgeCategoryResponse;
import com.knowledgegame.admin.api.dto.response.KnowledgeCategoryTreeResponse;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.domainenum.KnowledgeCategoryStatus;
import com.knowledgegame.core.domain.model.entity.KnowledgeCategory;
import com.knowledgegame.core.domain.model.vo.PageResult;
import com.knowledgegame.core.domain.port.outbound.KnowledgeCategoryRepositoryPort;
import com.knowledgegame.core.domain.service.KnowledgeCategoryDomainService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KnowledgeCategoryAppService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeCategoryAppServiceTest {

    @Mock
    private KnowledgeCategoryRepositoryPort categoryRepositoryPort;

    @Mock
    private KnowledgeCategoryDomainService categoryDomainService;

    @InjectMocks
    private KnowledgeCategoryAppService appService;

    private LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);

    /**
     * 创建 - 正常创建
     */
    @Test
    void create_shouldSucceed() {
        KnowledgeCategory newCategory = KnowledgeCategory.create(
                "编程", null, null, null, null, null, 0);
        when(categoryDomainService.validateAndCreate(
                "编程", null, null, null, null, null, 0)).thenReturn(newCategory);
        KnowledgeCategory saved = KnowledgeCategory.reconstruct(
                1L, null, "编程", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        when(categoryRepositoryPort.save(any())).thenReturn(saved);

        KnowledgeCategoryResponse result = appService.create(
                "编程", null, null, null, null, null, 0);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("编程", result.getName());
        assertEquals("ACTIVE", result.getStatus());
    }

    /**
     * 查询 - 正常返回
     */
    @Test
    void getById_shouldReturn_whenExists() {
        KnowledgeCategory category = KnowledgeCategory.reconstruct(
                1L, null, "编程", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        when(categoryRepositoryPort.findById(1L)).thenReturn(Optional.of(category));

        KnowledgeCategoryResponse result = appService.getById(1L);

        assertEquals(1L, result.getId());
        assertEquals("编程", result.getName());
    }

    /**
     * 查询 - 不存在抛异常
     */
    @Test
    void getById_shouldThrow_whenNotFound() {
        when(categoryRepositoryPort.findById(999L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> appService.getById(999L));
    }

    /**
     * 分页查询 - 正常返回
     */
    @Test
    void list_shouldReturnPagedResult() {
        KnowledgeCategory cat = KnowledgeCategory.reconstruct(
                1L, null, "编程", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        PageResult<KnowledgeCategory> mockPage = PageResult.<KnowledgeCategory>builder()
                .content(List.of(cat))
                .totalElements(1)
                .pageNumber(0)
                .pageSize(20)
                .totalPages(1)
                .build();
        when(categoryRepositoryPort.findByConditions(null, null, null, 0, 20)).thenReturn(mockPage);

        PageResult<KnowledgeCategoryResponse> result = appService.list(null, null, null, 0, 20);

        assertEquals(1, result.getContent().size());
        assertEquals(1L, result.getTotalElements());
    }

    /**
     * 分类树 - 正常构建
     */
    @Test
    void tree_shouldBuildTree() {
        KnowledgeCategory parent = KnowledgeCategory.reconstruct(
                1L, null, "编程", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        KnowledgeCategory child = KnowledgeCategory.reconstruct(
                2L, 1L, "Java", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        when(categoryRepositoryPort.findAll()).thenReturn(List.of(parent, child));

        List<KnowledgeCategoryTreeResponse> tree = appService.tree();

        assertEquals(1, tree.size());
        assertEquals("编程", tree.get(0).getName());
        assertEquals(1, tree.get(0).getChildren().size());
        assertEquals("Java", tree.get(0).getChildren().get(0).getName());
    }

    /**
     * 分类树 - 空列表返回空树
     */
    @Test
    void tree_shouldReturnEmpty_whenNoCategories() {
        when(categoryRepositoryPort.findAll()).thenReturn(Collections.emptyList());

        List<KnowledgeCategoryTreeResponse> tree = appService.tree();

        assertNotNull(tree);
        assertTrue(tree.isEmpty());
    }

    /**
     * 更新 - 名称不变时正常更新
     */
    @Test
    void update_shouldSucceed_whenNameNotChanged() {
        KnowledgeCategory existing = KnowledgeCategory.reconstruct(
                1L, null, "编程", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        when(categoryRepositoryPort.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        KnowledgeCategoryResponse result = appService.update(
                1L, "编程", "新描述", null, null, null, null);

        assertNotNull(result);
        verify(categoryRepositoryPort).save(any());
    }

    /**
     * 更新 - 同名已存在抛异常
     */
    @Test
    void update_shouldThrow_whenNameDuplicate() {
        KnowledgeCategory existing = KnowledgeCategory.reconstruct(
                1L, null, "编程", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        when(categoryRepositoryPort.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepositoryPort.existsByNameAndParentId("重复名", null)).thenReturn(true);

        assertThrows(BusinessException.class,
                () -> appService.update(1L, "重复名", null, null, null, null, null));
    }

    /**
     * 移动 - 正常移动
     */
    @Test
    void move_shouldSucceed() {
        KnowledgeCategory existing = KnowledgeCategory.reconstruct(
                2L, 1L, "Java", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        when(categoryRepositoryPort.findById(2L)).thenReturn(Optional.of(existing));
        when(categoryRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        KnowledgeCategoryResponse result = appService.move(2L, 5L);

        assertNotNull(result);
        verify(categoryRepositoryPort).save(argThat(cat -> cat.getParentId().equals(5L)));
    }

    /**
     * 移动 - 移到顶级（newParentId=null）
     */
    @Test
    void move_shouldSetParentIdToNull_whenMovingToRoot() {
        KnowledgeCategory existing = KnowledgeCategory.reconstruct(
                2L, 1L, "Java", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        when(categoryRepositoryPort.findById(2L)).thenReturn(Optional.of(existing));
        when(categoryRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        appService.move(2L, null);

        verify(categoryRepositoryPort).save(argThat(cat -> cat.getParentId() == null));
    }

    /**
     * 软删除 - status 变为 INACTIVE
     */
    @Test
    void delete_shouldDeactivate() {
        KnowledgeCategory existing = KnowledgeCategory.reconstruct(
                1L, null, "编程", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        when(categoryRepositoryPort.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        appService.delete(1L);

        verify(categoryRepositoryPort).save(argThat(cat ->
                cat.getStatus() == KnowledgeCategoryStatus.INACTIVE
        ));
    }

    /**
     * 软删除 - 不存在抛异常
     */
    @Test
    void delete_shouldThrow_whenNotFound() {
        when(categoryRepositoryPort.findById(999L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> appService.delete(999L));
    }
}
