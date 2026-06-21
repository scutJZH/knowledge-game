package com.knowledgegame.admin.application.service.recyclebin;

import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.domainenum.IpSeriesStatus;
import com.knowledgegame.core.domain.model.domainenum.ResourceType;
import com.knowledgegame.core.domain.model.entity.IpSeries;
import com.knowledgegame.core.domain.model.entity.RecycleBinItem;
import com.knowledgegame.core.domain.port.outbound.FileCleanupPort;
import com.knowledgegame.core.domain.port.outbound.IpSeriesRepositoryPort;
import com.knowledgegame.core.domain.port.outbound.RecycleBinItemRepositoryPort;
import com.knowledgegame.core.domain.service.IpSeriesDomainService;
import com.knowledgegame.core.infrastructure.adapter.repoadapter.IpSeriesRecycleBinStrategy;
import com.knowledgegame.core.infrastructure.db.entity.IpSeriesDeletedPO;
import com.knowledgegame.core.infrastructure.db.repository.IpSeriesDeletedJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.IpSeriesJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.RecycleBinItemJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IpSeriesRecycleBinStrategy 黑盒测试
 * <p>
 * 仅凭 PRD 行为描述编写，所有依赖 mock，不依赖 Spring 容器和真实数据库。
 * 测试场景与白盒 {@code IpSeriesRecycleBinStrategyTest}（@DataJpaTest + 真实 MySQL）不重叠。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IpSeriesRecycleBinStrategyBlackBoxTest {

    @Mock
    private IpSeriesDomainService ipSeriesDomainService;
    @Mock
    private IpSeriesRepositoryPort ipSeriesRepositoryPort;
    @Mock
    private RecycleBinItemRepositoryPort recycleBinItemRepositoryPort;
    @Mock
    private IpSeriesJpaRepository ipSeriesJpaRepository;
    @Mock
    private IpSeriesDeletedJpaRepository ipSeriesDeletedJpaRepository;
    @Mock
    private RecycleBinItemJpaRepository recycleBinItemJpaRepository;
    @Mock
    private FileCleanupPort fileCleanupPort;
    @Mock
    private EntityManager entityManager;
    @Mock
    private Query nativeQuery;

    private IpSeriesRecycleBinStrategy strategy;

    private static final LocalDateTime NOW = LocalDateTime.now();

    @BeforeEach
    void setUp() {
        strategy = new IpSeriesRecycleBinStrategy(
                ipSeriesDomainService,
                ipSeriesRepositoryPort,
                recycleBinItemRepositoryPort,
                ipSeriesJpaRepository,
                ipSeriesDeletedJpaRepository,
                recycleBinItemJpaRepository,
                Optional.of(fileCleanupPort));
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

        RecycleBinItem recycleBinItem = new RecycleBinItem(recycleBinId, ResourceType.IP_SERIES,
                originalId, "测试系列", null, null, null, null, "admin", NOW, NOW.plusDays(30));
        when(recycleBinItemRepositoryPort.findById(recycleBinId)).thenReturn(Optional.of(recycleBinItem));

        IpSeriesDeletedPO deletedPO = buildDeleted(originalId, "CODE-S", "系列S", IpSeriesStatus.ACTIVE);
        when(ipSeriesDeletedJpaRepository.findByOriginalId(originalId)).thenReturn(Optional.of(deletedPO));
        when(ipSeriesJpaRepository.findByCode("CODE-S")).thenReturn(Optional.empty());
        when(ipSeriesJpaRepository.findByName("系列S")).thenReturn(Optional.empty());

        strategy.restore(recycleBinId);

        ArgumentCaptor<Object> statusCaptor = ArgumentCaptor.forClass(Object.class);
        verify(nativeQuery).setParameter(eq(7), statusCaptor.capture());
        assertThat(statusCaptor.getValue()).hasToString("INACTIVE");

        verify(ipSeriesDeletedJpaRepository).deleteById(deletedPO.getId());
        verify(recycleBinItemJpaRepository).deleteById(recycleBinId);
    }

    @Test
    @DisplayName("restore — 即使删除时原状态为 INACTIVE，恢复后仍为 INACTIVE（不区分原状态）")
    void restore_shouldStillBeInactive_whenDeletedStatusWasInactive() {
        stubNativeInsert();
        long recycleBinId = 2L;
        long originalId = 200L;

        RecycleBinItem recycleBinItem = new RecycleBinItem(recycleBinId, ResourceType.IP_SERIES,
                originalId, "已停用系列", null, null, null, null, "admin", NOW, NOW.plusDays(30));
        when(recycleBinItemRepositoryPort.findById(recycleBinId)).thenReturn(Optional.of(recycleBinItem));

        IpSeriesDeletedPO deletedPO = buildDeleted(originalId, "CODE-I", "已停用系列", IpSeriesStatus.INACTIVE);
        when(ipSeriesDeletedJpaRepository.findByOriginalId(originalId)).thenReturn(Optional.of(deletedPO));
        when(ipSeriesJpaRepository.findByCode("CODE-I")).thenReturn(Optional.empty());
        when(ipSeriesJpaRepository.findByName("已停用系列")).thenReturn(Optional.empty());

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
        IpSeries ipSeries = buildIpSeries(originalId, "CODE-CYCLE", "循环测试", IpSeriesStatus.ACTIVE);
        when(ipSeriesRepositoryPort.findById(originalId)).thenReturn(Optional.of(ipSeries));

        strategy.moveToRecycleBin(originalId, "admin");

        verify(ipSeriesDeletedJpaRepository).save(any(IpSeriesDeletedPO.class));
        verify(ipSeriesJpaRepository).deleteById(originalId);
        verify(recycleBinItemJpaRepository).save(any());

        // --- 第一次恢复 ---
        RecycleBinItem recycleBinItem1 = new RecycleBinItem(recycleBinId1, ResourceType.IP_SERIES,
                originalId, "循环测试", null, null, null, null, "admin", NOW, NOW.plusDays(30));
        when(recycleBinItemRepositoryPort.findById(recycleBinId1)).thenReturn(Optional.of(recycleBinItem1));

        IpSeriesDeletedPO deletedPO1 = buildDeleted(originalId, "CODE-CYCLE", "循环测试", IpSeriesStatus.ACTIVE);
        when(ipSeriesDeletedJpaRepository.findByOriginalId(originalId)).thenReturn(Optional.of(deletedPO1));
        when(ipSeriesJpaRepository.findByCode("CODE-CYCLE")).thenReturn(Optional.empty());
        when(ipSeriesJpaRepository.findByName("循环测试")).thenReturn(Optional.empty());

        strategy.restore(recycleBinId1);

        verify(ipSeriesDeletedJpaRepository).deleteById(deletedPO1.getId());
        verify(recycleBinItemJpaRepository).deleteById(recycleBinId1);

        // --- 第二次删除 ---
        IpSeries ipSeries2 = buildIpSeries(originalId, "CODE-CYCLE", "循环测试", IpSeriesStatus.INACTIVE);
        when(ipSeriesRepositoryPort.findById(originalId)).thenReturn(Optional.of(ipSeries2));

        strategy.moveToRecycleBin(originalId, "admin");

        // --- 第二次恢复 ---
        RecycleBinItem recycleBinItem2 = new RecycleBinItem(recycleBinId2, ResourceType.IP_SERIES,
                originalId, "循环测试", null, null, null, null, "admin", NOW, NOW.plusDays(30));
        when(recycleBinItemRepositoryPort.findById(recycleBinId2)).thenReturn(Optional.of(recycleBinItem2));

        IpSeriesDeletedPO deletedPO2 = buildDeleted(originalId, "CODE-CYCLE", "循环测试", IpSeriesStatus.INACTIVE);
        when(ipSeriesDeletedJpaRepository.findByOriginalId(originalId)).thenReturn(Optional.of(deletedPO2));
        when(ipSeriesJpaRepository.findByCode("CODE-CYCLE")).thenReturn(Optional.empty());
        when(ipSeriesJpaRepository.findByName("循环测试")).thenReturn(Optional.empty());

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

        RecycleBinItem recycleBinItem = new RecycleBinItem(recycleBinId, ResourceType.IP_SERIES,
                originalId, "并发删除系列", null, null, null, null, "admin", NOW, NOW.plusDays(30));
        when(recycleBinItemRepositoryPort.findById(recycleBinId)).thenReturn(Optional.of(recycleBinItem));
        when(ipSeriesDeletedJpaRepository.findByOriginalId(originalId)).thenReturn(Optional.empty());

        strategy.purge(recycleBinId);

        verify(fileCleanupPort, never()).deleteFile(anyLong());
        verify(ipSeriesDeletedJpaRepository, never()).deleteById(anyLong());
        verify(recycleBinItemJpaRepository).deleteById(recycleBinId);
    }

    @Test
    @DisplayName("purge — 快照存在但无封面图 → 不调 fileCleanupPort，正常清理 DB")
    void purge_deletedExistsNoCover_shouldNotCallFileCleanup() {
        long recycleBinId = 4L;
        long originalId = 500L;

        RecycleBinItem recycleBinItem = new RecycleBinItem(recycleBinId, ResourceType.IP_SERIES,
                originalId, "无封面系列", null, null, null, null, "admin", NOW, NOW.plusDays(30));
        when(recycleBinItemRepositoryPort.findById(recycleBinId)).thenReturn(Optional.of(recycleBinItem));

        IpSeriesDeletedPO deletedPO = buildDeleted(originalId, "CODE-NC", "无封面系列", IpSeriesStatus.ACTIVE);
        deletedPO.setCoverImageFileId(null);
        when(ipSeriesDeletedJpaRepository.findByOriginalId(originalId)).thenReturn(Optional.of(deletedPO));

        strategy.purge(recycleBinId);

        verify(fileCleanupPort, never()).deleteFile(anyLong());
        verify(ipSeriesDeletedJpaRepository).deleteById(deletedPO.getId());
        verify(recycleBinItemJpaRepository).deleteById(recycleBinId);
    }

    // ============================================================
    // (d) validateDeletable 消息替换
    // ============================================================

    @Test
    @DisplayName("validateDeletable — 将「无法停用」替换为「无法删除」")
    void validateDeletable_shouldReplaceDeactivateWithDelete() {
        long originalId = 600L;
        doThrow(new BusinessException("IP 系列存在 3 张 ACTIVE 卡牌，无法停用"))
                .when(ipSeriesDomainService).validateDeactivatable(originalId);

        assertThatThrownBy(() -> strategy.validateDeletable(originalId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无法删除")
                .hasMessageNotContaining("无法停用");
    }

    @Test
    @DisplayName("validateDeletable — 无引用时不抛异常")
    void validateDeletable_noReference_shouldNotThrow() {
        long originalId = 700L;

        assertThatCode(() -> strategy.validateDeletable(originalId)).doesNotThrowAnyException();
    }

    // ============================================================
    // helpers
    // ============================================================

    private static IpSeriesDeletedPO buildDeleted(Long originalId, String code, String name,
                                                   IpSeriesStatus status) {
        IpSeriesDeletedPO po = new IpSeriesDeletedPO();
        po.setId(originalId + 1000);
        po.setOriginalId(originalId);
        po.setCode(code);
        po.setName(name);
        po.setStatus(status);
        po.setCreatedAt(LocalDateTime.of(2025, 6, 1, 10, 0));
        po.setUpdatedAt(NOW);
        po.setDeletedBy("admin");
        po.setDeletedAt(NOW);
        return po;
    }

    private static IpSeries buildIpSeries(Long id, String code, String name, IpSeriesStatus status) {
        return IpSeries.reconstruct(id, code, name, "", null, status,
                LocalDateTime.of(2025, 6, 1, 10, 0), NOW);
    }
}
