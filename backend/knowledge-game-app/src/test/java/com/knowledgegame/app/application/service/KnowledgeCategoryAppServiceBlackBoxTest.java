package com.knowledgegame.app.application.service;

import com.knowledgegame.app.api.dto.response.KnowledgeCategoryTreeResponse;
import com.knowledgegame.core.domain.model.domainenum.KnowledgeCategoryStatus;
import com.knowledgegame.core.domain.model.entity.KnowledgeCategory;
import com.knowledgegame.core.domain.model.vo.FileRef;
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
 * KnowledgeCategoryAppService 黑盒单元测试（纯 Mockito，不启动 Spring 上下文）
 *
 * <p>基于 PRD/API 签名编写，不依赖实现代码。验证 tree() 的边界条件和边缘场景。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KnowledgeCategoryAppService Black-Box")
class KnowledgeCategoryAppServiceBlackBoxTest {

    @Mock
    private KnowledgeCategoryRepositoryPort categoryRepositoryPort;

    @InjectMocks
    private KnowledgeCategoryAppService appService;

    private final LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);

    // ============================================================
    // 深度嵌套
    // ============================================================

    /**
     * 4 层深度嵌套：root → child → grandchild → great-grandchild
     */
    @Test
    @DisplayName("tree() 4 层深度嵌套结构正确")
    void tree_shouldBuildFourLevelDeepTree() {
        KnowledgeCategory root = KnowledgeCategory.reconstruct(
                1L, null, "分类1", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        KnowledgeCategory l1 = KnowledgeCategory.reconstruct(
                2L, 1L, "分类1-1", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        KnowledgeCategory l2 = KnowledgeCategory.reconstruct(
                3L, 2L, "分类1-1-1", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        KnowledgeCategory l3 = KnowledgeCategory.reconstruct(
                4L, 3L, "分类1-1-1-1", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        when(categoryRepositoryPort.findAll()).thenReturn(List.of(root, l1, l2, l3));

        List<KnowledgeCategoryTreeResponse> tree = appService.tree();

        assertThat(tree).hasSize(1);
        KnowledgeCategoryTreeResponse l1Node = tree.get(0).getChildren().get(0);
        assertThat(l1Node.getName()).isEqualTo("分类1-1");
        KnowledgeCategoryTreeResponse l2Node = l1Node.getChildren().get(0);
        assertThat(l2Node.getName()).isEqualTo("分类1-1-1");
        KnowledgeCategoryTreeResponse l3Node = l2Node.getChildren().get(0);
        assertThat(l3Node.getName()).isEqualTo("分类1-1-1-1");
        assertThat(l3Node.getChildren()).isEmpty();
    }

    /**
     * 3 层嵌套，每层多个节点，同时验证 sortOrder 递归排序
     */
    @Test
    @DisplayName("tree() 3 层多节点递归排序正确")
    void tree_shouldSortRecursively_whenThreeLevelsWithMultipleSiblings() {
        KnowledgeCategory root1 = KnowledgeCategory.reconstruct(
                1L, null, "A", null, null, null, null, 2,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        KnowledgeCategory root2 = KnowledgeCategory.reconstruct(
                2L, null, "B", null, null, null, null, 1,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        KnowledgeCategory a1 = KnowledgeCategory.reconstruct(
                3L, 1L, "A-2", null, null, null, null, 2,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        KnowledgeCategory a2 = KnowledgeCategory.reconstruct(
                4L, 1L, "A-1", null, null, null, null, 1,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        KnowledgeCategory b1 = KnowledgeCategory.reconstruct(
                5L, 2L, "B-1", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        KnowledgeCategory a1a = KnowledgeCategory.reconstruct(
                6L, 3L, "A-2-2", null, null, null, null, 2,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        KnowledgeCategory a1b = KnowledgeCategory.reconstruct(
                7L, 3L, "A-2-1", null, null, null, null, 1,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        when(categoryRepositoryPort.findAll()).thenReturn(
                List.of(root1, root2, a1, a2, b1, a1a, a1b));

        List<KnowledgeCategoryTreeResponse> tree = appService.tree();

        // 根层级排序
        assertThat(tree).hasSize(2);
        assertThat(tree.get(0).getName()).isEqualTo("B");  // sortOrder=1
        assertThat(tree.get(1).getName()).isEqualTo("A");  // sortOrder=2

        // B 的 children
        assertThat(tree.get(0).getChildren()).hasSize(1);
        assertThat(tree.get(0).getChildren().get(0).getName()).isEqualTo("B-1");

        // A 的 children 排序
        List<KnowledgeCategoryTreeResponse> aChildren = tree.get(1).getChildren();
        assertThat(aChildren).hasSize(2);
        assertThat(aChildren.get(0).getName()).isEqualTo("A-1"); // sortOrder=1
        assertThat(aChildren.get(1).getName()).isEqualTo("A-2"); // sortOrder=2

        // A-2 的 children 排序
        List<KnowledgeCategoryTreeResponse> a2Children = aChildren.get(1).getChildren();
        assertThat(a2Children).hasSize(2);
        assertThat(a2Children.get(0).getName()).isEqualTo("A-2-1"); // sortOrder=1
        assertThat(a2Children.get(1).getName()).isEqualTo("A-2-2"); // sortOrder=2
    }

    // ============================================================
    // 排序边界
    // ============================================================

    /**
     * 同级节点 sortOrder 相同时，所有节点均应出现在结果中（不丢失、不重复）
     */
    @Test
    @DisplayName("tree() 同 sortOrder 的兄弟节点全部保留")
    void tree_shouldIncludeAllSiblings_whenSameSortOrder() {
        KnowledgeCategory c1 = KnowledgeCategory.reconstruct(
                1L, null, "第一", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        KnowledgeCategory c2 = KnowledgeCategory.reconstruct(
                2L, null, "第二", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        KnowledgeCategory c3 = KnowledgeCategory.reconstruct(
                3L, null, "第三", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        when(categoryRepositoryPort.findAll()).thenReturn(List.of(c1, c2, c3));

        List<KnowledgeCategoryTreeResponse> tree = appService.tree();

        assertThat(tree).hasSize(3);
        assertThat(tree).extracting("name")
                .containsExactlyInAnyOrder("第一", "第二", "第三");
    }

    /**
     * sortOrder 取极端值（Integer.MAX_VALUE、Integer.MIN_VALUE、负数）
     */
    @Test
    @DisplayName("tree() 处理极端 sortOrder 值")
    void tree_shouldHandleExtremeSortOrderValues() {
        KnowledgeCategory min = KnowledgeCategory.reconstruct(
                1L, null, "最小", null, null, null, null, Integer.MIN_VALUE,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        KnowledgeCategory negative = KnowledgeCategory.reconstruct(
                2L, null, "负数", null, null, null, null, -1,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        KnowledgeCategory zero = KnowledgeCategory.reconstruct(
                3L, null, "零", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        KnowledgeCategory max = KnowledgeCategory.reconstruct(
                4L, null, "最大", null, null, null, null, Integer.MAX_VALUE,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        when(categoryRepositoryPort.findAll()).thenReturn(List.of(min, negative, zero, max));

        List<KnowledgeCategoryTreeResponse> tree = appService.tree();

        assertThat(tree).hasSize(4);
        // MIN_VALUE 应排最前，MAX_VALUE 排最后
        assertThat(tree.get(0).getSortOrder()).isEqualTo(Integer.MIN_VALUE);
        assertThat(tree.get(1).getSortOrder()).isEqualTo(-1);
        assertThat(tree.get(2).getSortOrder()).isEqualTo(0);
        assertThat(tree.get(3).getSortOrder()).isEqualTo(Integer.MAX_VALUE);
    }

    // ============================================================
    // 空 children 列表
    // ============================================================

    /**
     * 单个根节点无子分类时，children 应为空列表非 null
     */
    @Test
    @DisplayName("tree() 叶子节点 children 为空列表（非 null）")
    void tree_shouldReturnEmptyChildrenList_whenLeafNode() {
        KnowledgeCategory leaf = KnowledgeCategory.reconstruct(
                1L, null, "独立分类", "描述", null, null, null, 5,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        when(categoryRepositoryPort.findAll()).thenReturn(List.of(leaf));

        List<KnowledgeCategoryTreeResponse> tree = appService.tree();

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getChildren()).isNotNull();
        assertThat(tree.get(0).getChildren()).isEmpty();
    }

    // ============================================================
    // ACTIVE/INACTIVE 过滤边界
    // ============================================================

    /**
     * 父 ACTIVE、子 INACTIVE：子不应出现在树中
     */
    @Test
    @DisplayName("tree() ACTIVE 父的 INACTIVE 子不出现在树中")
    void tree_shouldExcludeInactiveChild_whenParentActiveChildInactive() {
        KnowledgeCategory parent = KnowledgeCategory.reconstruct(
                1L, null, "父", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        KnowledgeCategory activeChild = KnowledgeCategory.reconstruct(
                2L, 1L, "活子", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        KnowledgeCategory inactiveChild = KnowledgeCategory.reconstruct(
                3L, 1L, "死子", null, null, null, null, 1,
                KnowledgeCategoryStatus.INACTIVE, now, now);
        when(categoryRepositoryPort.findAll()).thenReturn(List.of(parent, activeChild, inactiveChild));

        List<KnowledgeCategoryTreeResponse> tree = appService.tree();

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getChildren()).hasSize(1);
        assertThat(tree.get(0).getChildren().get(0).getName()).isEqualTo("活子");
        // INACTIVE child name should never appear
        assertThat(tree.get(0).getChildren()).extracting("name")
                .doesNotContain("死子");
    }

    /**
     * 父 INACTIVE、子 ACTIVE：父被过滤后，ACTIVE 子成为孤儿提升为根
     */
    @Test
    @DisplayName("tree() INACTIVE 父的 ACTIVE 子提升为根节点")
    void tree_shouldPromoteActiveChildToRoot_whenParentInactive() {
        KnowledgeCategory inactiveParent = KnowledgeCategory.reconstruct(
                1L, null, "死父", null, null, null, null, 0,
                KnowledgeCategoryStatus.INACTIVE, now, now);
        KnowledgeCategory activeChild = KnowledgeCategory.reconstruct(
                2L, 1L, "活子", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        when(categoryRepositoryPort.findAll()).thenReturn(List.of(inactiveParent, activeChild));

        List<KnowledgeCategoryTreeResponse> tree = appService.tree();

        // 子变成根节点
        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getName()).isEqualTo("活子");
        assertThat(tree.get(0).getParentId()).isEqualTo(1L); // 保留原始 parentId
    }

    /**
     * ACTIVE 子节点引用的父 ID 指向一个不在结果集中的 INACTIVE 父，
     * 同时该子还有 ACTIVE 孙节点 — 验证整条子树被提升为根
     */
    @Test
    @DisplayName("tree() INACTIVE 父的 ACTIVE 子树整体提升为根")
    void tree_shouldPromoteEntireSubtree_whenRootInactive() {
        KnowledgeCategory inactiveRoot = KnowledgeCategory.reconstruct(
                1L, null, "死根", null, null, null, null, 0,
                KnowledgeCategoryStatus.INACTIVE, now, now);
        KnowledgeCategory activeChild = KnowledgeCategory.reconstruct(
                2L, 1L, "活子", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        KnowledgeCategory grandchild = KnowledgeCategory.reconstruct(
                3L, 2L, "活孙", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        when(categoryRepositoryPort.findAll()).thenReturn(
                List.of(inactiveRoot, activeChild, grandchild));

        List<KnowledgeCategoryTreeResponse> tree = appService.tree();

        // 子树提升为根，保持内部结构
        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getName()).isEqualTo("活子");
        assertThat(tree.get(0).getChildren()).hasSize(1);
        assertThat(tree.get(0).getChildren().get(0).getName()).isEqualTo("活孙");
    }

    /**
     * 全 INACTIVE — 结果为空列表
     */
    @Test
    @DisplayName("tree() 全 INACTIVE 返回空列表")
    void tree_shouldReturnEmptyList_whenAllCategoriesInactive() {
        KnowledgeCategory c1 = KnowledgeCategory.reconstruct(
                1L, null, "A", null, null, null, null, 0,
                KnowledgeCategoryStatus.INACTIVE, now, now);
        KnowledgeCategory c2 = KnowledgeCategory.reconstruct(
                2L, 1L, "B", null, null, null, null, 0,
                KnowledgeCategoryStatus.INACTIVE, now, now);
        when(categoryRepositoryPort.findAll()).thenReturn(List.of(c1, c2));

        List<KnowledgeCategoryTreeResponse> tree = appService.tree();

        assertThat(tree).isEmpty();
    }

    // ============================================================
    // 可选字段
    // ============================================================

    /**
     * 所有可选字段为 null（description、icon、color、coverImage），不应 NPE
     */
    @Test
    @DisplayName("tree() 所有可选字段为 null 时不抛 NPE")
    void tree_shouldNotNPE_whenAllOptionalFieldsNull() {
        KnowledgeCategory minimal = KnowledgeCategory.reconstruct(
                1L, null, "极简", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        when(categoryRepositoryPort.findAll()).thenReturn(List.of(minimal));

        List<KnowledgeCategoryTreeResponse> tree = appService.tree();

        assertThat(tree).hasSize(1);
        KnowledgeCategoryTreeResponse node = tree.get(0);
        assertThat(node.getName()).isEqualTo("极简");
        assertThat(node.getDescription()).isNull();
        assertThat(node.getIconFileId()).isNull();
        assertThat(node.getIconUrl()).isNull();
        assertThat(node.getColor()).isNull();
        assertThat(node.getCoverImageFileId()).isNull();
        assertThat(node.getCoverImageUrl()).isNull();
    }

    /**
     * description、color 有值，但 icon 和 coverImage 为 null FileRef
     */
    @Test
    @DisplayName("tree() icon/coverImage 为 null 时映射字段为 null")
    void tree_shouldMapNullFileRefToNullFields() {
        KnowledgeCategory category = KnowledgeCategory.reconstruct(
                1L, null, "带描述和颜色", "这是描述", null, "#FF0000", null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        when(categoryRepositoryPort.findAll()).thenReturn(List.of(category));

        List<KnowledgeCategoryTreeResponse> tree = appService.tree();

        assertThat(tree).hasSize(1);
        KnowledgeCategoryTreeResponse node = tree.get(0);
        assertThat(node.getDescription()).isEqualTo("这是描述");
        assertThat(node.getColor()).isEqualTo("#FF0000");
        assertThat(node.getIconFileId()).isNull();
        assertThat(node.getIconUrl()).isNull();
        assertThat(node.getCoverImageFileId()).isNull();
        assertThat(node.getCoverImageUrl()).isNull();
    }

    /**
     * FileRef 完整引用映射到 DTO 的 fileId+url 双字段
     */
    @Test
    @DisplayName("tree() FileRef 完整引用正确映射到 DTO 双字段")
    void tree_shouldMapFileRefToDtoFields() {
        FileRef icon = FileRef.of(100L, "https://example.com/icon.png");
        FileRef cover = FileRef.of(200L, "https://example.com/cover.png");
        KnowledgeCategory category = KnowledgeCategory.reconstruct(
                1L, null, "完整分类", "完整描述", icon, "#00FF00", cover, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        when(categoryRepositoryPort.findAll()).thenReturn(List.of(category));

        List<KnowledgeCategoryTreeResponse> tree = appService.tree();

        assertThat(tree).hasSize(1);
        KnowledgeCategoryTreeResponse node = tree.get(0);
        assertThat(node.getIconFileId()).isEqualTo(100L);
        assertThat(node.getIconUrl()).isEqualTo("https://example.com/icon.png");
        assertThat(node.getCoverImageFileId()).isEqualTo(200L);
        assertThat(node.getCoverImageUrl()).isEqualTo("https://example.com/cover.png");
    }

    /**
     * FileRef.of(null, null) 语义 — FileRef 对象存在但双字段均为 null
     */
    @Test
    @DisplayName("tree() FileRef.of(null,null) 映射为 null fileId 和 null url")
    void tree_shouldMapNullNullFileRefToNullFields() {
        FileRef emptyIcon = FileRef.of(null, null);
        KnowledgeCategory category = KnowledgeCategory.reconstruct(
                1L, null, "空图标", null, emptyIcon, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        when(categoryRepositoryPort.findAll()).thenReturn(List.of(category));

        List<KnowledgeCategoryTreeResponse> tree = appService.tree();

        assertThat(tree).hasSize(1);
        KnowledgeCategoryTreeResponse node = tree.get(0);
        assertThat(node.getIconFileId()).isNull();
        assertThat(node.getIconUrl()).isNull();
    }

    // ============================================================
    // 时间戳
    // ============================================================

    /**
     * createdAt / updatedAt 返回毫秒时间戳（Long 类型）
     */
    @Test
    @DisplayName("tree() createdAt/updatedAt 返回毫秒时间戳 Long")
    void tree_shouldReturnEpochMillis_whenCategoriesHaveDates() {
        KnowledgeCategory category = KnowledgeCategory.reconstruct(
                1L, null, "时间测试", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        when(categoryRepositoryPort.findAll()).thenReturn(List.of(category));

        List<KnowledgeCategoryTreeResponse> tree = appService.tree();

        assertThat(tree).hasSize(1);
        KnowledgeCategoryTreeResponse node = tree.get(0);
        assertThat(node.getCreatedAt()).isNotNull();
        assertThat(node.getCreatedAt()).isInstanceOf(Long.class);
        assertThat(node.getCreatedAt()).isPositive();
        assertThat(node.getUpdatedAt()).isNotNull();
        assertThat(node.getUpdatedAt()).isInstanceOf(Long.class);
        assertThat(node.getUpdatedAt()).isPositive();
    }

    // ============================================================
    // status 字段
    // ============================================================

    /**
     * 所有返回节点的 status 均为 "ACTIVE"
     */
    @Test
    @DisplayName("tree() 所有返回节点 status 均为 ACTIVE")
    void tree_shouldHaveActiveStatus_whenAllReturnedNodes() {
        KnowledgeCategory root = KnowledgeCategory.reconstruct(
                1L, null, "根", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        KnowledgeCategory child = KnowledgeCategory.reconstruct(
                2L, 1L, "子", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        when(categoryRepositoryPort.findAll()).thenReturn(List.of(root, child));

        List<KnowledgeCategoryTreeResponse> tree = appService.tree();

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getStatus()).isEqualTo("ACTIVE");
        assertThat(tree.get(0).getChildren().get(0).getStatus()).isEqualTo("ACTIVE");
    }

    // ============================================================
    // 空仓储
    // ============================================================

    /**
     * 仓储返回空列表 → tree() 返回空列表
     */
    @Test
    @DisplayName("tree() 空仓储返回空列表")
    void tree_shouldReturnEmptyList_whenRepositoryEmpty() {
        when(categoryRepositoryPort.findAll()).thenReturn(Collections.emptyList());

        List<KnowledgeCategoryTreeResponse> tree = appService.tree();

        assertThat(tree).isNotNull();
        assertThat(tree).isEmpty();
    }

    // ============================================================
    // 复杂混合场景
    // ============================================================

    /**
     * 混合场景：多个 ACTIVE 根 + 部分有子 + 部分孤儿 + INACTIVE 被过滤
     */
    @Test
    @DisplayName("tree() 复杂混合场景：多根、孤儿、INACTIVE 过滤同时存在")
    void tree_shouldHandleComplexMixedScenario() {
        KnowledgeCategory root1 = KnowledgeCategory.reconstruct(
                1L, null, "根A", null, null, null, null, 1,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        KnowledgeCategory root2 = KnowledgeCategory.reconstruct(
                2L, null, "根B", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        KnowledgeCategory childOf1 = KnowledgeCategory.reconstruct(
                3L, 1L, "A-子", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        KnowledgeCategory childOf1Inactive = KnowledgeCategory.reconstruct(
                4L, 1L, "A-死子", null, null, null, null, 1,
                KnowledgeCategoryStatus.INACTIVE, now, now);
        KnowledgeCategory orphan = KnowledgeCategory.reconstruct(
                5L, 999L, "孤儿", null, null, null, null, 2,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        KnowledgeCategory orphanChild = KnowledgeCategory.reconstruct(
                6L, 5L, "孤儿-子", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        KnowledgeCategory inactiveRoot = KnowledgeCategory.reconstruct(
                7L, null, "死根", null, null, null, null, 0,
                KnowledgeCategoryStatus.INACTIVE, now, now);
        when(categoryRepositoryPort.findAll()).thenReturn(List.of(
                root1, root2, childOf1, childOf1Inactive, orphan, orphanChild, inactiveRoot));

        List<KnowledgeCategoryTreeResponse> tree = appService.tree();

        // 根层级按 sortOrder：根B(0) → 根A(1) → 孤儿(2)
        assertThat(tree).hasSize(3);
        assertThat(tree).extracting("name").containsExactly("根B", "根A", "孤儿");

        // 根B(0) 无子
        assertThat(tree.get(0).getChildren()).isEmpty();

        // 根A(1) 只有 ACTIVE 子
        assertThat(tree.get(1).getChildren()).hasSize(1);
        assertThat(tree.get(1).getChildren().get(0).getName()).isEqualTo("A-子");

        // 孤儿(2) 有子
        assertThat(tree.get(2).getChildren()).hasSize(1);
        assertThat(tree.get(2).getChildren().get(0).getName()).isEqualTo("孤儿-子");

        // 死根不在结果中
        assertThat(tree).extracting("name").doesNotContain("死根");
    }
}
