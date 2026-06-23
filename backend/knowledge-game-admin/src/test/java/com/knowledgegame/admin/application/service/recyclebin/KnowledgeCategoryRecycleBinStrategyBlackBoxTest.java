package com.knowledgegame.admin.application.service.recyclebin;

import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.domainenum.KnowledgeCategoryStatus;
import com.knowledgegame.core.domain.model.domainenum.ResourceType;
import com.knowledgegame.core.domain.model.entity.KnowledgeCategory;
import com.knowledgegame.core.domain.model.entity.RecycleBinItem;
import com.knowledgegame.core.domain.port.outbound.FileCleanupPort;
import com.knowledgegame.core.domain.port.outbound.KnowledgeCategoryRepositoryPort;
import com.knowledgegame.core.domain.port.outbound.KnowledgeItemRepository;
import com.knowledgegame.core.domain.port.outbound.QuestionRepository;
import com.knowledgegame.core.domain.port.outbound.RecycleBinItemRepositoryPort;
import com.knowledgegame.core.infrastructure.adapter.repoadapter.KnowledgeCategoryRecycleBinStrategy;
import com.knowledgegame.core.infrastructure.db.entity.KnowledgeCategoryDeletedPO;
import com.knowledgegame.core.infrastructure.db.repository.KnowledgeCategoryDeletedJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.KnowledgeCategoryJpaRepository;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KnowledgeCategoryRecycleBinStrategyBlackBoxTest {

    @Mock
    private KnowledgeCategoryRepositoryPort categoryRepositoryPort;
    @Mock
    private QuestionRepository questionRepository;
    @Mock
    private KnowledgeItemRepository itemRepository;
    @Mock
    private RecycleBinItemRepositoryPort recycleBinItemRepositoryPort;
    @Mock
    private KnowledgeCategoryJpaRepository categoryJpaRepository;
    @Mock
    private KnowledgeCategoryDeletedJpaRepository deletedJpaRepository;
    @Mock
    private RecycleBinItemJpaRepository recycleBinItemJpaRepository;
    @Mock
    private FileCleanupPort fileCleanupPort;
    @Mock
    private EntityManager entityManager;
    @Mock
    private Query nativeQuery;

    @InjectMocks
    private KnowledgeCategoryRecycleBinStrategy strategy;

    private static final LocalDateTime NOW = LocalDateTime.now();

    @BeforeEach
    void setUp() {
        strategy.setEntityManager(entityManager);
    }

    private void stubNativeInsert() {
        when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
        when(nativeQuery.setParameter(any(int.class), any())).thenReturn(nativeQuery);
        when(nativeQuery.executeUpdate()).thenReturn(1);
    }

    // ============================================================
    // restore status 强制 INACTIVE
    // ============================================================

    @Test
    @DisplayName("restore — status 强制 INACTIVE，无论删除前是 ACTIVE")
    void restore_shouldForceInactive() {
        stubNativeInsert();
        long recycleBinId = 1L;
        long originalId = 100L;

        RecycleBinItem recycleBinItem = new RecycleBinItem(recycleBinId, ResourceType.KNOWLEDGE_CATEGORY,
                originalId, "测试分类", null, null, null, null, "admin", NOW, NOW.plusDays(30));
        when(recycleBinItemRepositoryPort.findById(recycleBinId)).thenReturn(Optional.of(recycleBinItem));

        KnowledgeCategoryDeletedPO deletedPO = buildDeleted(originalId, "测试分类", null,
                KnowledgeCategoryStatus.ACTIVE, writeSubtreeIds(List.of(originalId)));
        when(deletedJpaRepository.findByOriginalId(originalId)).thenReturn(Optional.of(deletedPO));
        when(deletedJpaRepository.findAllByOriginalIdIn(any())).thenReturn(List.of(deletedPO));
        when(categoryRepositoryPort.existsByNameAndParentId(anyString(), any())).thenReturn(false);

        strategy.restore(recycleBinId);

        ArgumentCaptor<Object> statusCaptor = ArgumentCaptor.forClass(Object.class);
        verify(nativeQuery).setParameter(eq(11), statusCaptor.capture());
        assertThat(statusCaptor.getValue()).hasToString("INACTIVE");

        verify(deletedJpaRepository).deleteAllById(any());
        verify(recycleBinItemJpaRepository).deleteById(recycleBinId);
    }

    // ============================================================
    // 拓扑排序
    // ============================================================

    @Test
    @DisplayName("restore — 按 parentId 拓扑排序：父先于子插入")
    void restore_shouldTopologicalSortParentFirst() {
        stubNativeInsert();
        long recycleBinId = 2L;
        long parentId = 200L;
        long childId = 201L;

        RecycleBinItem recycleBinItem = new RecycleBinItem(recycleBinId, ResourceType.KNOWLEDGE_CATEGORY,
                parentId, "父分类", null, null, null, null, "admin", NOW, NOW.plusDays(30));
        when(recycleBinItemRepositoryPort.findById(recycleBinId)).thenReturn(Optional.of(recycleBinItem));

        KnowledgeCategoryDeletedPO parent = buildDeleted(parentId, "父分类", null,
                KnowledgeCategoryStatus.ACTIVE, writeSubtreeIds(List.of(parentId, childId)));
        KnowledgeCategoryDeletedPO child = buildDeleted(childId, "子分类", parentId,
                KnowledgeCategoryStatus.ACTIVE, null);
        when(deletedJpaRepository.findByOriginalId(parentId)).thenReturn(Optional.of(parent));
        when(deletedJpaRepository.findAllByOriginalIdIn(any())).thenReturn(List.of(parent, child));
        when(categoryRepositoryPort.existsByNameAndParentId(anyString(), any())).thenReturn(false);

        strategy.restore(recycleBinId);

        // 验证父先于子插入
        ArgumentCaptor<Object> idCaptor = ArgumentCaptor.forClass(Object.class);
        verify(nativeQuery, times(2)).setParameter(eq(1), idCaptor.capture());
        List<Object> ids = idCaptor.getAllValues();
        assertThat(ids.get(0)).isEqualTo(parentId);
        assertThat(ids.get(1)).isEqualTo(childId);
    }

    // ============================================================
    // 并发删除静默跳过
    // ============================================================

    @Test
    @DisplayName("purge — 快照已被并发删除 → 静默跳过，仅删 recycle_bin")
    void purge_snapshotConcurrentlyDeleted_shouldSilentlySkip() {
        long recycleBinId = 3L;
        long originalId = 400L;

        RecycleBinItem recycleBinItem = new RecycleBinItem(recycleBinId, ResourceType.KNOWLEDGE_CATEGORY,
                originalId, "并发分类", null, null, null, null, "admin", NOW, NOW.plusDays(30));
        when(recycleBinItemRepositoryPort.findById(recycleBinId)).thenReturn(Optional.of(recycleBinItem));
        when(deletedJpaRepository.findByOriginalId(originalId)).thenReturn(Optional.empty());

        strategy.purge(recycleBinId);

        verify(fileCleanupPort, never()).deleteFile(anyLong());
        verify(deletedJpaRepository, never()).deleteAllById(any());
        verify(recycleBinItemJpaRepository).deleteById(recycleBinId);
    }

    @Test
    @DisplayName("purge — 快照存在无图片 → 不调 FileCleanupPort，清理 DB")
    void purge_deletedExistsNoImage_shouldNotCallFileCleanup() {
        long recycleBinId = 4L;
        long originalId = 500L;

        RecycleBinItem recycleBinItem = new RecycleBinItem(recycleBinId, ResourceType.KNOWLEDGE_CATEGORY,
                originalId, "无图分类", null, null, null, null, "admin", NOW, NOW.plusDays(30));
        when(recycleBinItemRepositoryPort.findById(recycleBinId)).thenReturn(Optional.of(recycleBinItem));

        KnowledgeCategoryDeletedPO deletedPO = buildDeleted(originalId, "无图分类", null,
                KnowledgeCategoryStatus.ACTIVE, writeSubtreeIds(List.of(originalId)));
        when(deletedJpaRepository.findByOriginalId(originalId)).thenReturn(Optional.of(deletedPO));
        when(deletedJpaRepository.findAllByOriginalIdIn(any())).thenReturn(List.of(deletedPO));

        strategy.purge(recycleBinId);

        verify(fileCleanupPort, never()).deleteFile(anyLong());
        verify(deletedJpaRepository).deleteAllById(any());
        verify(recycleBinItemJpaRepository).deleteById(recycleBinId);
    }

    // ============================================================
    // 循环删除恢复
    // ============================================================

    @Test
    @DisplayName("循环操作 — moveToRecycleBin → restore → 再 moveToRecycleBin → restore 成功")
    void cycleDeleteRestoreTwice_shouldSucceed() {
        stubNativeInsert();
        long originalId = 300L;
        long recycleBinId1 = 10L;
        long recycleBinId2 = 11L;

        KnowledgeCategory category = KnowledgeCategory.reconstruct(originalId, null, "循环分类",
                "", null, null, null, 0, KnowledgeCategoryStatus.ACTIVE,
                LocalDateTime.of(2025, 6, 1, 10, 0), NOW);
        when(categoryRepositoryPort.findById(originalId)).thenReturn(Optional.of(category));
        when(categoryRepositoryPort.findDescendantIds(originalId)).thenReturn(Collections.emptyList());
        when(categoryRepositoryPort.findAllByIdIn(any())).thenReturn(List.of(category));

        // 第一次删除
        strategy.moveToRecycleBin(originalId, "admin");
        verify(deletedJpaRepository).saveAll(any());
        verify(categoryJpaRepository).deleteAllById(any());
        verify(recycleBinItemJpaRepository).save(any());

        // 第一次恢复
        RecycleBinItem recycleBinItem1 = new RecycleBinItem(recycleBinId1, ResourceType.KNOWLEDGE_CATEGORY,
                originalId, "循环分类", null, null, null, null, "admin", NOW, NOW.plusDays(30));
        when(recycleBinItemRepositoryPort.findById(recycleBinId1)).thenReturn(Optional.of(recycleBinItem1));
        KnowledgeCategoryDeletedPO deletedPO1 = buildDeleted(originalId, "循环分类", null,
                KnowledgeCategoryStatus.ACTIVE, writeSubtreeIds(List.of(originalId)));
        when(deletedJpaRepository.findByOriginalId(originalId)).thenReturn(Optional.of(deletedPO1));
        when(deletedJpaRepository.findAllByOriginalIdIn(any())).thenReturn(List.of(deletedPO1));
        when(categoryRepositoryPort.existsByNameAndParentId(anyString(), any())).thenReturn(false);

        strategy.restore(recycleBinId1);
        verify(deletedJpaRepository).deleteAllById(any());
        verify(recycleBinItemJpaRepository).deleteById(recycleBinId1);

        // 第二次删除
        KnowledgeCategory category2 = KnowledgeCategory.reconstruct(originalId, null, "循环分类",
                "", null, null, null, 0, KnowledgeCategoryStatus.INACTIVE,
                LocalDateTime.of(2025, 6, 1, 10, 0), NOW);
        when(categoryRepositoryPort.findById(originalId)).thenReturn(Optional.of(category2));

        strategy.moveToRecycleBin(originalId, "admin");

        // 第二次恢复
        RecycleBinItem recycleBinItem2 = new RecycleBinItem(recycleBinId2, ResourceType.KNOWLEDGE_CATEGORY,
                originalId, "循环分类", null, null, null, null, "admin", NOW, NOW.plusDays(30));
        when(recycleBinItemRepositoryPort.findById(recycleBinId2)).thenReturn(Optional.of(recycleBinItem2));
        KnowledgeCategoryDeletedPO deletedPO2 = buildDeleted(originalId, "循环分类", null,
                KnowledgeCategoryStatus.INACTIVE, writeSubtreeIds(List.of(originalId)));
        when(deletedJpaRepository.findByOriginalId(originalId)).thenReturn(Optional.of(deletedPO2));
        when(deletedJpaRepository.findAllByOriginalIdIn(any())).thenReturn(List.of(deletedPO2));

        assertThatCode(() -> strategy.restore(recycleBinId2)).doesNotThrowAnyException();
    }

    // ============================================================
    // helpers
    // ============================================================

    private static KnowledgeCategoryDeletedPO buildDeleted(Long originalId, String name, Long parentId,
                                                            KnowledgeCategoryStatus status,
                                                            String relatedData) {
        KnowledgeCategoryDeletedPO po = new KnowledgeCategoryDeletedPO();
        po.setId(originalId + 1000);
        po.setOriginalId(originalId);
        po.setParentId(parentId);
        po.setName(name);
        po.setSortOrder(0);
        po.setStatus(status);
        po.setCreatedAt(LocalDateTime.of(2025, 6, 1, 10, 0));
        po.setUpdatedAt(NOW);
        po.setRelatedData(relatedData);
        po.setDeletedBy("admin");
        po.setDeletedAt(NOW);
        return po;
    }

    private static String writeSubtreeIds(List<Long> ids) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(Collections.singletonMap("subtreeOriginalIds", ids));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
