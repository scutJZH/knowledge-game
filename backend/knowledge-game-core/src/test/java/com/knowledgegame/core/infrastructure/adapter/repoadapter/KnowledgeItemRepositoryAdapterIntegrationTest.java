package com.knowledgegame.core.infrastructure.adapter.repoadapter;

import com.knowledgegame.core.domain.model.domainenum.KnowledgeCategoryStatus;
import com.knowledgegame.core.domain.model.domainenum.KnowledgeItemStatus;
import com.knowledgegame.core.domain.model.entity.KnowledgeItem;
import com.knowledgegame.core.domain.model.vo.FileRef;
import com.knowledgegame.core.domain.model.vo.KnowledgeItemSummary;
import com.knowledgegame.core.domain.model.vo.PageResult;
import com.knowledgegame.core.domain.model.vo.SortField;
import com.knowledgegame.core.infrastructure.db.entity.KnowledgeCategoryPO;
import com.knowledgegame.core.infrastructure.db.entity.KnowledgeItemCategoryRelationPO;
import com.knowledgegame.core.infrastructure.db.entity.KnowledgeItemPO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@EntityScan(basePackages = "com.knowledgegame.core.infrastructure.db.entity")
@EnableJpaRepositories(basePackages = "com.knowledgegame.core.infrastructure.db.repository")
@Import(KnowledgeItemRepositoryAdapter.class)
class KnowledgeItemRepositoryAdapterIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private KnowledgeItemRepositoryAdapter adapter;

    /**
     * 按分类名称升序排列：有分类的在前，无分类的在后（NULLS LAST）
     */
    @Test
    @DisplayName("categoryName 升序 → 有分类条目按名称 ASC 在前，无分类条目 NULLS LAST")
    void shouldSortByCategoryNameAsc() {
        // 使用英文前缀确保排序不受 MySQL collation 影响（中文字符排序因 collation 而异）
        KnowledgeCategoryPO catA = persistCategory("AAA-文学");
        KnowledgeCategoryPO catB = persistCategory("BBB-艺术");

        KnowledgeItemPO item1 = persistItem("条目A", 0); // 关联"AAA-文学"
        KnowledgeItemPO item2 = persistItem("条目B", 1); // 无分类
        KnowledgeItemPO item3 = persistItem("条目C", 2); // 关联"BBB-艺术"

        persistRelation(item1.getId(), catA.getId());
        persistRelation(item3.getId(), catB.getId());

        entityManager.flush();
        entityManager.clear();

        PageResult<KnowledgeItem> result = adapter.findByConditions(
                null, null, null, null,
                new SortField("categoryName", SortField.Direction.ASC), 0, 20);

        assertEquals(3, result.getTotalElements());
        List<KnowledgeItem> content = result.getContent();
        // AAA-文学 < BBB-艺术，所以 item1（AAA-文学）在 item3（BBB-艺术）前面
        // item2 无分类，CASE WHEN IS NULL 使其排在最后（NULLS LAST）
        assertEquals(item1.getId(), content.get(0).getId());
        assertEquals(item3.getId(), content.get(1).getId());
        assertEquals(item2.getId(), content.get(2).getId());
    }

    /**
     * 按分类名称降序排列：有分类的在前（按名称 DESC），无分类的 NULLS LAST
     */
    @Test
    @DisplayName("categoryName 降序 → 有分类条目按名称 DESC 在前，无分类条目 NULLS LAST")
    void shouldSortByCategoryNameDesc() {
        KnowledgeCategoryPO catA = persistCategory("AAA-文学");
        KnowledgeCategoryPO catB = persistCategory("BBB-艺术");

        KnowledgeItemPO item1 = persistItem("条目A", 0);
        KnowledgeItemPO item2 = persistItem("条目B", 1);
        KnowledgeItemPO item3 = persistItem("条目C", 2);

        persistRelation(item1.getId(), catA.getId());
        persistRelation(item3.getId(), catB.getId());

        entityManager.flush();
        entityManager.clear();

        PageResult<KnowledgeItem> result = adapter.findByConditions(
                null, null, null, null,
                new SortField("categoryName", SortField.Direction.DESC), 0, 20);

        assertEquals(3, result.getTotalElements());
        List<KnowledgeItem> content = result.getContent();
        // BBB-艺术 > AAA-文学（降序），所以 item3（BBB-艺术）在 item1（AAA-文学）前面
        assertEquals(item3.getId(), content.get(0).getId());
        assertEquals(item1.getId(), content.get(1).getId());
        assertEquals(item2.getId(), content.get(2).getId());
    }

    /**
     * 条目有多个分类：DISTINCT 去重，按 MIN(name) 排序，只返回一条
     */
    @Test
    @DisplayName("条目关联多个分类 → DISTINCT 去重，按 MIN(category.name) 排序")
    void shouldDeduplicate_whenItemHasMultipleCategories() {
        KnowledgeCategoryPO catA = persistCategory("AAA-文学");
        KnowledgeCategoryPO catB = persistCategory("BBB-艺术");

        KnowledgeItemPO item1 = persistItem("多分类条目", 0);

        persistRelation(item1.getId(), catA.getId());
        persistRelation(item1.getId(), catB.getId());

        entityManager.flush();
        entityManager.clear();

        PageResult<KnowledgeItem> result = adapter.findByConditions(
                null, null, null, null,
                new SortField("categoryName", SortField.Direction.ASC), 0, 20);

        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());
        assertEquals(item1.getId(), result.getContent().get(0).getId());
    }

    /**
     * COUNT 一致性：关联 3 个分类的条目，totalElements=1
     */
    @Test
    @DisplayName("COUNT 与数据分页一致 → 关联 3 个分类的条目 totalElements=1")
    void shouldHaveCorrectCount_whenItemHasMultipleCategories() {
        KnowledgeCategoryPO catA = persistCategory("AAA-文学");
        KnowledgeCategoryPO catB = persistCategory("BBB-艺术");
        KnowledgeCategoryPO catC = persistCategory("CCC-科学");

        KnowledgeItemPO item1 = persistItem("三分类型条目", 0);

        persistRelation(item1.getId(), catA.getId());
        persistRelation(item1.getId(), catB.getId());
        persistRelation(item1.getId(), catC.getId());

        entityManager.flush();
        entityManager.clear();

        PageResult<KnowledgeItem> result = adapter.findByConditions(
                null, null, null, null,
                new SortField("categoryName", SortField.Direction.ASC), 0, 20);

        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getTotalPages());
    }

    /**
     * 性能基准：>=1000 条条目 + >=5000 条关联，categoryName 查询 < 500ms
     */
    @Test
    @DisplayName("性能基准 → 1000 条目 + 5000 关联 < 500ms")
    @Tag("performance")
    void shouldBeFastEnough_withLargeDataset() {
        // 预插入 5 个分类
        KnowledgeCategoryPO[] cats = new KnowledgeCategoryPO[5];
        for (int i = 0; i < 5; i++) {
            cats[i] = persistCategory("分类" + i);
        }

        // 预插入 1000 条条目，每条关联 5 个分类 = 5000 条关联
        for (int i = 0; i < 1000; i++) {
            KnowledgeItemPO item = persistItem("条目" + i, i);
            for (int j = 0; j < 5; j++) {
                persistRelation(item.getId(), cats[j].getId());
            }
        }

        entityManager.flush();
        entityManager.clear();

        long start = System.currentTimeMillis();
        PageResult<KnowledgeItem> result = adapter.findByConditions(
                null, null, null, null,
                new SortField("categoryName", SortField.Direction.ASC), 0, 20);
        long elapsed = System.currentTimeMillis() - start;

        assertEquals(1000, result.getTotalElements());
        assertEquals(20, result.getContent().size());
        assertTrue(elapsed < 500,
                "categoryName 排序耗时 " + elapsed + "ms 超过 500ms 阈值");
    }

    /**
     * findByConditionsSummary — 基本分页，返回 KnowledgeItemSummary 不含 content/contentHtml
     */
    @Test
    @DisplayName("findByConditionsSummary → 返回 KnowledgeItemSummary，含 coverImage/tags，不含正文")
    void shouldReturnSummaryWithoutContent() {
        KnowledgeItemPO item = persistItemWithCoverImage("摘要条目", 0, 1L,
                "https://example.com/cover.png", "[\"Java\",\"Spring\"]");

        entityManager.flush();
        entityManager.clear();

        PageResult<KnowledgeItemSummary> result = adapter.findByConditionsSummary(
                null, null, null, null, null, 0, 20);

        assertEquals(1, result.getTotalElements());
        KnowledgeItemSummary summary = result.getContent().get(0);
        assertEquals(item.getId(), summary.getId());
        assertEquals("摘要条目", summary.getTitle());
        assertEquals(1L, summary.getCoverImage().fileId());
        assertEquals("https://example.com/cover.png", summary.getCoverImage().url());
        assertEquals(List.of("Java", "Spring"), summary.getTags());
        assertEquals(0, summary.getSortOrder());
        assertEquals(KnowledgeItemStatus.ACTIVE, summary.getStatus());
    }

    /**
     * findByConditionsSummary — categoryName 排序
     */
    @Test
    @DisplayName("findByConditionsSummary + categoryName 升序 → 有分类在前，无分类 NULLS LAST")
    void shouldSortSummaryByCategoryNameAsc() {
        KnowledgeCategoryPO catA = persistCategory("AAA-文学");
        KnowledgeCategoryPO catB = persistCategory("BBB-艺术");

        KnowledgeItemPO item1 = persistItem("摘要A", 0);
        KnowledgeItemPO item2 = persistItem("摘要B", 1);
        KnowledgeItemPO item3 = persistItem("摘要C", 2);

        persistRelation(item1.getId(), catA.getId());
        persistRelation(item3.getId(), catB.getId());

        entityManager.flush();
        entityManager.clear();

        PageResult<KnowledgeItemSummary> result = adapter.findByConditionsSummary(
                null, null, null, null,
                new SortField("categoryName", SortField.Direction.ASC), 0, 20);

        assertEquals(3, result.getTotalElements());
        List<KnowledgeItemSummary> content = result.getContent();
        assertEquals(item1.getId(), content.get(0).getId());
        assertEquals(item3.getId(), content.get(1).getId());
        assertEquals(item2.getId(), content.get(2).getId());
    }

    /**
     * findByConditionsSummary — categoryName 降序
     */
    @Test
    @DisplayName("findByConditionsSummary + categoryName 降序 → 有分类按名称 DESC 在前")
    void shouldSortSummaryByCategoryNameDesc() {
        KnowledgeCategoryPO catA = persistCategory("AAA-文学");
        KnowledgeCategoryPO catB = persistCategory("BBB-艺术");

        KnowledgeItemPO item1 = persistItem("摘要A", 0);
        KnowledgeItemPO item2 = persistItem("摘要B", 1);
        KnowledgeItemPO item3 = persistItem("摘要C", 2);

        persistRelation(item1.getId(), catA.getId());
        persistRelation(item3.getId(), catB.getId());

        entityManager.flush();
        entityManager.clear();

        PageResult<KnowledgeItemSummary> result = adapter.findByConditionsSummary(
                null, null, null, null,
                new SortField("categoryName", SortField.Direction.DESC), 0, 20);

        assertEquals(3, result.getTotalElements());
        List<KnowledgeItemSummary> content = result.getContent();
        assertEquals(item3.getId(), content.get(0).getId());
        assertEquals(item1.getId(), content.get(1).getId());
        assertEquals(item2.getId(), content.get(2).getId());
    }

    /**
     * findByConditionsSummary — 关键词 + 状态筛选
     */
    @Test
    @DisplayName("findByConditionsSummary + keyword + status → 筛选正确")
    void shouldFilterSummaryByKeywordAndStatus() {
        persistItem("Java入门", 0);
        persistItem("Spring实战", 1);

        entityManager.flush();
        entityManager.clear();

        PageResult<KnowledgeItemSummary> result = adapter.findByConditionsSummary(
                "Java", null, null, KnowledgeItemStatus.ACTIVE, null, 0, 20);

        assertEquals(1, result.getTotalElements());
        assertEquals("Java入门", result.getContent().get(0).getTitle());
    }

    // --- 辅助方法 ---

    private KnowledgeItemPO persistItemWithCoverImage(String title, int sortOrder,
                                                       Long fileId, String url, String tags) {
        KnowledgeItemPO po = KnowledgeItemPO.builder()
                .title(title)
                .content("正文内容")
                .contentHtml("<p>正文内容</p>")
                .coverImageFileId(fileId)
                .coverImageUrl(url)
                .tags(tags)
                .sortOrder(sortOrder)
                .status(KnowledgeItemStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return entityManager.persistFlushFind(po);
    }

    private KnowledgeCategoryPO persistCategory(String name) {
        KnowledgeCategoryPO po = KnowledgeCategoryPO.builder()
                .name(name)
                .sortOrder(0)
                .status(KnowledgeCategoryStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return entityManager.persistFlushFind(po);
    }

    private KnowledgeItemPO persistItem(String title, int sortOrder) {
        KnowledgeItemPO po = KnowledgeItemPO.builder()
                .title(title)
                .content("内容")
                .contentHtml("<p>内容</p>")
                .sortOrder(sortOrder)
                .status(KnowledgeItemStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return entityManager.persistFlushFind(po);
    }

    private void persistRelation(Long itemId, Long categoryId) {
        KnowledgeItemCategoryRelationPO po = KnowledgeItemCategoryRelationPO.builder()
                .itemId(itemId)
                .categoryId(categoryId)
                .build();
        entityManager.persist(po);
    }
}
