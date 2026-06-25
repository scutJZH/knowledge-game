package com.knowledgegame.admin.application.service.recyclebin;

import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.domainenum.Difficulty;
import com.knowledgegame.core.domain.model.domainenum.QuestionStatus;
import com.knowledgegame.core.domain.model.domainenum.QuestionType;
import com.knowledgegame.core.domain.model.domainenum.ResourceType;
import com.knowledgegame.core.domain.model.entity.Question;
import com.knowledgegame.core.domain.model.entity.RecycleBinItem;
import com.knowledgegame.core.domain.port.outbound.QuestionRepository;
import com.knowledgegame.core.domain.port.outbound.RecycleBinItemRepositoryPort;
import com.knowledgegame.core.infrastructure.adapter.repoadapter.QuestionRecycleBinStrategy;
import com.knowledgegame.core.infrastructure.db.entity.QuestionDeletedPO;
import com.knowledgegame.core.infrastructure.db.repository.QuestionCategoryRelationJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.QuestionDeletedJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.QuestionJpaRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
class QuestionRecycleBinStrategyBlackBoxTest {

    @Mock private QuestionRepository questionRepository;
    @Mock private RecycleBinItemRepositoryPort recycleBinItemRepositoryPort;
    @Mock private QuestionJpaRepository questionJpaRepository;
    @Mock private QuestionDeletedJpaRepository questionDeletedJpaRepository;
    @Mock private QuestionCategoryRelationJpaRepository relationJpaRepository;
    @Mock private RecycleBinItemJpaRepository recycleBinItemJpaRepository;
    @Mock private EntityManager entityManager;
    @Mock private Query query;

    @InjectMocks
    private QuestionRecycleBinStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy.setEntityManager(entityManager);
        lenient().when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        lenient().when(query.setParameter(any(Integer.class), any())).thenReturn(query);
        lenient().when(query.executeUpdate()).thenReturn(1);
    }

    private RecycleBinItem mockBinItem(Long id, Long originalId, String originalName) {
        return new RecycleBinItem(id, ResourceType.QUESTION, originalId, originalName,
                LocalDateTime.now(), LocalDateTime.now(), null, null,
                "admin", LocalDateTime.now(), LocalDateTime.now().plusDays(30));
    }

    // ============================================================
    // restore — INACTIVE
    // ============================================================

    @Test
    @DisplayName("restore — 删除前 ACTIVE → 恢复后强制 INACTIVE")
    void restore_originalActive_shouldRestoreAsInactive() {
        RecycleBinItem binItem = mockBinItem(1L, 100L, "题目");
        QuestionDeletedPO deletedPO = buildDeletedPO(100L, QuestionStatus.ACTIVE);

        lenient().when(recycleBinItemRepositoryPort.findById(1L)).thenReturn(Optional.of(binItem));
        lenient().when(questionDeletedJpaRepository.findByOriginalId(100L)).thenReturn(Optional.of(deletedPO));

        strategy.restore(1L);

        ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
        verify(query).setParameter(eq(9), statusCaptor.capture());
        assertThat(statusCaptor.getValue()).isEqualTo(QuestionStatus.INACTIVE.name());
    }

    @Test
    @DisplayName("restore — 删除前 INACTIVE → 恢复后仍 INACTIVE")
    void restore_originalInactive_shouldRestoreAsInactive() {
        RecycleBinItem binItem = mockBinItem(2L, 200L, "已停用");
        QuestionDeletedPO deletedPO = buildDeletedPO(200L, QuestionStatus.INACTIVE);

        lenient().when(recycleBinItemRepositoryPort.findById(2L)).thenReturn(Optional.of(binItem));
        lenient().when(questionDeletedJpaRepository.findByOriginalId(200L)).thenReturn(Optional.of(deletedPO));

        strategy.restore(2L);

        ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
        verify(query).setParameter(eq(9), statusCaptor.capture());
        assertThat(statusCaptor.getValue()).isEqualTo(QuestionStatus.INACTIVE.name());
    }

    // ============================================================
    // restore — 分类关联恢复
    // ============================================================

    @Test
    @DisplayName("restore — 有分类关联 → 调 saveCategoryRelations")
    void restore_withCategories_shouldCallSaveCategoryRelations() {
        RecycleBinItem binItem = mockBinItem(3L, 300L, "有分类");
        QuestionDeletedPO deletedPO = buildDeletedPOWithRelatedData(300L,
                QuestionRecycleBinStrategy.writeCategoryIds(List.of(1L, 2L, 3L)));

        lenient().when(recycleBinItemRepositoryPort.findById(3L)).thenReturn(Optional.of(binItem));
        lenient().when(questionDeletedJpaRepository.findByOriginalId(300L)).thenReturn(Optional.of(deletedPO));

        strategy.restore(3L);

        verify(questionRepository).saveCategoryRelations(eq(300L), eq(List.of(1L, 2L, 3L)));
    }

    // ============================================================
    // purge — 并发删除
    // ============================================================

    @Test
    @DisplayName("purge — 快照已被并发删除 → 仅删 recycle_bin")
    void purge_snapshotConcurrentlyDeleted_shouldSilentlySkip() {
        RecycleBinItem binItem = mockBinItem(4L, 400L, "已删快照");

        lenient().when(recycleBinItemRepositoryPort.findById(4L)).thenReturn(Optional.of(binItem));
        lenient().when(questionDeletedJpaRepository.findByOriginalId(400L)).thenReturn(Optional.empty());

        strategy.purge(4L);

        verify(recycleBinItemJpaRepository).deleteById(4L);
        verify(questionDeletedJpaRepository, never()).deleteById(anyLong());
    }

    // ============================================================
    // 循环删除恢复
    // ============================================================

    @Test
    @DisplayName("循环：delete → restore → delete → restore 可重复执行")
    void cycleDeleteRestoreTwice_shouldSucceed() {
        Question question = Question.reconstruct(500L, QuestionType.SINGLE_CHOICE, "循环题",
                List.of(), "A", Difficulty.EASY, null, null, QuestionStatus.ACTIVE,
                LocalDateTime.now(), LocalDateTime.now());
        QuestionDeletedPO deletedPO = buildDeletedPO(500L, QuestionStatus.ACTIVE);
        RecycleBinItem binItem = mockBinItem(1L, 500L, "循环题");

        lenient().when(questionRepository.findById(500L)).thenReturn(Optional.of(question));
        lenient().when(relationJpaRepository.findByQuestionId(500L)).thenReturn(List.of());
        lenient().when(recycleBinItemRepositoryPort.findById(1L)).thenReturn(Optional.of(binItem));
        lenient().when(questionDeletedJpaRepository.findByOriginalId(500L)).thenReturn(Optional.of(deletedPO));

        // round 1
        strategy.moveToRecycleBin(500L, "admin");
        strategy.restore(1L);

        // round 2 — must not throw
        strategy.moveToRecycleBin(500L, "admin");
        strategy.restore(1L);
    }

    // helpers

    private QuestionDeletedPO buildDeletedPO(Long originalId, QuestionStatus status) {
        return QuestionDeletedPO.builder()
                .originalId(originalId).type(QuestionType.SINGLE_CHOICE)
                .content("mock内容").options("[{\"key\":\"A\",\"content\":\"选项\"}]")
                .answer("\"A\"").difficulty(Difficulty.EASY).status(status)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .relatedData(null).deletedBy("admin").deletedAt(LocalDateTime.now()).build();
    }

    private QuestionDeletedPO buildDeletedPOWithRelatedData(Long originalId, String relatedData) {
        return QuestionDeletedPO.builder()
                .originalId(originalId).type(QuestionType.SINGLE_CHOICE)
                .content("mock内容").options("[{\"key\":\"A\",\"content\":\"选项\"}]")
                .answer("\"A\"").difficulty(Difficulty.EASY).status(QuestionStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .relatedData(relatedData).deletedBy("admin").deletedAt(LocalDateTime.now()).build();
    }
}
