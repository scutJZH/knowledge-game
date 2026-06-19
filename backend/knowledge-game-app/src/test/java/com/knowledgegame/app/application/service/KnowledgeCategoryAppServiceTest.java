package com.knowledgegame.app.application.service;

import com.knowledgegame.app.api.dto.response.KnowledgeCategoryTreeResponse;
import com.knowledgegame.core.domain.model.domainenum.KnowledgeCategoryStatus;
import com.knowledgegame.core.domain.model.entity.KnowledgeCategory;
import com.knowledgegame.core.domain.port.outbound.KnowledgeCategoryRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * KnowledgeCategoryAppService 单元测试（纯 Mockito，不启动 Spring 上下文）
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KnowledgeCategoryAppService")
class KnowledgeCategoryAppServiceTest {

    @Mock
    private KnowledgeCategoryRepositoryPort categoryRepositoryPort;

    @InjectMocks
    private KnowledgeCategoryAppService appService;

    private final LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);

    /**
     * 场景 a：单层多节点按 sortOrder 升序排列
     */
    @Test
    @DisplayName("tree() 单层多节点按 sortOrder 升序排列")
    void tree_shouldSortBySortOrder_whenSingleLevel() {
        KnowledgeCategory cat0 = KnowledgeCategory.reconstruct(
                1L, null, "编程", null, null, null, null, 2,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        KnowledgeCategory cat1 = KnowledgeCategory.reconstruct(
                2L, null, "数学", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        KnowledgeCategory cat2 = KnowledgeCategory.reconstruct(
                3L, null, "英语", null, null, null, null, 1,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        when(categoryRepositoryPort.findAll()).thenReturn(List.of(cat0, cat1, cat2));

        List<KnowledgeCategoryTreeResponse> tree = appService.tree();

        assertThat(tree).hasSize(3);
        assertThat(tree.get(0).getName()).isEqualTo("数学");
        assertThat(tree.get(0).getSortOrder()).isEqualTo(0);
        assertThat(tree.get(1).getName()).isEqualTo("英语");
        assertThat(tree.get(1).getSortOrder()).isEqualTo(1);
        assertThat(tree.get(2).getName()).isEqualTo("编程");
        assertThat(tree.get(2).getSortOrder()).isEqualTo(2);
    }

    /**
     * 场景 b：2 层父子嵌套结构
     */
    @Test
    @DisplayName("tree() 2 层父子嵌套结构正确")
    void tree_shouldBuildNestedStructure_whenTwoLevels() {
        KnowledgeCategory parent = KnowledgeCategory.reconstruct(
                1L, null, "编程", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        KnowledgeCategory child = KnowledgeCategory.reconstruct(
                2L, 1L, "Java", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        when(categoryRepositoryPort.findAll()).thenReturn(List.of(parent, child));

        List<KnowledgeCategoryTreeResponse> tree = appService.tree();

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getName()).isEqualTo("编程");
        assertThat(tree.get(0).getChildren()).hasSize(1);
        assertThat(tree.get(0).getChildren().get(0).getName()).isEqualTo("Java");
        assertThat(tree.get(0).getChildren().get(0).getParentId()).isEqualTo(1L);
    }

    /**
     * 场景 c：空列表返回空数组
     */
    @Test
    @DisplayName("tree() 空列表返回空数组")
    void tree_shouldReturnEmptyList_whenNoCategories() {
        when(categoryRepositoryPort.findAll()).thenReturn(Collections.emptyList());

        List<KnowledgeCategoryTreeResponse> tree = appService.tree();

        assertThat(tree).isNotNull();
        assertThat(tree).isEmpty();
    }

    /**
     * 场景 d：全 INACTIVE 返回空数组
     */
    @Test
    @DisplayName("tree() 全 INACTIVE 返回空数组")
    void tree_shouldReturnEmptyList_whenAllInactive() {
        KnowledgeCategory cat1 = KnowledgeCategory.reconstruct(
                1L, null, "编程", null, null, null, null, 0,
                KnowledgeCategoryStatus.INACTIVE, now, now);
        KnowledgeCategory cat2 = KnowledgeCategory.reconstruct(
                2L, null, "数学", null, null, null, null, 0,
                KnowledgeCategoryStatus.INACTIVE, now, now);
        when(categoryRepositoryPort.findAll()).thenReturn(List.of(cat1, cat2));

        List<KnowledgeCategoryTreeResponse> tree = appService.tree();

        assertThat(tree).isEmpty();
    }

    /**
     * 场景 e：混合 ACTIVE/INACTIVE 只返回 ACTIVE
     */
    @Test
    @DisplayName("tree() 混合 ACTIVE/INACTIVE 只返回 ACTIVE")
    void tree_shouldReturnOnlyActive_whenMixed() {
        KnowledgeCategory active = KnowledgeCategory.reconstruct(
                1L, null, "编程", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        KnowledgeCategory inactive = KnowledgeCategory.reconstruct(
                2L, null, "数学", null, null, null, null, 0,
                KnowledgeCategoryStatus.INACTIVE, now, now);
        when(categoryRepositoryPort.findAll()).thenReturn(List.of(active, inactive));

        List<KnowledgeCategoryTreeResponse> tree = appService.tree();

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getName()).isEqualTo("编程");
        assertThat(tree.get(0).getStatus()).isEqualTo("ACTIVE");
    }

    /**
     * 边界：子分类按 sortOrder 排序，递归到所有层级
     */
    @Test
    @DisplayName("tree() 子分类递归按 sortOrder 排序")
    void tree_shouldSortChildrenRecursively() {
        KnowledgeCategory parent = KnowledgeCategory.reconstruct(
                1L, null, "编程", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        KnowledgeCategory child2 = KnowledgeCategory.reconstruct(
                2L, 1L, "Python", null, null, null, null, 2,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        KnowledgeCategory child1 = KnowledgeCategory.reconstruct(
                3L, 1L, "Java", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        KnowledgeCategory grandchild = KnowledgeCategory.reconstruct(
                4L, 3L, "Spring", null, null, null, null, 1,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        KnowledgeCategory grandchild0 = KnowledgeCategory.reconstruct(
                5L, 3L, "JPA", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        when(categoryRepositoryPort.findAll()).thenReturn(
                List.of(parent, child2, child1, grandchild, grandchild0));

        List<KnowledgeCategoryTreeResponse> tree = appService.tree();

        assertThat(tree).hasSize(1);
        // 子级排序
        assertThat(tree.get(0).getChildren()).hasSize(2);
        assertThat(tree.get(0).getChildren().get(0).getName()).isEqualTo("Java");
        assertThat(tree.get(0).getChildren().get(1).getName()).isEqualTo("Python");
        // 孙级排序
        List<KnowledgeCategoryTreeResponse> grandchildren = tree.get(0).getChildren().get(0).getChildren();
        assertThat(grandchildren).hasSize(2);
        assertThat(grandchildren.get(0).getName()).isEqualTo("JPA");
        assertThat(grandchildren.get(1).getName()).isEqualTo("Spring");
    }

    /**
     * 边界：孤儿节点（父级不在列表中）挂载为根
     */
    @Test
    @DisplayName("tree() 孤儿节点挂载为根")
    void tree_shouldTreatOrphanAsRoot() {
        KnowledgeCategory orphan = KnowledgeCategory.reconstruct(
                2L, 999L, "孤儿", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        when(categoryRepositoryPort.findAll()).thenReturn(List.of(orphan));

        List<KnowledgeCategoryTreeResponse> tree = appService.tree();

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getName()).isEqualTo("孤儿");
        assertThat(tree.get(0).getParentId()).isEqualTo(999L);
    }
}
