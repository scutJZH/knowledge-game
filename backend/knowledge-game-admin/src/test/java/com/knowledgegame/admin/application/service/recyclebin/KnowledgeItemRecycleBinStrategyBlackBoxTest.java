package com.knowledgegame.admin.application.service.recyclebin;

import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.domainenum.KnowledgeItemStatus;
import com.knowledgegame.core.domain.model.domainenum.ResourceType;
import com.knowledgegame.core.domain.model.entity.KnowledgeItem;
import com.knowledgegame.core.domain.model.entity.RecycleBinItem;
import com.knowledgegame.core.domain.model.vo.FileRef;
import com.knowledgegame.core.domain.port.outbound.FileCleanupPort;
import com.knowledgegame.core.domain.port.outbound.KnowledgeItemRepository;
import com.knowledgegame.core.domain.port.outbound.RecycleBinItemRepositoryPort;
import com.knowledgegame.core.infrastructure.adapter.repoadapter.KnowledgeItemRecycleBinStrategy;
import com.knowledgegame.core.infrastructure.db.entity.KnowledgeCategoryPO;
import com.knowledgegame.core.infrastructure.db.entity.KnowledgeItemCategoryRelationPO;
import com.knowledgegame.core.infrastructure.db.entity.KnowledgeItemDeletedPO;
import com.knowledgegame.core.infrastructure.db.repository.KnowledgeCategoryJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.KnowledgeItemCategoryRelationJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.KnowledgeItemDeletedJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.KnowledgeItemJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.RecycleBinItemJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeItemRecycleBinStrategyBlackBoxTest {

    @Mock private KnowledgeItemRepository itemRepository;
    @Mock private RecycleBinItemRepositoryPort recycleBinItemRepositoryPort;
    @Mock private KnowledgeItemJpaRepository itemJpaRepository;
    @Mock private KnowledgeItemDeletedJpaRepository itemDeletedJpaRepository;
    @Mock private KnowledgeItemCategoryRelationJpaRepository relationJpaRepository;
    @Mock private RecycleBinItemJpaRepository recycleBinItemJpaRepository;
    @Mock private KnowledgeCategoryJpaRepository categoryJpaRepository;
    @Mock private FileCleanupPort fileCleanupPort;
    @Mock private EntityManager entityManager;
    @Mock private Query query;

    @InjectMocks
    private KnowledgeItemRecycleBinStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy.setEntityManager(entityManager);
        lenient().when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        lenient().when(query.setParameter(any(Integer.class), any())).thenReturn(query);
        lenient().when(query.executeUpdate()).thenReturn(1);
    }

    private RecycleBinItem mockBinItem(Long id, Long originalId, String originalName) {
        return new RecycleBinItem(id, ResourceType.KNOWLEDGE_ITEM, originalId, originalName,
                LocalDateTime.now(), LocalDateTime.now(), null, null,
                "admin", LocalDateTime.now(), LocalDateTime.now().plusDays(30));
    }

    // ============================================================
    // restore — INACTIVE 强制
    // ============================================================

    @Test
    @DisplayName("restore — 删除前 ACTIVE → 恢复后强制 INACTIVE")
    void restore_originalActive_shouldRestoreAsInactive() {
        RecycleBinItem binItem = mockBinItem(1L, 100L, "知识条目");
        KnowledgeItemDeletedPO deletedPO = buildDeletedPO(100L, KnowledgeItemStatus.ACTIVE);

        lenient().when(recycleBinItemRepositoryPort.findById(1L)).thenReturn(Optional.of(binItem));
        lenient().when(itemDeletedJpaRepository.findByOriginalId(100L)).thenReturn(Optional.of(deletedPO));

        strategy.restore(1L);

        ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
        verify(query).setParameter(eq(9), statusCaptor.capture());
        assertThat(statusCaptor.getValue()).isEqualTo(KnowledgeItemStatus.INACTIVE.name());
    }

    @Test
    @DisplayName("restore — 删除前 INACTIVE → 恢复后仍 INACTIVE")
    void restore_originalInactive_shouldRestoreAsInactive() {
        RecycleBinItem binItem = mockBinItem(2L, 200L, "已停用条目");
        KnowledgeItemDeletedPO deletedPO = buildDeletedPO(200L, KnowledgeItemStatus.INACTIVE);

        lenient().when(recycleBinItemRepositoryPort.findById(2L)).thenReturn(Optional.of(binItem));
        lenient().when(itemDeletedJpaRepository.findByOriginalId(200L)).thenReturn(Optional.of(deletedPO));

        strategy.restore(2L);

        ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
        verify(query).setParameter(eq(9), statusCaptor.capture());
        assertThat(statusCaptor.getValue()).isEqualTo(KnowledgeItemStatus.INACTIVE.name());
    }

    // ============================================================
    // restore — 分类校验失败
    // ============================================================

    @Test
    @DisplayName("restore — 关联分类已被删除 → BusinessException（含缺失分类名）")
    void restore_categoriesDeleted_shouldThrow() {
        RecycleBinItem binItem = mockBinItem(3L, 300L, "有分类");
        KnowledgeItemDeletedPO deletedPO = buildDeletedPOWithRelatedData(300L,
                KnowledgeItemRecycleBinStrategy.writeCategoryIds(List.of(1L, 2L), Map.of(1L, "分类A")));

        lenient().when(recycleBinItemRepositoryPort.findById(3L)).thenReturn(Optional.of(binItem));
        lenient().when(itemDeletedJpaRepository.findByOriginalId(300L)).thenReturn(Optional.of(deletedPO));
        KnowledgeCategoryPO cat1 = buildCategoryPO(1L, "分类A");
        lenient().when(categoryJpaRepository.findAll()).thenReturn(List.of(cat1));
        lenient().when(categoryJpaRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(cat1));

        assertThatThrownBy(() -> strategy.restore(3L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("知识条目关联的分类已被删除，无法恢复。缺失分类: #2");
    }

    // ============================================================
    // restore — saveCategoryRelations 调用契约
    // ============================================================

    @Test
    @DisplayName("restore — 分类校验通过 → 调 saveCategoryRelations")
    void restore_withCategories_shouldCallSaveCategoryRelations() {
        RecycleBinItem binItem = mockBinItem(4L, 400L, "有分类");
        KnowledgeItemDeletedPO deletedPO = buildDeletedPOWithRelatedData(400L,
                KnowledgeItemRecycleBinStrategy.writeCategoryIds(List.of(1L, 2L, 3L),
                        Map.of(1L, "A", 2L, "B", 3L, "C")));

        lenient().when(recycleBinItemRepositoryPort.findById(4L)).thenReturn(Optional.of(binItem));
        lenient().when(itemDeletedJpaRepository.findByOriginalId(400L)).thenReturn(Optional.of(deletedPO));
        List<KnowledgeCategoryPO> allCats = List.of(
                buildCategoryPO(1L, "A"), buildCategoryPO(2L, "B"), buildCategoryPO(3L, "C"));
        lenient().when(categoryJpaRepository.findAll()).thenReturn(allCats);
        lenient().when(categoryJpaRepository.findAllById(List.of(1L, 2L, 3L))).thenReturn(allCats);

        strategy.restore(4L);

        verify(itemRepository).saveCategoryRelations(eq(400L), eq(List.of(1L, 2L, 3L)));
    }

    // ============================================================
    // purge — 并发删快照静默跳过
    // ============================================================

    @Test
    @DisplayName("purge — 快照已被并发删除 → 仅删 recycle_bin")
    void purge_snapshotConcurrentlyDeleted_shouldSilentlySkip() {
        RecycleBinItem binItem = mockBinItem(5L, 500L, "已删快照");

        lenient().when(recycleBinItemRepositoryPort.findById(5L)).thenReturn(Optional.of(binItem));
        lenient().when(itemDeletedJpaRepository.findByOriginalId(500L)).thenReturn(Optional.empty());

        strategy.purge(5L);

        verify(recycleBinItemJpaRepository).deleteById(5L);
        verify(itemDeletedJpaRepository, never()).deleteById(anyLong());
    }

    // ============================================================
    // 循环删除恢复
    // ============================================================

    @Test
    @DisplayName("循环：delete → restore → delete → restore 可重复执行")
    void cycleDeleteRestoreTwice_shouldSucceed() {
        KnowledgeItem item = KnowledgeItem.reconstruct(600L, "循环条目", "正文",
                "<p>正文</p>", null, List.of("Java"), 0,
                KnowledgeItemStatus.ACTIVE,
                LocalDateTime.now(), LocalDateTime.now());
        KnowledgeItemDeletedPO deletedPO = buildDeletedPO(600L, KnowledgeItemStatus.ACTIVE);
        RecycleBinItem binItem = mockBinItem(1L, 600L, "循环条目");

        lenient().when(itemRepository.findById(600L)).thenReturn(Optional.of(item));
        lenient().when(relationJpaRepository.findByItemId(600L)).thenReturn(List.of());
        lenient().when(recycleBinItemRepositoryPort.findById(1L)).thenReturn(Optional.of(binItem));
        lenient().when(itemDeletedJpaRepository.findByOriginalId(600L)).thenReturn(Optional.of(deletedPO));

        // round 1
        strategy.moveToRecycleBin(600L, "admin");
        strategy.restore(1L);

        // round 2 — must not throw
        strategy.moveToRecycleBin(600L, "admin");
        strategy.restore(1L);
    }

    // helpers

    private KnowledgeItemDeletedPO buildDeletedPO(Long originalId, KnowledgeItemStatus status) {
        return KnowledgeItemDeletedPO.builder()
                .originalId(originalId).title("测试条目").content("正文")
                .contentHtml("<p>正文</p>").coverImageFileId(null).coverImageUrl(null)
                .tags("[\"Java\"]").sortOrder(0).status(status)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .relatedData(null).deletedBy("admin").deletedAt(LocalDateTime.now()).build();
    }

    private KnowledgeItemDeletedPO buildDeletedPOWithRelatedData(Long originalId, String relatedData) {
        return KnowledgeItemDeletedPO.builder()
                .originalId(originalId).title("测试条目").content("正文")
                .contentHtml("<p>正文</p>").coverImageFileId(null).coverImageUrl(null)
                .tags("[\"Java\"]").sortOrder(0).status(KnowledgeItemStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .relatedData(relatedData).deletedBy("admin").deletedAt(LocalDateTime.now()).build();
    }

    private KnowledgeCategoryPO buildCategoryPO(Long id, String name) {
        return KnowledgeCategoryPO.builder().id(id).name(name)
                .sortOrder(0).status(com.knowledgegame.core.domain.model.domainenum.KnowledgeCategoryStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
    }
}
