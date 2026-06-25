package com.knowledgegame.core.infrastructure.adapter.repoadapter;

import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.domainenum.Difficulty;
import com.knowledgegame.core.domain.model.domainenum.QuestionStatus;
import com.knowledgegame.core.domain.model.domainenum.QuestionType;
import com.knowledgegame.core.domain.model.domainenum.ResourceType;
import com.knowledgegame.core.infrastructure.db.entity.KnowledgeCategoryPO;
import com.knowledgegame.core.infrastructure.db.entity.QuestionCategoryRelationPO;
import com.knowledgegame.core.infrastructure.db.entity.QuestionDeletedPO;
import com.knowledgegame.core.infrastructure.db.entity.QuestionPO;
import com.knowledgegame.core.infrastructure.db.entity.RecycleBinItemPO;
import com.knowledgegame.core.infrastructure.db.repository.KnowledgeCategoryJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.QuestionCategoryRelationJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.QuestionDeletedJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.QuestionJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.RecycleBinItemJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@DataJpaTest
@EntityScan(basePackages = "com.knowledgegame.core.infrastructure.db.entity")
@EnableJpaRepositories(basePackages = "com.knowledgegame.core.infrastructure.db.repository")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({QuestionRecycleBinStrategy.class,
        QuestionRepositoryAdapter.class,
        RecycleBinItemRepositoryAdapter.class})
@ActiveProfiles("test")
class QuestionRecycleBinStrategyTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private QuestionRecycleBinStrategy strategy;

    @Autowired
    private QuestionJpaRepository questionJpaRepository;

    @Autowired
    private QuestionDeletedJpaRepository deletedJpaRepository;

    @Autowired
    private QuestionCategoryRelationJpaRepository relationJpaRepository;

    @Autowired
    private KnowledgeCategoryJpaRepository categoryJpaRepository;

    @Autowired
    private RecycleBinItemJpaRepository recycleBinItemJpaRepository;

    @BeforeEach
    void setUp() {
        recycleBinItemJpaRepository.deleteAll();
        relationJpaRepository.deleteAll();
        deletedJpaRepository.deleteAll();
        questionJpaRepository.deleteAll();
        categoryJpaRepository.deleteAll();
        entityManager.flush();
    }

    // ============================================================
    // validateDeletable
    // ============================================================

    @Test
    @DisplayName("validateDeletable — 题目存在 → 正常返回")
    void validateDeletable_exists_shouldPass() {
        QuestionPO q = persistQuestion("题目内容");
        assertThatCode(() -> strategy.validateDeletable(q.getId())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateDeletable — 题目不存在 → BusinessException")
    void validateDeletable_notFound_shouldThrow() {
        assertThatThrownBy(() -> strategy.validateDeletable(9999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("题目不存在: 9999");
    }

    // ============================================================
    // moveToRecycleBin
    // ============================================================

    @Test
    @DisplayName("完整删除链路：validateDeletable → moveToRecycleBin")
    void fullDeleteFlow_validateThenMove_shouldSucceed() {
        QuestionPO q = persistQuestion("完整链路测试");
        Long originalId = q.getId();

        assertThatCode(() -> strategy.validateDeletable(originalId)).doesNotThrowAnyException();
        strategy.moveToRecycleBin(originalId, "admin");

        entityManager.flush();
        entityManager.clear();

        assertThat(questionJpaRepository.findById(originalId)).isEmpty();
        assertThat(deletedJpaRepository.findByOriginalId(originalId)).isPresent();
        assertThat(recycleBinItemJpaRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("moveToRecycleBin — 叶子节点 → 删原表 + 快照 + 总览")
    void moveToRecycleBin_leaf_shouldMoveToRecycleBin() {
        QuestionPO q = persistQuestion("测试题目");
        Long originalId = q.getId();

        strategy.moveToRecycleBin(originalId, "admin");
        entityManager.flush();
        entityManager.clear();

        assertThat(questionJpaRepository.findById(originalId)).isEmpty();
        QuestionDeletedPO deletedPO = deletedJpaRepository.findByOriginalId(originalId).orElseThrow();
        assertThat(deletedPO.getContent()).isEqualTo("测试题目");
        assertThat(deletedPO.getStatus()).isEqualTo(QuestionStatus.ACTIVE);
        assertThat(deletedPO.getDeletedBy()).isEqualTo("admin");
        RecycleBinItemPO binPO = recycleBinItemJpaRepository.findAll().get(0);
        assertThat(binPO.getResourceType()).isEqualTo(ResourceType.QUESTION);
        assertThat(binPO.getOriginalName()).isEqualTo("测试题目");
    }

    @Test
    @DisplayName("moveToRecycleBin — 有关联分类 → related_data 含 categoryIds")
    void moveToRecycleBin_withCategoryRelations_shouldSnapshotCategoryIds() {
        QuestionPO q = persistQuestion("有关联题目");
        KnowledgeCategoryPO cat1 = persistCategory("分类1");
        KnowledgeCategoryPO cat2 = persistCategory("分类2");
        persistCategoryRelation(q.getId(), cat1.getId());
        persistCategoryRelation(q.getId(), cat2.getId());
        entityManager.flush();

        strategy.moveToRecycleBin(q.getId(), "admin");
        entityManager.flush();
        entityManager.clear();

        QuestionDeletedPO deletedPO = deletedJpaRepository.findByOriginalId(q.getId()).orElseThrow();
        assertThat(deletedPO.getRelatedData()).contains("categoryAssociationIds");
        assertThat(deletedPO.getRelatedData()).contains(cat1.getId().toString());
        assertThat(deletedPO.getRelatedData()).contains(cat2.getId().toString());
        assertThat(relationJpaRepository.findByQuestionId(q.getId())).isEmpty();
    }

    @Test
    @DisplayName("moveToRecycleBin — 题目不存在 → BusinessException")
    void moveToRecycleBin_notFound_shouldThrow() {
        assertThatThrownBy(() -> strategy.moveToRecycleBin(9999L, "admin"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("题目不存在: 9999");
    }

    // ============================================================
    // restore
    // ============================================================

    @Test
    @DisplayName("restore — 叶子节点 → 行恢复(INACTIVE) + 快照删除 + 回收站删除")
    void restore_leaf_shouldRestoreRow() {
        LocalDateTime originalCreatedAt = LocalDateTime.of(2025, 6, 1, 10, 0);
        Long recycleBinId = setupDeletedAndRecycleBin(100L, "恢复测试", originalCreatedAt);

        strategy.restore(recycleBinId);
        entityManager.flush();
        entityManager.clear();

        QuestionPO restored = questionJpaRepository.findById(100L).orElseThrow();
        assertThat(restored.getContent()).isEqualTo("恢复测试");
        assertThat(restored.getStatus()).isEqualTo(QuestionStatus.INACTIVE);
        assertThat(restored.getCreatedAt()).isEqualTo(originalCreatedAt);
        assertThat(restored.getUpdatedAt()).isCloseTo(LocalDateTime.now(), within(5, java.time.temporal.ChronoUnit.SECONDS));
        assertThat(deletedJpaRepository.findByOriginalId(100L)).isEmpty();
        assertThat(recycleBinItemJpaRepository.findById(recycleBinId)).isEmpty();
    }

    @Test
    @DisplayName("restore — 有关联分类 → 分类关联恢复")
    void restore_withCategories_shouldRestoreRelations() {
        KnowledgeCategoryPO cat = persistCategory("恢复分类");
        entityManager.flush();

        List<Long> categoryIds = List.of(cat.getId());
        Long recycleBinId = setupDeletedAndRecycleBinWithCategories(200L, "带分类恢复",
                LocalDateTime.now(), categoryIds);

        strategy.restore(recycleBinId);
        entityManager.flush();
        entityManager.clear();

        assertThat(questionJpaRepository.findById(200L)).isPresent();
        List<QuestionCategoryRelationPO> relations = relationJpaRepository.findByQuestionId(200L);
        assertThat(relations).hasSize(1);
        assertThat(relations.get(0).getCategoryId()).isEqualTo(cat.getId());
    }

    @Test
    @DisplayName("restore — 回收站记录不存在 → BusinessException")
    void restore_recycleBinNotFound_shouldThrow() {
        assertThatThrownBy(() -> strategy.restore(9999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("回收站记录不存在: 9999");
    }

    @Test
    @DisplayName("restore — 主快照不存在 → BusinessException")
    void restore_snapshotNotFound_shouldThrow() {
        RecycleBinItemPO bin = persistRecycleBinItem(777L, "Ghost");
        entityManager.flush();

        assertThatThrownBy(() -> strategy.restore(bin.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("题目快照不存在: 777");
    }

    // ============================================================
    // purge
    // ============================================================

    @Test
    @DisplayName("purge — 正常路径 → deleted + recycle_bin 均删除")
    void purge_normal_shouldCleanBoth() {
        Long recycleBinId = setupDeletedAndRecycleBin(888L, "待清理", LocalDateTime.now());

        strategy.purge(recycleBinId);
        entityManager.flush();
        entityManager.clear();

        assertThat(deletedJpaRepository.findByOriginalId(888L)).isEmpty();
        assertThat(recycleBinItemJpaRepository.findById(recycleBinId)).isEmpty();
    }

    @Test
    @DisplayName("purge — 回收站记录不存在 → BusinessException")
    void purge_recycleBinNotFound_shouldThrow() {
        assertThatThrownBy(() -> strategy.purge(9999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("回收站记录不存在: 9999");
    }

    @Test
    @DisplayName("purge — 主快照已被并发删除 → 静默跳过，仅删 recycle_bin")
    void purge_snapshotConcurrentlyDeleted_shouldSilentlySkip() {
        RecycleBinItemPO bin = persistRecycleBinItem(555L, "GhostQ");
        entityManager.flush();

        strategy.purge(bin.getId());
        entityManager.flush();

        assertThat(recycleBinItemJpaRepository.findById(bin.getId())).isEmpty();
    }

    // ============================================================
    // helpers
    // ============================================================

    private QuestionPO persistQuestion(String content) {
        QuestionPO po = QuestionPO.builder()
                .type(QuestionType.SINGLE_CHOICE)
                .content(content)
                .options("[{\"key\":\"A\",\"content\":\"选项A\"}]")
                .answer("\"A\"")
                .difficulty(Difficulty.EASY)
                .status(QuestionStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return questionJpaRepository.saveAndFlush(po);
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

    private void persistCategoryRelation(Long questionId, Long categoryId) {
        QuestionCategoryRelationPO po = QuestionCategoryRelationPO.builder()
                .questionId(questionId)
                .categoryId(categoryId)
                .build();
        relationJpaRepository.saveAndFlush(po);
    }

    private Long setupDeletedAndRecycleBin(Long originalId, String content,
                                            LocalDateTime createdAt) {
        return setupDeletedAndRecycleBinWithCategories(originalId, content, createdAt, List.of(originalId));
    }

    private Long setupDeletedAndRecycleBinWithCategories(Long originalId, String content,
                                                          LocalDateTime createdAt,
                                                          List<Long> categoryIds) {
        QuestionDeletedPO deletedPO = QuestionDeletedPO.builder()
                .originalId(originalId)
                .type(QuestionType.SINGLE_CHOICE)
                .content(content)
                .options("[{\"key\":\"A\",\"content\":\"选项A\"}]")
                .answer("\"A\"")
                .difficulty(Difficulty.EASY)
                .status(QuestionStatus.ACTIVE)
                .createdAt(createdAt)
                .updatedAt(LocalDateTime.now())
                .relatedData(QuestionRecycleBinStrategy.writeCategoryIds(categoryIds))
                .deletedBy("admin")
                .deletedAt(LocalDateTime.now())
                .build();
        deletedJpaRepository.saveAndFlush(deletedPO);

        RecycleBinItemPO binPO = RecycleBinItemPO.builder()
                .resourceType(ResourceType.QUESTION)
                .originalId(originalId)
                .originalName(content)
                .deletedBy("admin")
                .deletedAt(LocalDateTime.now())
                .restoreDeadline(LocalDateTime.now().plusDays(30))
                .build();
        binPO = recycleBinItemJpaRepository.saveAndFlush(binPO);
        return binPO.getId();
    }

    private RecycleBinItemPO persistRecycleBinItem(Long originalId, String originalName) {
        RecycleBinItemPO po = RecycleBinItemPO.builder()
                .resourceType(ResourceType.QUESTION)
                .originalId(originalId)
                .originalName(originalName)
                .deletedBy("admin")
                .deletedAt(LocalDateTime.now())
                .restoreDeadline(LocalDateTime.now().plusDays(30))
                .build();
        return recycleBinItemJpaRepository.saveAndFlush(po);
    }
}
