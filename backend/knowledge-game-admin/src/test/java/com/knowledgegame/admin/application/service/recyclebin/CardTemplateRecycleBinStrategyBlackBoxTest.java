package com.knowledgegame.admin.application.service.recyclebin;

import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.domainenum.CardRarity;
import com.knowledgegame.core.domain.model.domainenum.CardTemplateStatus;
import com.knowledgegame.core.domain.model.domainenum.ResourceType;
import com.knowledgegame.core.domain.model.entity.CardTemplate;
import com.knowledgegame.core.domain.model.entity.RecycleBinItem;
import com.knowledgegame.core.domain.port.outbound.CardTemplateRepositoryPort;
import com.knowledgegame.core.domain.port.outbound.FileCleanupPort;
import com.knowledgegame.core.domain.port.outbound.IpSeriesRepositoryPort;
import com.knowledgegame.core.domain.port.outbound.RecycleBinItemRepositoryPort;
import com.knowledgegame.core.infrastructure.adapter.repoadapter.CardTemplateRecycleBinStrategy;
import com.knowledgegame.core.infrastructure.db.entity.CardTemplateDeletedPO;
import com.knowledgegame.core.infrastructure.db.repository.CardTemplateDeletedJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.CardTemplateJpaRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CardTemplateRecycleBinStrategy 黑盒测试
 * <p>
 * 仅凭 PRD 行为描述编写，所有依赖 mock，不依赖 Spring 容器和真实数据库。
 * 测试场景与白盒 {@code CardTemplateRecycleBinStrategyTest}（@DataJpaTest + 真实 MySQL）不重叠。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CardTemplateRecycleBinStrategyBlackBoxTest {

    @Mock
    private CardTemplateRepositoryPort cardTemplateRepositoryPort;
    @Mock
    private RecycleBinItemRepositoryPort recycleBinItemRepositoryPort;
    @Mock
    private CardTemplateJpaRepository cardTemplateJpaRepository;
    @Mock
    private CardTemplateDeletedJpaRepository cardTemplateDeletedJpaRepository;
    @Mock
    private RecycleBinItemJpaRepository recycleBinItemJpaRepository;
    @Mock
    private IpSeriesRepositoryPort ipSeriesRepositoryPort;
    @Mock
    private FileCleanupPort fileCleanupPort;
    @Mock
    private EntityManager entityManager;
    @Mock
    private Query nativeQuery;

    @InjectMocks
    private CardTemplateRecycleBinStrategy strategy;

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
    // (a) restore 时 status 被覆盖为 INACTIVE
    // ============================================================

    @Test
    @DisplayName("restore — status 被强制覆盖为 INACTIVE，无论删除前状态是 ACTIVE")
    void restore_shouldForceInactive_whenDeletedStatusWasActive() {
        stubNativeInsert();
        long recycleBinId = 1L;
        long originalId = 100L;

        RecycleBinItem recycleBinItem = new RecycleBinItem(recycleBinId, ResourceType.CARD_TEMPLATE,
                originalId, "测试卡牌", null, null, null, null, "admin", NOW, NOW.plusDays(30));
        when(recycleBinItemRepositoryPort.findById(recycleBinId)).thenReturn(Optional.of(recycleBinItem));

        CardTemplateDeletedPO deletedPO = buildDeleted(originalId, "PIKACHU", "皮卡丘", 10L, CardTemplateStatus.ACTIVE);
        when(cardTemplateDeletedJpaRepository.findByOriginalId(originalId)).thenReturn(Optional.of(deletedPO));
        when(ipSeriesRepositoryPort.existsById(10L)).thenReturn(true);
        when(cardTemplateJpaRepository.findByIpSeriesIdAndCode(10L, "PIKACHU")).thenReturn(Optional.empty());

        strategy.restore(recycleBinId);

        ArgumentCaptor<Object> statusCaptor = ArgumentCaptor.forClass(Object.class);
        verify(nativeQuery).setParameter(eq(7), statusCaptor.capture());
        assertThat(statusCaptor.getValue()).hasToString("INACTIVE");

        verify(cardTemplateDeletedJpaRepository).deleteById(deletedPO.getId());
        verify(recycleBinItemJpaRepository).deleteById(recycleBinId);
    }

    @Test
    @DisplayName("restore — 即使删除时原状态为 INACTIVE，恢复后仍为 INACTIVE")
    void restore_shouldStillBeInactive_whenDeletedStatusWasInactive() {
        stubNativeInsert();
        long recycleBinId = 2L;
        long originalId = 200L;

        RecycleBinItem recycleBinItem = new RecycleBinItem(recycleBinId, ResourceType.CARD_TEMPLATE,
                originalId, "已停用卡牌", null, null, null, null, "admin", NOW, NOW.plusDays(30));
        when(recycleBinItemRepositoryPort.findById(recycleBinId)).thenReturn(Optional.of(recycleBinItem));

        CardTemplateDeletedPO deletedPO = buildDeleted(originalId, "CODE-I", "已停用卡牌", 10L, CardTemplateStatus.INACTIVE);
        when(cardTemplateDeletedJpaRepository.findByOriginalId(originalId)).thenReturn(Optional.of(deletedPO));
        when(ipSeriesRepositoryPort.existsById(10L)).thenReturn(true);
        when(cardTemplateJpaRepository.findByIpSeriesIdAndCode(10L, "CODE-I")).thenReturn(Optional.empty());

        strategy.restore(recycleBinId);

        ArgumentCaptor<Object> statusCaptor = ArgumentCaptor.forClass(Object.class);
        verify(nativeQuery).setParameter(eq(7), statusCaptor.capture());
        assertThat(statusCaptor.getValue()).hasToString("INACTIVE");
    }

    // ============================================================
    // (b) 循环操作: delete → restore → delete → restore
    // ============================================================

    @Test
    @DisplayName("循环操作 — moveToRecycleBin → restore → 再 moveToRecycleBin → restore 应成功")
    void cycleDeleteRestoreTwice_shouldSucceed() {
        stubNativeInsert();
        long originalId = 300L;
        long recycleBinId1 = 10L;
        long recycleBinId2 = 11L;

        // --- 第一次删除 ---
        CardTemplate card = buildCardTemplate(originalId, "CODE-CYCLE", "循环测试", 1L, CardTemplateStatus.ACTIVE);
        when(cardTemplateRepositoryPort.findById(originalId)).thenReturn(Optional.of(card));

        strategy.moveToRecycleBin(originalId, "admin");

        verify(cardTemplateDeletedJpaRepository).save(any(CardTemplateDeletedPO.class));
        verify(cardTemplateJpaRepository).deleteById(originalId);
        verify(recycleBinItemJpaRepository).save(any());

        // --- 第一次恢复 ---
        RecycleBinItem recycleBinItem1 = new RecycleBinItem(recycleBinId1, ResourceType.CARD_TEMPLATE,
                originalId, "循环测试", null, null, null, null, "admin", NOW, NOW.plusDays(30));
        when(recycleBinItemRepositoryPort.findById(recycleBinId1)).thenReturn(Optional.of(recycleBinItem1));

        CardTemplateDeletedPO deletedPO1 = buildDeleted(originalId, "CODE-CYCLE", "循环测试", 1L, CardTemplateStatus.ACTIVE);
        when(cardTemplateDeletedJpaRepository.findByOriginalId(originalId)).thenReturn(Optional.of(deletedPO1));
        when(ipSeriesRepositoryPort.existsById(1L)).thenReturn(true);
        when(cardTemplateJpaRepository.findByIpSeriesIdAndCode(1L, "CODE-CYCLE")).thenReturn(Optional.empty());

        strategy.restore(recycleBinId1);

        verify(cardTemplateDeletedJpaRepository).deleteById(deletedPO1.getId());
        verify(recycleBinItemJpaRepository).deleteById(recycleBinId1);

        // --- 第二次删除 ---
        CardTemplate card2 = buildCardTemplate(originalId, "CODE-CYCLE", "循环测试", 1L, CardTemplateStatus.INACTIVE);
        when(cardTemplateRepositoryPort.findById(originalId)).thenReturn(Optional.of(card2));

        strategy.moveToRecycleBin(originalId, "admin");

        // --- 第二次恢复 ---
        RecycleBinItem recycleBinItem2 = new RecycleBinItem(recycleBinId2, ResourceType.CARD_TEMPLATE,
                originalId, "循环测试", null, null, null, null, "admin", NOW, NOW.plusDays(30));
        when(recycleBinItemRepositoryPort.findById(recycleBinId2)).thenReturn(Optional.of(recycleBinItem2));

        CardTemplateDeletedPO deletedPO2 = buildDeleted(originalId, "CODE-CYCLE", "循环测试", 1L, CardTemplateStatus.INACTIVE);
        when(cardTemplateDeletedJpaRepository.findByOriginalId(originalId)).thenReturn(Optional.of(deletedPO2));
        when(ipSeriesRepositoryPort.existsById(1L)).thenReturn(true);
        when(cardTemplateJpaRepository.findByIpSeriesIdAndCode(1L, "CODE-CYCLE")).thenReturn(Optional.empty());

        assertThatCode(() -> strategy.restore(recycleBinId2)).doesNotThrowAnyException();
    }

    // ============================================================
    // (c) purge 时 deletedPO 已被并发删除 → 静默跳过
    // ============================================================

    @Test
    @DisplayName("purge — 快照已被并发删除（deletedPO=null）→ 静默跳过，仅删 recycle_bin")
    void purge_deletedSnapshotConcurrentlyDeleted_shouldSilentlySkip() {
        long recycleBinId = 3L;
        long originalId = 400L;

        RecycleBinItem recycleBinItem = new RecycleBinItem(recycleBinId, ResourceType.CARD_TEMPLATE,
                originalId, "并发删除卡牌", null, null, null, null, "admin", NOW, NOW.plusDays(30));
        when(recycleBinItemRepositoryPort.findById(recycleBinId)).thenReturn(Optional.of(recycleBinItem));
        when(cardTemplateDeletedJpaRepository.findByOriginalId(originalId)).thenReturn(Optional.empty());

        strategy.purge(recycleBinId);

        verify(fileCleanupPort, never()).deleteFile(anyLong());
        verify(cardTemplateDeletedJpaRepository, never()).deleteById(anyLong());
        verify(recycleBinItemJpaRepository).deleteById(recycleBinId);
    }

    @Test
    @DisplayName("purge — 快照存在但无图片 → 不调 fileCleanupPort，正常清理 DB")
    void purge_deletedExistsNoImage_shouldNotCallFileCleanup() {
        long recycleBinId = 4L;
        long originalId = 500L;

        RecycleBinItem recycleBinItem = new RecycleBinItem(recycleBinId, ResourceType.CARD_TEMPLATE,
                originalId, "无图片卡牌", null, null, null, null, "admin", NOW, NOW.plusDays(30));
        when(recycleBinItemRepositoryPort.findById(recycleBinId)).thenReturn(Optional.of(recycleBinItem));

        CardTemplateDeletedPO deletedPO = buildDeleted(originalId, "CODE-NI", "无图片卡牌", 1L, CardTemplateStatus.ACTIVE);
        deletedPO.setImageFileId(null);
        when(cardTemplateDeletedJpaRepository.findByOriginalId(originalId)).thenReturn(Optional.of(deletedPO));

        strategy.purge(recycleBinId);

        verify(fileCleanupPort, never()).deleteFile(anyLong());
        verify(cardTemplateDeletedJpaRepository).deleteById(deletedPO.getId());
        verify(recycleBinItemJpaRepository).deleteById(recycleBinId);
    }

    // ============================================================
    // (d) restore 时 IP 系列不存在 → BusinessException
    // ============================================================

    @Test
    @DisplayName("restore — IP 系列已删除 → BusinessException")
    void restore_ipSeriesNotExists_shouldThrow() {
        long recycleBinId = 5L;
        long originalId = 600L;

        RecycleBinItem recycleBinItem = new RecycleBinItem(recycleBinId, ResourceType.CARD_TEMPLATE,
                originalId, "孤儿卡牌", null, null, null, null, "admin", NOW, NOW.plusDays(30));
        when(recycleBinItemRepositoryPort.findById(recycleBinId)).thenReturn(Optional.of(recycleBinItem));

        CardTemplateDeletedPO deletedPO = buildDeleted(originalId, "ORPHAN", "孤儿卡牌", 999L, CardTemplateStatus.ACTIVE);
        when(cardTemplateDeletedJpaRepository.findByOriginalId(originalId)).thenReturn(Optional.of(deletedPO));
        when(ipSeriesRepositoryPort.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> strategy.restore(recycleBinId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已不存在，无法恢复");
    }

    // ============================================================
    // helpers
    // ============================================================

    private static CardTemplateDeletedPO buildDeleted(Long originalId, String code, String name,
                                                       Long ipSeriesId, CardTemplateStatus status) {
        CardTemplateDeletedPO po = new CardTemplateDeletedPO();
        po.setId(originalId + 1000);
        po.setOriginalId(originalId);
        po.setIpSeriesId(ipSeriesId);
        po.setCode(code);
        po.setName(name);
        po.setRarity(CardRarity.SR);
        po.setStatus(status);
        po.setCreatedAt(LocalDateTime.of(2025, 6, 1, 10, 0));
        po.setUpdatedAt(NOW);
        po.setDeletedBy("admin");
        po.setDeletedAt(NOW);
        return po;
    }

    private static CardTemplate buildCardTemplate(Long id, String code, String name, Long ipSeriesId,
                                                   CardTemplateStatus status) {
        return CardTemplate.reconstruct(id, ipSeriesId, code, name, CardRarity.SR, "",
                status, null, LocalDateTime.of(2025, 6, 1, 10, 0), NOW);
    }
}
