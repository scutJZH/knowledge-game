package com.knowledgegame.core.infrastructure.adapter.repoadapter;

import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.domainenum.KnowledgeCategoryStatus;
import com.knowledgegame.core.domain.model.domainenum.ResourceType;
import com.knowledgegame.core.domain.port.outbound.FileCleanupPort;
import com.knowledgegame.core.infrastructure.db.entity.KnowledgeCategoryDeletedPO;
import com.knowledgegame.core.infrastructure.db.entity.KnowledgeCategoryPO;
import com.knowledgegame.core.infrastructure.db.entity.RecycleBinItemPO;
import com.knowledgegame.core.infrastructure.db.repository.KnowledgeCategoryDeletedJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.KnowledgeCategoryJpaRepository;
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
@Import({KnowledgeCategoryRecycleBinStrategy.class,
        KnowledgeCategoryRepositoryAdapter.class,
        QuestionRepositoryAdapter.class,
        KnowledgeItemRepositoryAdapter.class,
        RecycleBinItemRepositoryAdapter.class})
@ActiveProfiles("test")
class KnowledgeCategoryRecycleBinStrategyTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private KnowledgeCategoryRecycleBinStrategy strategy;

    @Autowired
    private KnowledgeCategoryJpaRepository categoryJpaRepository;

    @Autowired
    private KnowledgeCategoryDeletedJpaRepository deletedJpaRepository;

    @Autowired
    private RecycleBinItemJpaRepository recycleBinItemJpaRepository;

    @MockBean
    private FileCleanupPort fileCleanupPort;

    @BeforeEach
    void setUp() {
        recycleBinItemJpaRepository.deleteAll();
        deletedJpaRepository.deleteAll();
        categoryJpaRepository.deleteAll();
        entityManager.flush();
    }

    // ============================================================
    // validateDeletable
    // ============================================================

    @Test
    @DisplayName("validateDeletable — 叶子节点无关联 → 正常返回")
    void validateDeletable_leafNoAssociation_shouldPass() {
        KnowledgeCategoryPO cat = persistCategory(null, "叶子分类");
        assertThatCode(() -> strategy.validateDeletable(cat.getId())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateDeletable — 父分类+子分类无关联 → 正常返回")
    void validateDeletable_treeNoAssociation_shouldPass() {
        KnowledgeCategoryPO parent = persistCategory(null, "父分类");
        persistCategory(parent.getId(), "子分类1");
        persistCategory(parent.getId(), "子分类2");
        assertThatCode(() -> strategy.validateDeletable(parent.getId())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateDeletable — 分类不存在 → BusinessException")
    void validateDeletable_notFound_shouldThrow() {
        assertThatThrownBy(() -> strategy.validateDeletable(9999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("知识点分类不存在: 9999");
    }

    // ============================================================
    // moveToRecycleBin
    // ============================================================

    @Test
    @DisplayName("完整删除链路：validateDeletable → moveToRecycleBin（模拟 AppService 调用）")
    void fullDeleteFlow_validateThenMove_shouldSucceed() {
        KnowledgeCategoryPO cat = persistCategory(null, "完整链路测试");
        Long originalId = cat.getId();

        // 模拟 AppService.delete() 的调用顺序
        assertThatCode(() -> strategy.validateDeletable(originalId)).doesNotThrowAnyException();
        strategy.moveToRecycleBin(originalId, "admin");

        entityManager.flush();
        entityManager.clear();

        assertThat(categoryJpaRepository.findById(originalId)).isEmpty();
        assertThat(deletedJpaRepository.findByOriginalId(originalId)).isPresent();
        assertThat(recycleBinItemJpaRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("完整删除链路：父+子 validateDeletable → moveToRecycleBin")
    void fullDeleteFlow_tree_validateThenMove_shouldSucceed() {
        KnowledgeCategoryPO parent = persistCategory(null, "父");
        persistCategory(parent.getId(), "子1");
        persistCategory(parent.getId(), "子2");

        assertThatCode(() -> strategy.validateDeletable(parent.getId())).doesNotThrowAnyException();
        strategy.moveToRecycleBin(parent.getId(), "admin");

        entityManager.flush();
        entityManager.clear();

        assertThat(categoryJpaRepository.count()).isEqualTo(0);
        assertThat(deletedJpaRepository.count()).isEqualTo(3);
        assertThat(recycleBinItemJpaRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("moveToRecycleBin — 叶子节点 → 删除1行 + 1条快照 + 1条总览记录")
    void moveToRecycleBin_leaf_shouldMoveToRecycleBin() {
        KnowledgeCategoryPO cat = persistCategory(null, "测试分类");
        Long originalId = cat.getId();

        strategy.moveToRecycleBin(originalId, "admin");
        entityManager.flush();
        entityManager.clear();

        assertThat(categoryJpaRepository.findById(originalId)).isEmpty();
        KnowledgeCategoryDeletedPO deletedPO = deletedJpaRepository.findByOriginalId(originalId).orElseThrow();
        assertThat(deletedPO.getName()).isEqualTo("测试分类");
        assertThat(deletedPO.getParentId()).isNull();
        assertThat(deletedPO.getDeletedBy()).isEqualTo("admin");
        RecycleBinItemPO binPO = recycleBinItemJpaRepository.findAll().get(0);
        assertThat(binPO.getResourceType()).isEqualTo(ResourceType.KNOWLEDGE_CATEGORY);
        assertThat(binPO.getOriginalName()).isEqualTo("测试分类");
    }

    @Test
    @DisplayName("moveToRecycleBin — 父+3子 → 删除4行 + 4条快照 + 1条总览 + 主快照 related_data 含4个ID")
    void moveToRecycleBin_parentWithChildren_shouldMoveAll() {
        KnowledgeCategoryPO parent = persistCategory(null, "根");
        persistCategory(parent.getId(), "子1");
        persistCategory(parent.getId(), "子2");
        persistCategory(parent.getId(), "子3");

        strategy.moveToRecycleBin(parent.getId(), "admin");
        entityManager.flush();
        entityManager.clear();

        assertThat(categoryJpaRepository.count()).isEqualTo(0);
        List<KnowledgeCategoryDeletedPO> all = deletedJpaRepository.findAll();
        assertThat(all).hasSize(4);
        KnowledgeCategoryDeletedPO rootPO = deletedJpaRepository.findByOriginalId(parent.getId()).orElseThrow();
        assertThat(rootPO.getRelatedData()).isNotNull();
        assertThat(rootPO.getRelatedData()).contains("subtreeOriginalIds");
        assertThat(recycleBinItemJpaRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("moveToRecycleBin — 分类不存在 → BusinessException")
    void moveToRecycleBin_notFound_shouldThrow() {
        assertThatThrownBy(() -> strategy.moveToRecycleBin(9999L, "admin"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("知识点分类不存在: 9999");
    }

    // ============================================================
    // restore
    // ============================================================

    @Test
    @DisplayName("restore — 叶子节点 → 行恢复(INACTIVE) + 快照删除 + 回收站删除")
    void restore_leaf_shouldRestoreRow() {
        LocalDateTime originalCreatedAt = LocalDateTime.of(2025, 6, 1, 10, 0);
        Long recycleBinId = setupDeletedAndRecycleBin("叶子", null, 100L, originalCreatedAt);

        strategy.restore(recycleBinId);
        entityManager.flush();
        entityManager.clear();

        KnowledgeCategoryPO restored = categoryJpaRepository.findById(100L).orElseThrow();
        assertThat(restored.getName()).isEqualTo("叶子");
        assertThat(restored.getStatus()).isEqualTo(KnowledgeCategoryStatus.INACTIVE);
        assertThat(restored.getCreatedAt()).isEqualTo(originalCreatedAt);
        assertThat(restored.getUpdatedAt()).isCloseTo(LocalDateTime.now(), within(5, java.time.temporal.ChronoUnit.SECONDS));
        assertThat(deletedJpaRepository.findByOriginalId(100L)).isEmpty();
        assertThat(recycleBinItemJpaRepository.findById(recycleBinId)).isEmpty();
    }

    @Test
    @DisplayName("restore — 父+2子 → 全部恢复，parentId 关系正确")
    void restore_tree_shouldRestoreStructure() {
        Long recycleBinId = setupDeletedTree();
        strategy.restore(recycleBinId);
        entityManager.flush();
        entityManager.clear();

        List<KnowledgeCategoryPO> all = categoryJpaRepository.findAll();
        assertThat(all).hasSize(3);
        KnowledgeCategoryPO parent = categoryJpaRepository.findById(200L).orElseThrow();
        assertThat(parent.getParentId()).isNull();
        assertThat(parent.getStatus()).isEqualTo(KnowledgeCategoryStatus.INACTIVE);
        KnowledgeCategoryPO child1 = categoryJpaRepository.findById(201L).orElseThrow();
        assertThat(child1.getParentId()).isEqualTo(200L);
        KnowledgeCategoryPO child2 = categoryJpaRepository.findById(202L).orElseThrow();
        assertThat(child2.getParentId()).isEqualTo(200L);
        assertThat(deletedJpaRepository.count()).isEqualTo(0);
        assertThat(recycleBinItemJpaRepository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("restore — 同名同父级冲突 → BusinessException")
    void restore_nameConflict_shouldThrow() {
        persistCategory(null, "冲突名称");
        Long recycleBinId = setupDeletedAndRecycleBin("冲突名称", null, 300L, LocalDateTime.now());

        assertThatThrownBy(() -> strategy.restore(recycleBinId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("目标父级下已存在同名分类: 冲突名称");
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
                .hasMessageContaining("分类快照不存在: 777");
    }

    @Test
    @DisplayName("restore — 快照无 related_data → 仅恢复自身")
    void restore_noRelatedData_shouldRestoreSelfOnly() {
        KnowledgeCategoryDeletedPO deletedPO = new KnowledgeCategoryDeletedPO();
        deletedPO.setOriginalId(500L);
        deletedPO.setName("无子节点分类");
        deletedPO.setParentId(null);
        deletedPO.setSortOrder(0);
        deletedPO.setStatus(KnowledgeCategoryStatus.ACTIVE);
        deletedPO.setCreatedAt(LocalDateTime.now());
        deletedPO.setUpdatedAt(LocalDateTime.now());
        deletedPO.setRelatedData(null);
        deletedPO.setDeletedBy("admin");
        deletedPO.setDeletedAt(LocalDateTime.now());
        deletedPO = deletedJpaRepository.saveAndFlush(deletedPO);

        RecycleBinItemPO bin = persistRecycleBinItem(500L, "无子节点分类");
        entityManager.flush();

        strategy.restore(bin.getId());
        entityManager.flush();

        assertThat(categoryJpaRepository.findById(500L)).isPresent();
    }

    // ============================================================
    // purge
    // ============================================================

    @Test
    @DisplayName("purge — 叶子节点无图 → deleted + recycle_bin 均删除")
    void purge_leafNoImage_shouldCleanBoth() {
        Long recycleBinId = setupDeletedAndRecycleBin("待清理", null, 888L, LocalDateTime.now());

        strategy.purge(recycleBinId);
        entityManager.flush();
        entityManager.clear();

        assertThat(deletedJpaRepository.findByOriginalId(888L)).isEmpty();
        assertThat(recycleBinItemJpaRepository.findById(recycleBinId)).isEmpty();
    }

    @Test
    @DisplayName("purge — 有图标和封面图 → 调 fileCleanupPort 各一次，清理 DB")
    void purge_withImages_shouldCallFileCleanupThenClean() {
        KnowledgeCategoryDeletedPO deletedPO = new KnowledgeCategoryDeletedPO();
        deletedPO.setOriginalId(999L);
        deletedPO.setName("有图分类");
        deletedPO.setParentId(null);
        deletedPO.setIconFileId(11L);
        deletedPO.setCoverImageFileId(22L);
        deletedPO.setSortOrder(0);
        deletedPO.setStatus(KnowledgeCategoryStatus.ACTIVE);
        deletedPO.setCreatedAt(LocalDateTime.now());
        deletedPO.setUpdatedAt(LocalDateTime.now());
        deletedPO.setRelatedData(writeSubtreeIds(List.of(999L)));
        deletedPO.setDeletedBy("admin");
        deletedPO.setDeletedAt(LocalDateTime.now());
        deletedPO = deletedJpaRepository.saveAndFlush(deletedPO);

        RecycleBinItemPO bin = persistRecycleBinItem(999L, "有图分类");
        entityManager.flush();

        strategy.purge(bin.getId());

        verify(fileCleanupPort).deleteFile(11L);
        verify(fileCleanupPort).deleteFile(22L);
        entityManager.flush();
        entityManager.clear();

        assertThat(deletedJpaRepository.findByOriginalId(999L)).isEmpty();
        assertThat(recycleBinItemJpaRepository.findById(bin.getId())).isEmpty();
    }

    @Test
    @DisplayName("purge — FileCleanupPort 抛异常 → 仅 log.warn，DB 清理不受影响")
    void purge_fileCleanupThrows_shouldStillCleanDb() {
        KnowledgeCategoryDeletedPO deletedPO = new KnowledgeCategoryDeletedPO();
        deletedPO.setOriginalId(777L);
        deletedPO.setName("异常分类");
        deletedPO.setParentId(null);
        deletedPO.setIconFileId(99L);
        deletedPO.setSortOrder(0);
        deletedPO.setStatus(KnowledgeCategoryStatus.ACTIVE);
        deletedPO.setCreatedAt(LocalDateTime.now());
        deletedPO.setUpdatedAt(LocalDateTime.now());
        deletedPO.setRelatedData(writeSubtreeIds(List.of(777L)));
        deletedPO.setDeletedBy("admin");
        deletedPO.setDeletedAt(LocalDateTime.now());
        deletedPO = deletedJpaRepository.saveAndFlush(deletedPO);

        RecycleBinItemPO bin = persistRecycleBinItem(777L, "异常分类");
        entityManager.flush();

        doThrow(new RuntimeException("file service down")).when(fileCleanupPort).deleteFile(any());

        strategy.purge(bin.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(deletedJpaRepository.findByOriginalId(777L)).isEmpty();
        assertThat(recycleBinItemJpaRepository.findById(bin.getId())).isEmpty();
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
        RecycleBinItemPO bin = persistRecycleBinItem(555L, "GhostCat");
        entityManager.flush();

        strategy.purge(bin.getId());
        entityManager.flush();

        verifyNoInteractions(fileCleanupPort);
        assertThat(recycleBinItemJpaRepository.findById(bin.getId())).isEmpty();
    }

    // ============================================================
    // helpers
    // ============================================================

    private KnowledgeCategoryPO persistCategory(Long parentId, String name) {
        KnowledgeCategoryPO po = KnowledgeCategoryPO.builder()
                .parentId(parentId).name(name)
                .sortOrder(0).status(KnowledgeCategoryStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        return categoryJpaRepository.saveAndFlush(po);
    }

    private Long setupDeletedAndRecycleBin(String name, Long parentId, Long originalId,
                                            LocalDateTime createdAt) {
        return setupDeletedAndRecycleBinWithRelData(name, parentId, originalId, createdAt,
                writeSubtreeIds(List.of(originalId)));
    }

    private Long setupDeletedAndRecycleBinWithRelData(String name, Long parentId, Long originalId,
                                                       LocalDateTime createdAt, String relatedData) {
        KnowledgeCategoryDeletedPO deletedPO = KnowledgeCategoryDeletedPO.builder()
                .originalId(originalId).parentId(parentId).name(name)
                .sortOrder(0).status(KnowledgeCategoryStatus.ACTIVE)
                .createdAt(createdAt).updatedAt(LocalDateTime.now())
                .relatedData(relatedData)
                .deletedBy("admin").deletedAt(LocalDateTime.now())
                .build();
        deletedJpaRepository.saveAndFlush(deletedPO);

        RecycleBinItemPO binPO = RecycleBinItemPO.builder()
                .resourceType(ResourceType.KNOWLEDGE_CATEGORY).originalId(originalId)
                .originalName(name).deletedBy("admin")
                .deletedAt(LocalDateTime.now()).restoreDeadline(LocalDateTime.now().plusDays(30))
                .build();
        binPO = recycleBinItemJpaRepository.saveAndFlush(binPO);
        return binPO.getId();
    }

    private Long setupDeletedTree() {
        List<Long> subtreeIds = List.of(200L, 201L, 202L);
        String relatedData = writeSubtreeIds(subtreeIds);

        // 父节点
        KnowledgeCategoryDeletedPO parent = KnowledgeCategoryDeletedPO.builder()
                .originalId(200L).parentId(null).name("父")
                .sortOrder(0).status(KnowledgeCategoryStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .relatedData(relatedData)
                .deletedBy("admin").deletedAt(LocalDateTime.now())
                .build();
        deletedJpaRepository.save(parent);

        // 子节点 1
        KnowledgeCategoryDeletedPO child1 = KnowledgeCategoryDeletedPO.builder()
                .originalId(201L).parentId(200L).name("子1")
                .sortOrder(0).status(KnowledgeCategoryStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .relatedData(null)
                .deletedBy("admin").deletedAt(LocalDateTime.now())
                .build();
        deletedJpaRepository.save(child1);

        // 子节点 2
        KnowledgeCategoryDeletedPO child2 = KnowledgeCategoryDeletedPO.builder()
                .originalId(202L).parentId(200L).name("子2")
                .sortOrder(0).status(KnowledgeCategoryStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .relatedData(null)
                .deletedBy("admin").deletedAt(LocalDateTime.now())
                .build();
        deletedJpaRepository.saveAndFlush(child2);

        RecycleBinItemPO binPO = RecycleBinItemPO.builder()
                .resourceType(ResourceType.KNOWLEDGE_CATEGORY).originalId(200L)
                .originalName("父").deletedBy("admin")
                .deletedAt(LocalDateTime.now()).restoreDeadline(LocalDateTime.now().plusDays(30))
                .build();
        binPO = recycleBinItemJpaRepository.saveAndFlush(binPO);
        return binPO.getId();
    }

    private RecycleBinItemPO persistRecycleBinItem(Long originalId, String originalName) {
        RecycleBinItemPO po = RecycleBinItemPO.builder()
                .resourceType(ResourceType.KNOWLEDGE_CATEGORY).originalId(originalId)
                .originalName(originalName).deletedBy("admin")
                .deletedAt(LocalDateTime.now()).restoreDeadline(LocalDateTime.now().plusDays(30))
                .build();
        return recycleBinItemJpaRepository.saveAndFlush(po);
    }

    private static String writeSubtreeIds(List<Long> ids) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(java.util.Collections.singletonMap("subtreeOriginalIds", ids));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
