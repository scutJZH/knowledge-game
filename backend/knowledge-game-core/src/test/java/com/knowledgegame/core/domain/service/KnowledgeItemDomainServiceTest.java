package com.knowledgegame.core.domain.service;

import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.domainenum.KnowledgeCategoryStatus;
import com.knowledgegame.core.domain.model.entity.KnowledgeCategory;
import com.knowledgegame.core.domain.model.entity.KnowledgeItem;
import com.knowledgegame.core.domain.model.vo.FileRef;
import com.knowledgegame.core.domain.port.outbound.KnowledgeCategoryRepositoryPort;
import com.knowledgegame.core.domain.port.outbound.KnowledgeItemRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * KnowledgeItemDomainService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeItemDomainServiceTest {

    @Mock
    private KnowledgeItemRepository itemRepository;

    @Mock
    private KnowledgeCategoryRepositoryPort categoryRepositoryPort;

    /**
     * 校验创建 - 正常
     */
    @Test
    void validateAndCreate_shouldSucceed() {
        when(categoryRepositoryPort.findAllByIdIn(anyList()))
                .thenReturn(List.of(buildCategory(1L, "分类1", KnowledgeCategoryStatus.ACTIVE)));

        KnowledgeItemDomainService service = new KnowledgeItemDomainService(itemRepository, categoryRepositoryPort);

        KnowledgeItem result = service.validateAndCreate(
                "标题", "内容",
                FileRef.of(1L, "https://example.com/cover.png"),
                List.of("Java"), 0,
                List.of(1L)
        );

        assertNotNull(result);
        assertEquals("标题", result.getTitle());
    }

    /**
     * 校验创建 - 标题为空抛异常
     */
    @Test
    void validateAndCreate_shouldThrow_whenTitleBlank() {
        KnowledgeItemDomainService service = new KnowledgeItemDomainService(itemRepository, categoryRepositoryPort);

        assertThrows(BusinessException.class,
                () -> service.validateAndCreate(
                        "", "内容", null, null, 0, List.of(1L)
                ));
    }

    /**
     * 校验创建 - 标题为 null 抛异常
     */
    @Test
    void validateAndCreate_shouldThrow_whenTitleNull() {
        KnowledgeItemDomainService service = new KnowledgeItemDomainService(itemRepository, categoryRepositoryPort);

        assertThrows(BusinessException.class,
                () -> service.validateAndCreate(
                        null, "内容", null, null, 0, List.of(1L)
                ));
    }

    /**
     * 校验创建 - 标题超长抛异常
     */
    @Test
    void validateAndCreate_shouldThrow_whenTitleTooLong() {
        KnowledgeItemDomainService service = new KnowledgeItemDomainService(itemRepository, categoryRepositoryPort);
        String longTitle = "a".repeat(201);

        assertThrows(BusinessException.class,
                () -> service.validateAndCreate(
                        longTitle, "内容", null, null, 0, List.of(1L)
                ));
    }

    /**
     * 校验创建 - 标题恰好 200 字正常
     */
    @Test
    void validateAndCreate_shouldSucceed_whenTitleExactly200() {
        when(categoryRepositoryPort.findAllByIdIn(anyList()))
                .thenReturn(List.of(buildCategory(1L, "分类1", KnowledgeCategoryStatus.ACTIVE)));
        KnowledgeItemDomainService service = new KnowledgeItemDomainService(itemRepository, categoryRepositoryPort);
        String title200 = "a".repeat(200);

        KnowledgeItem result = service.validateAndCreate(
                title200, "内容", null, null, 0, List.of(1L)
        );

        assertNotNull(result);
        assertEquals(200, result.getTitle().length());
    }

    /**
     * 校验创建 - 内容为空抛异常
     */
    @Test
    void validateAndCreate_shouldThrow_whenContentBlank() {
        KnowledgeItemDomainService service = new KnowledgeItemDomainService(itemRepository, categoryRepositoryPort);

        assertThrows(BusinessException.class,
                () -> service.validateAndCreate(
                        "标题", "", null, null, 0, List.of(1L)
                ));
    }

    /**
     * 校验创建 - 内容为 null 抛异常
     */
    @Test
    void validateAndCreate_shouldThrow_whenContentNull() {
        KnowledgeItemDomainService service = new KnowledgeItemDomainService(itemRepository, categoryRepositoryPort);

        assertThrows(BusinessException.class,
                () -> service.validateAndCreate(
                        "标题", null, null, null, 0, List.of(1L)
                ));
    }

    /**
     * 校验创建 - 内容恰好 50000 字正常
     */
    @Test
    void validateAndCreate_shouldSucceed_whenContentExactly50000() {
        when(categoryRepositoryPort.findAllByIdIn(anyList()))
                .thenReturn(List.of(buildCategory(1L, "分类1", KnowledgeCategoryStatus.ACTIVE)));
        KnowledgeItemDomainService service = new KnowledgeItemDomainService(itemRepository, categoryRepositoryPort);
        String content = "a".repeat(50000);

        KnowledgeItem result = service.validateAndCreate(
                "标题", content, null, null, 0, List.of(1L)
        );

        assertNotNull(result);
        assertEquals(50000, result.getContent().length());
    }

    /**
     * 校验创建 - 内容 50001 字抛异常
     */
    @Test
    void validateAndCreate_shouldThrow_whenContent50001() {
        KnowledgeItemDomainService service = new KnowledgeItemDomainService(itemRepository, categoryRepositoryPort);
        String content = "a".repeat(50001);

        assertThrows(BusinessException.class,
                () -> service.validateAndCreate(
                        "标题", content, null, null, 0, List.of(1L)
                ));
    }

    /**
     * 校验创建 - 标签超过 10 个抛异常
     */
    @Test
    void validateAndCreate_shouldThrow_whenTooManyTags() {
        KnowledgeItemDomainService service = new KnowledgeItemDomainService(itemRepository, categoryRepositoryPort);
        List<String> tags = IntStream.range(0, 11)
                .mapToObj(i -> "tag" + i).toList();

        assertThrows(BusinessException.class,
                () -> service.validateAndCreate(
                        "标题", "内容", null, tags, 0, List.of(1L)
                ));
    }

    /**
     * 校验创建 - 单个标签超过 20 字抛异常
     */
    @Test
    void validateAndCreate_shouldThrow_whenSingleTagTooLong() {
        KnowledgeItemDomainService service = new KnowledgeItemDomainService(itemRepository, categoryRepositoryPort);
        String longTag = "a".repeat(21);

        assertThrows(BusinessException.class,
                () -> service.validateAndCreate(
                        "标题", "内容", null, List.of(longTag), 0, List.of(1L)
                ));
    }

    /**
     * 校验创建 - 标签恰好 10 个正常
     */
    @Test
    void validateAndCreate_shouldSucceed_whenExactly10Tags() {
        when(categoryRepositoryPort.findAllByIdIn(anyList()))
                .thenReturn(List.of(buildCategory(1L, "分类1", KnowledgeCategoryStatus.ACTIVE)));
        KnowledgeItemDomainService service = new KnowledgeItemDomainService(itemRepository, categoryRepositoryPort);
        List<String> tags = IntStream.range(0, 10)
                .mapToObj(i -> "tag" + i).toList();

        KnowledgeItem result = service.validateAndCreate(
                "标题", "内容", null, tags, 0, List.of(1L)
        );

        assertNotNull(result);
        assertEquals(10, result.getTags().size());
    }

    /**
     * 校验创建 - 标签恰好 20 字正常
     */
    @Test
    void validateAndCreate_shouldSucceed_whenTagExactly20() {
        when(categoryRepositoryPort.findAllByIdIn(anyList()))
                .thenReturn(List.of(buildCategory(1L, "分类1", KnowledgeCategoryStatus.ACTIVE)));
        KnowledgeItemDomainService service = new KnowledgeItemDomainService(itemRepository, categoryRepositoryPort);
        String tag20 = "a".repeat(20);

        KnowledgeItem result = service.validateAndCreate(
                "标题", "内容", null, List.of(tag20), 0, List.of(1L)
        );

        assertNotNull(result);
        assertEquals(20, result.getTags().get(0).length());
    }

    /**
     * 校验创建 - 标签为 null 正常
     */
    @Test
    void validateAndCreate_shouldSucceed_whenTagsNull() {
        when(categoryRepositoryPort.findAllByIdIn(anyList()))
                .thenReturn(List.of(buildCategory(1L, "分类1", KnowledgeCategoryStatus.ACTIVE)));
        KnowledgeItemDomainService service = new KnowledgeItemDomainService(itemRepository, categoryRepositoryPort);

        KnowledgeItem result = service.validateAndCreate(
                "标题", "内容", null, null, 0, List.of(1L)
        );

        assertNotNull(result);
    }

    /**
     * 校验创建 - categoryIds 为空抛异常
     */
    @Test
    void validateAndCreate_shouldThrow_whenCategoryIdsEmpty() {
        KnowledgeItemDomainService service = new KnowledgeItemDomainService(itemRepository, categoryRepositoryPort);

        assertThrows(BusinessException.class,
                () -> service.validateAndCreate(
                        "标题", "内容", null, null, 0, List.of()
                ));
    }

    /**
     * 校验创建 - categoryIds 含不存在的分类抛异常
     */
    @Test
    void validateAndCreate_shouldThrow_whenCategoryNotExists() {
        when(categoryRepositoryPort.findAllByIdIn(anyList()))
                .thenReturn(List.of(buildCategory(1L, "分类1", KnowledgeCategoryStatus.ACTIVE)));
        KnowledgeItemDomainService service = new KnowledgeItemDomainService(itemRepository, categoryRepositoryPort);

        assertThrows(BusinessException.class,
                () -> service.validateAndCreate(
                        "标题", "内容", null, null, 0, List.of(1L, 999L)
                ));
    }

    /**
     * 校验创建 - categoryIds 含 INACTIVE 分类抛异常
     */
    @Test
    void validateAndCreate_shouldThrow_whenCategoryInactive() {
        when(categoryRepositoryPort.findAllByIdIn(anyList()))
                .thenReturn(List.of(buildCategory(1L, "停用分类", KnowledgeCategoryStatus.INACTIVE)));
        KnowledgeItemDomainService service = new KnowledgeItemDomainService(itemRepository, categoryRepositoryPort);

        assertThrows(BusinessException.class,
                () -> service.validateAndCreate(
                        "标题", "内容", null, null, 0, List.of(1L)
                ));
    }

    // ==================== validateUpdate 测试 ====================

    /**
     * 校验更新 - 正常更新全部字段
     */
    @Test
    void validateUpdate_shouldSucceed_whenValidFields() {
        KnowledgeItemDomainService service = new KnowledgeItemDomainService(itemRepository, categoryRepositoryPort);
        KnowledgeItem existing = KnowledgeItem.create("旧标题", "旧内容", null, null, 0);

        service.validateUpdate(existing, "新标题", "新内容", List.of("标签"));
    }

    /**
     * 校验更新 - 所有字段 null 不抛异常
     */
    @Test
    void validateUpdate_shouldSucceed_whenAllNull() {
        KnowledgeItemDomainService service = new KnowledgeItemDomainService(itemRepository, categoryRepositoryPort);
        KnowledgeItem existing = KnowledgeItem.create("旧标题", "旧内容", null, null, 0);

        service.validateUpdate(existing, null, null, null);
    }

    /**
     * 校验更新 - 标题为空抛异常
     */
    @Test
    void validateUpdate_shouldThrow_whenTitleBlank() {
        KnowledgeItemDomainService service = new KnowledgeItemDomainService(itemRepository, categoryRepositoryPort);
        KnowledgeItem existing = KnowledgeItem.create("旧标题", "旧内容", null, null, 0);

        assertThrows(BusinessException.class,
                () -> service.validateUpdate(existing, "", null, null));
    }

    /**
     * 校验更新 - 内容为空抛异常
     */
    @Test
    void validateUpdate_shouldThrow_whenContentBlank() {
        KnowledgeItemDomainService service = new KnowledgeItemDomainService(itemRepository, categoryRepositoryPort);
        KnowledgeItem existing = KnowledgeItem.create("旧标题", "旧内容", null, null, 0);

        assertThrows(BusinessException.class,
                () -> service.validateUpdate(existing, null, "", null));
    }

    /**
     * 校验更新 - 标签超 10 个抛异常
     */
    @Test
    void validateUpdate_shouldThrow_whenTooManyTags() {
        KnowledgeItemDomainService service = new KnowledgeItemDomainService(itemRepository, categoryRepositoryPort);
        KnowledgeItem existing = KnowledgeItem.create("旧标题", "旧内容", null, null, 0);
        List<String> tags = IntStream.range(0, 11)
                .mapToObj(i -> "tag" + i).toList();

        assertThrows(BusinessException.class,
                () -> service.validateUpdate(existing, null, null, tags));
    }

    /**
     * 校验更新 - 单个标签超 20 字抛异常
     */
    @Test
    void validateUpdate_shouldThrow_whenTagTooLong() {
        KnowledgeItemDomainService service = new KnowledgeItemDomainService(itemRepository, categoryRepositoryPort);
        KnowledgeItem existing = KnowledgeItem.create("旧标题", "旧内容", null, null, 0);

        assertThrows(BusinessException.class,
                () -> service.validateUpdate(existing, null, null, List.of("a".repeat(21))));
    }

    /**
     * 校验更新 - 仅更新标题正常
     */
    @Test
    void validateUpdate_shouldSucceed_whenOnlyTitleProvided() {
        KnowledgeItemDomainService service = new KnowledgeItemDomainService(itemRepository, categoryRepositoryPort);
        KnowledgeItem existing = KnowledgeItem.create("旧标题", "旧内容", null, null, 0);

        service.validateUpdate(existing, "新标题", null, null);
    }

    // ==================== validateActivatable 测试 ====================

    /**
     * validateActivatable - 未关联分类时允许启用
     */
    @Test
    @DisplayName("validateActivatable 未关联任何分类时应通过")
    void validateActivatable_shouldPass_whenNoCategories() {
        KnowledgeItemDomainService service = new KnowledgeItemDomainService(itemRepository, categoryRepositoryPort);
        service.validateActivatable("测试条目", List.of(), Map.of());
    }

    /**
     * validateActivatable - 全部 ACTIVE 时允许启用
     */
    @Test
    @DisplayName("validateActivatable 全部关联分类为 ACTIVE 时应通过")
    void validateActivatable_shouldPass_whenAllCategoriesActive() {
        KnowledgeCategory cat1 = buildCategory(10L, "Java基础", KnowledgeCategoryStatus.ACTIVE);
        KnowledgeCategory cat2 = buildCategory(20L, "面向对象", KnowledgeCategoryStatus.ACTIVE);
        Map<Long, KnowledgeCategory> catMap = Map.of(10L, cat1, 20L, cat2);

        KnowledgeItemDomainService service = new KnowledgeItemDomainService(itemRepository, categoryRepositoryPort);
        service.validateActivatable("测试条目", List.of(10L, 20L), catMap);
    }

    /**
     * validateActivatable - 存在 INACTIVE 分类时抛异常
     */
    @Test
    @DisplayName("validateActivatable 存在 INACTIVE 分类时应抛异常")
    void validateActivatable_shouldThrow_whenHasInactiveCategories() {
        KnowledgeCategory cat1 = buildCategory(10L, "Java基础", KnowledgeCategoryStatus.INACTIVE);
        KnowledgeCategory cat2 = buildCategory(20L, "面向对象", KnowledgeCategoryStatus.INACTIVE);
        Map<Long, KnowledgeCategory> catMap = Map.of(10L, cat1, 20L, cat2);

        KnowledgeItemDomainService service = new KnowledgeItemDomainService(itemRepository, categoryRepositoryPort);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateActivatable("测试条目", List.of(10L, 20L), catMap));
        assertEquals("知识条目《测试条目》关联的知识点分类《Java基础》、《面向对象》处于停用状态，请先启用对应分类再启用条目",
                ex.getMessage());
    }

    /**
     * validateActivatable - 部分 INACTIVE 只列出 INACTIVE 名称
     */
    @Test
    @DisplayName("validateActivatable 部分分类 INACTIVE 时只列出 INACTIVE 的名称")
    void validateActivatable_shouldListOnlyInactiveNames() {
        KnowledgeCategory cat1 = buildCategory(10L, "Java基础", KnowledgeCategoryStatus.ACTIVE);
        KnowledgeCategory cat2 = buildCategory(20L, "面向对象", KnowledgeCategoryStatus.INACTIVE);
        Map<Long, KnowledgeCategory> catMap = Map.of(10L, cat1, 20L, cat2);

        KnowledgeItemDomainService service = new KnowledgeItemDomainService(itemRepository, categoryRepositoryPort);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateActivatable("测试条目", List.of(10L, 20L), catMap));
        assertEquals("知识条目《测试条目》关联的知识点分类《面向对象》处于停用状态，请先启用对应分类再启用条目",
                ex.getMessage());
    }

    /**
     * validateActivatable - 分类缺失视为已删除报异常
     */
    @Test
    @DisplayName("validateActivatable categoryMap 中缺少分类 ID 时应报 INACTIVE")
    void validateActivatable_shouldTreatMissingCategoryAsInactive() {
        KnowledgeCategory cat1 = buildCategory(10L, "Java基础", KnowledgeCategoryStatus.ACTIVE);
        Map<Long, KnowledgeCategory> catMap = Map.of(10L, cat1);

        KnowledgeItemDomainService service = new KnowledgeItemDomainService(itemRepository, categoryRepositoryPort);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateActivatable("测试条目", List.of(10L, 999L), catMap));
        assertEquals("知识条目《测试条目》关联的知识点分类《(ID=999)》处于停用状态，请先启用对应分类再启用条目",
                ex.getMessage());
    }

    // ==================== 辅助方法 ====================

    private KnowledgeCategory buildCategory(Long id, String name, KnowledgeCategoryStatus status) {
        return KnowledgeCategory.reconstruct(id, null, name, null, null, null, null, 0,
                status, null, null);
    }
}
