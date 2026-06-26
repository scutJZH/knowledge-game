package com.knowledgegame.core.infrastructure.adapter.repoadapter;

import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.domainenum.KnowledgeItemStatus;
import com.knowledgegame.core.domain.model.domainenum.ResourceType;
import com.knowledgegame.core.domain.port.outbound.FileCleanupPort;
import com.knowledgegame.core.infrastructure.db.entity.KnowledgeCategoryPO;
import com.knowledgegame.core.infrastructure.db.entity.KnowledgeItemCategoryRelationPO;
import com.knowledgegame.core.infrastructure.db.entity.KnowledgeItemDeletedPO;
import com.knowledgegame.core.infrastructure.db.entity.KnowledgeItemPO;
import com.knowledgegame.core.infrastructure.db.entity.RecycleBinItemPO;
import com.knowledgegame.core.infrastructure.db.repository.KnowledgeCategoryJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.KnowledgeItemCategoryRelationJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.KnowledgeItemDeletedJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.KnowledgeItemJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.RecycleBinItemJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@DataJpaTest
@EntityScan(basePackages = "com.knowledgegame.core.infrastructure.db.entity")
@EnableJpaRepositories(basePackages = "com.knowledgegame.core.infrastructure.db.repository")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({KnowledgeItemRecycleBinStrategy.class,
        KnowledgeItemRepositoryAdapter.class,
        RecycleBinItemRepositoryAdapter.class})
@ActiveProfiles("test")
class KnowledgeItemRecycleBinStrategyTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private KnowledgeItemRecycleBinStrategy strategy;

    @Autowired
    private KnowledgeItemJpaRepository itemJpaRepository;

    @Autowired
    private KnowledgeItemDeletedJpaRepository deletedJpaRepository;

    @Autowired
    private KnowledgeItemCategoryRelationJpaRepository relationJpaRepository;

    @Autowired
    private KnowledgeCategoryJpaRepository categoryJpaRepository;

    @Autowired
    private RecycleBinItemJpaRepository recycleBinItemJpaRepository;

    @MockBean
    private FileCleanupPort fileCleanupPort;

    @BeforeEach
    void setUp() {
        recycleBinItemJpaRepository.deleteAll();
        relationJpaRepository.deleteAll();
        deletedJpaRepository.deleteAll();
        itemJpaRepository.deleteAll();
        categoryJpaRepository.deleteAll();
        entityManager.flush();
    }

    // ============================================================
    // validateDeletable
    // ============================================================

    @Test
    @DisplayName("validateDeletable — 条目存在 → 正常返回")
    void validateDeletable_exists_shouldPass() {
        KnowledgeItemPO item = persistItem("测试条目", null);
        assertThatCode(() -> strategy.validateDeletable(item.getId())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateDeletable — 条目不存在 → BusinessException")
    void validateDeletable_notFound_shouldThrow() {
        assertThatThrownBy(() -> strategy.validateDeletable(9999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("知识条目不存在: 9999");
    }

    // ============================================================
    // moveToRecycleBin
    // ============================================================

    @Test
    @DisplayName("moveToRecycleBin — 有分类关联+有封面图 → 全表状态正确")
    void moveToRecycleBin_withCategoriesAndCoverImage_shouldSnapshotAll() {
        KnowledgeItemPO item = persistItem("完整条目", 100L);
        KnowledgeCategoryPO cat = persistCategory("测试分类");
        persistItemRelation(item.getId(), cat.getId());
        entityManager.flush();

        strategy.moveToRecycleBin(item.getId(), "admin");
        entityManager.flush();
        entityManager.clear();

        assertThat(itemJpaRepository.findById(item.getId())).isEmpty();
        assertThat(relationJpaRepository.findByItemId(item.getId())).isEmpty();

        KnowledgeItemDeletedPO deletedPO = deletedJpaRepository.findByOriginalId(item.getId()).orElseThrow();
        assertThat(deletedPO.getTitle()).isEqualTo("完整条目");
        assertThat(deletedPO.getContent()).isEqualTo("正文");
        assertThat(deletedPO.getContentHtml()).isEqualTo("<p>正文</p>");
        assertThat(deletedPO.getCoverImageFileId()).isEqualTo(100L);
        assertThat(deletedPO.getCoverImageUrl()).isEqualTo("http://example.com/img.png");
        assertThat(deletedPO.getTags()).isEqualTo("[\"Java\"]");
        assertThat(deletedPO.getSortOrder()).isEqualTo(0);
        assertThat(deletedPO.getStatus()).isEqualTo(KnowledgeItemStatus.ACTIVE);
        assertThat(deletedPO.getRelatedData()).contains("categoryAssociationIds");
        assertThat(deletedPO.getRelatedData()).contains(cat.getId().toString());
        assertThat(deletedPO.getDeletedBy()).isEqualTo("admin");

        RecycleBinItemPO binPO = recycleBinItemJpaRepository.findAll().get(0);
        assertThat(binPO.getResourceType()).isEqualTo(ResourceType.KNOWLEDGE_ITEM);
        assertThat(binPO.getOriginalId()).isEqualTo(item.getId());
        assertThat(binPO.getOriginalName()).isEqualTo("完整条目");
    }

    @Test
    @DisplayName("moveToRecycleBin — 无分类关联+无封面图 → related_data 为空数组，coverImage 字段为 null")
    void moveToRecycleBin_noCategoriesNoCoverImage_shouldHaveEmptyRelatedData() {
        KnowledgeItemPO item = persistItem("无关联条目", null);
        entityManager.flush();

        strategy.moveToRecycleBin(item.getId(), "admin");
        entityManager.flush();
        entityManager.clear();

        KnowledgeItemDeletedPO deletedPO = deletedJpaRepository.findByOriginalId(item.getId()).orElseThrow();
        assertThat(deletedPO.getRelatedData()).contains("categoryAssociationIds");
        assertThat(deletedPO.getRelatedData()).contains("[]");
        assertThat(deletedPO.getCoverImageFileId()).isNull();
        assertThat(deletedPO.getCoverImageUrl()).isNull();
        assertThat(deletedPO.getTags()).isEqualTo("[\"Java\"]");
    }

    @Test
    @DisplayName("moveToRecycleBin — 条目不存在 → BusinessException")
    void moveToRecycleBin_notFound_shouldThrow() {
        assertThatThrownBy(() -> strategy.moveToRecycleBin(9999L, "admin"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("知识条目不存在: 9999");
    }

    @Test
    @DisplayName("moveToRecycleBin — originalName 使用 title（不截断）")
    void moveToRecycleBin_originalNameShouldBeTitle() {
        String title = "A".repeat(200);
        KnowledgeItemPO item = persistItem(title, null);
        entityManager.flush();

        strategy.moveToRecycleBin(item.getId(), "admin");
        entityManager.flush();
        entityManager.clear();

        RecycleBinItemPO binPO = recycleBinItemJpaRepository.findAll().get(0);
        assertThat(binPO.getOriginalName()).isEqualTo(title);
        assertThat(binPO.getOriginalName()).hasSize(200);
    }

    // ============================================================
    // restore
    // ============================================================

    @Test
    @DisplayName("restore — 有分类关联+分类全存在 → 行恢复(INACTIVE) + 分类关联恢复 + 快照删除")
    void restore_withCategories_shouldRestoreRowAndRelations() {
        KnowledgeCategoryPO cat = persistCategory("恢复分类");
        entityManager.flush();

        List<Long> categoryIds = List.of(cat.getId());
        LocalDateTime originalCreatedAt = LocalDateTime.of(2025, 6, 1, 10, 0);
        Long recycleBinId = setupDeletedAndRecycleBinWithCategories(100L, "恢复条目",
                originalCreatedAt, categoryIds);

        strategy.restore(recycleBinId);
        entityManager.flush();
        entityManager.clear();

        KnowledgeItemPO restored = itemJpaRepository.findById(100L).orElseThrow();
        assertThat(restored.getTitle()).isEqualTo("恢复条目");
        assertThat(restored.getStatus()).isEqualTo(KnowledgeItemStatus.INACTIVE);
        assertThat(restored.getCreatedAt()).isEqualTo(originalCreatedAt);
        assertThat(restored.getUpdatedAt()).isCloseTo(LocalDateTime.now(), within(5, java.time.temporal.ChronoUnit.SECONDS));

        List<KnowledgeItemCategoryRelationPO> relations = relationJpaRepository.findByItemId(100L);
        assertThat(relations).hasSize(1);
        assertThat(relations.get(0).getCategoryId()).isEqualTo(cat.getId());

        assertThat(deletedJpaRepository.findByOriginalId(100L)).isEmpty();
        assertThat(recycleBinItemJpaRepository.findById(recycleBinId)).isEmpty();
    }

    @Test
    @DisplayName("restore — 关联分类部分被删 → BusinessException，原表不恢复")
    void restore_partialCategoriesDeleted_shouldThrow() {
        List<Long> categoryIds = List.of(9999L);
        Long recycleBinId = setupDeletedAndRecycleBinWithCategories(200L, "部分分类",
                LocalDateTime.now(), categoryIds);

        assertThatThrownBy(() -> strategy.restore(recycleBinId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("知识条目关联的分类已被删除，无法恢复");

        assertThat(itemJpaRepository.findById(200L)).isEmpty();
    }

    @Test
    @DisplayName("restore — 无分类关联 → 恢复行但无关联恢复")
    void restore_noCategories_shouldRestoreRowOnly() {
        Long recycleBinId = setupDeletedAndRecycleBin(300L, "无分类条目", LocalDateTime.now());

        strategy.restore(recycleBinId);
        entityManager.flush();
        entityManager.clear();

        assertThat(itemJpaRepository.findById(300L)).isPresent();
        assertThat(relationJpaRepository.findByItemId(300L)).isEmpty();
        assertThat(deletedJpaRepository.findByOriginalId(300L)).isEmpty();
        assertThat(recycleBinItemJpaRepository.findById(recycleBinId)).isEmpty();
    }

    @Test
    @DisplayName("restore — 回收站记录不存在 → BusinessException")
    void restore_recycleBinNotFound_shouldThrow() {
        assertThatThrownBy(() -> strategy.restore(9999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("回收站记录不存在: 9999");
    }

    @Test
    @DisplayName("restore — 快照不存在 → BusinessException")
    void restore_snapshotNotFound_shouldThrow() {
        RecycleBinItemPO bin = persistRecycleBinItem(777L, "Ghost Item");
        entityManager.flush();

        assertThatThrownBy(() -> strategy.restore(bin.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("知识条目快照不存在: 777");
    }

    // ============================================================
    // purge
    // ============================================================

    @Test
    @DisplayName("purge — 有封面图 → deleted + recycle_bin 均删除 + FileCleanupPort 被调用")
    void purge_withCoverImage_shouldCleanBothAndCallFileCleanup() {
        Long recycleBinId = setupDeletedAndRecycleBinWithCoverImage(500L);

        strategy.purge(recycleBinId);
        entityManager.flush();
        entityManager.clear();

        assertThat(deletedJpaRepository.findByOriginalId(500L)).isEmpty();
        assertThat(recycleBinItemJpaRepository.findById(recycleBinId)).isEmpty();
        verify(fileCleanupPort, times(1)).deleteFile(100L);
    }

    @Test
    @DisplayName("purge — 无封面图 → FileCleanupPort 不被调用")
    void purge_noCoverImage_shouldNotCallFileCleanup() {
        Long recycleBinId = setupDeletedAndRecycleBin(600L, "无封面图条目", LocalDateTime.now());

        strategy.purge(recycleBinId);
        entityManager.flush();
        entityManager.clear();

        assertThat(deletedJpaRepository.findByOriginalId(600L)).isEmpty();
        assertThat(recycleBinItemJpaRepository.findById(recycleBinId)).isEmpty();
        verifyNoInteractions(fileCleanupPort);
    }

    @Test
    @DisplayName("purge — 回收站记录不存在 → BusinessException")
    void purge_recycleBinNotFound_shouldThrow() {
        assertThatThrownBy(() -> strategy.purge(9999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("回收站记录不存在: 9999");
    }

    @Test
    @DisplayName("purge — 快照已被并发删除 → 静默跳过，仅删 recycle_bin")
    void purge_snapshotConcurrentlyDeleted_shouldSilentlySkip() {
        RecycleBinItemPO bin = persistRecycleBinItem(555L, "Ghost Item");
        entityManager.flush();

        strategy.purge(bin.getId());
        entityManager.flush();

        assertThat(recycleBinItemJpaRepository.findById(bin.getId())).isEmpty();
    }

    @Test
    @DisplayName("purge — FileCleanupPort 抛异常 → 仅 log.warn，DB 清理不受影响")
    void purge_fileCleanupThrows_shouldStillCleanDb() {
        Long recycleBinId = setupDeletedAndRecycleBinWithCoverImage(700L);
        doThrow(new RuntimeException("文件服务不可用"))
                .when(fileCleanupPort).deleteFile(any());

        strategy.purge(recycleBinId);
        entityManager.flush();
        entityManager.clear();

        assertThat(deletedJpaRepository.findByOriginalId(700L)).isEmpty();
        assertThat(recycleBinItemJpaRepository.findById(recycleBinId)).isEmpty();
        verify(fileCleanupPort, times(1)).deleteFile(100L);
    }

    // ============================================================
    // helpers
    // ============================================================

    private KnowledgeItemPO persistItem(String title, Long coverImageFileId) {
        KnowledgeItemPO po = KnowledgeItemPO.builder()
                .title(title)
                .content("正文")
                .contentHtml("<p>正文</p>")
                .coverImageFileId(coverImageFileId)
                .coverImageUrl(coverImageFileId != null ? "http://example.com/img.png" : null)
                .tags("[\"Java\"]")
                .sortOrder(0)
                .status(KnowledgeItemStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return itemJpaRepository.saveAndFlush(po);
    }

    private KnowledgeCategoryPO persistCategory(String name) {
        KnowledgeCategoryPO po = KnowledgeCategoryPO.builder()
                .name(name)
                .sortOrder(0)
                .status(com.knowledgegame.core.domain.model.domainenum.KnowledgeCategoryStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return categoryJpaRepository.saveAndFlush(po);
    }

    private void persistItemRelation(Long itemId, Long categoryId) {
        KnowledgeItemCategoryRelationPO po = KnowledgeItemCategoryRelationPO.builder()
                .itemId(itemId)
                .categoryId(categoryId)
                .build();
        relationJpaRepository.saveAndFlush(po);
    }

    private Long setupDeletedAndRecycleBin(Long originalId, String title,
                                           LocalDateTime createdAt) {
        return setupDeletedAndRecycleBinWithCategories(originalId, title, createdAt, List.of());
    }

    private Long setupDeletedAndRecycleBinWithCategories(Long originalId, String title,
                                                         LocalDateTime createdAt,
                                                         List<Long> categoryIds) {
        KnowledgeItemDeletedPO deletedPO = KnowledgeItemDeletedPO.builder()
                .originalId(originalId)
                .title(title)
                .content("正文")
                .contentHtml("<p>正文</p>")
                .coverImageFileId(null)
                .coverImageUrl(null)
                .tags("[\"Java\"]")
                .sortOrder(0)
                .status(KnowledgeItemStatus.ACTIVE)
                .createdAt(createdAt)
                .updatedAt(LocalDateTime.now())
                .relatedData(KnowledgeItemRecycleBinStrategy.writeCategoryIds(categoryIds))
                .deletedBy("admin")
                .deletedAt(LocalDateTime.now())
                .build();
        deletedJpaRepository.saveAndFlush(deletedPO);

        RecycleBinItemPO binPO = RecycleBinItemPO.builder()
                .resourceType(ResourceType.KNOWLEDGE_ITEM)
                .originalId(originalId)
                .originalName(title)
                .deletedBy("admin")
                .deletedAt(LocalDateTime.now())
                .restoreDeadline(LocalDateTime.now().plusDays(30))
                .build();
        binPO = recycleBinItemJpaRepository.saveAndFlush(binPO);
        return binPO.getId();
    }

    private Long setupDeletedAndRecycleBinWithCoverImage(Long originalId) {
        KnowledgeItemDeletedPO deletedPO = KnowledgeItemDeletedPO.builder()
                .originalId(originalId)
                .title("有封面图条目")
                .content("正文")
                .contentHtml("<p>正文</p>")
                .coverImageFileId(100L)
                .coverImageUrl("http://example.com/img.png")
                .tags("[\"Java\"]")
                .sortOrder(0)
                .status(KnowledgeItemStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .relatedData(KnowledgeItemRecycleBinStrategy.writeCategoryIds(List.of()))
                .deletedBy("admin")
                .deletedAt(LocalDateTime.now())
                .build();
        deletedJpaRepository.saveAndFlush(deletedPO);

        RecycleBinItemPO binPO = RecycleBinItemPO.builder()
                .resourceType(ResourceType.KNOWLEDGE_ITEM)
                .originalId(originalId)
                .originalName("有封面图条目")
                .deletedBy("admin")
                .deletedAt(LocalDateTime.now())
                .restoreDeadline(LocalDateTime.now().plusDays(30))
                .build();
        binPO = recycleBinItemJpaRepository.saveAndFlush(binPO);
        return binPO.getId();
    }

    private RecycleBinItemPO persistRecycleBinItem(Long originalId, String originalName) {
        RecycleBinItemPO po = RecycleBinItemPO.builder()
                .resourceType(ResourceType.KNOWLEDGE_ITEM)
                .originalId(originalId)
                .originalName(originalName)
                .deletedBy("admin")
                .deletedAt(LocalDateTime.now())
                .restoreDeadline(LocalDateTime.now().plusDays(30))
                .build();
        return recycleBinItemJpaRepository.saveAndFlush(po);
    }
}
