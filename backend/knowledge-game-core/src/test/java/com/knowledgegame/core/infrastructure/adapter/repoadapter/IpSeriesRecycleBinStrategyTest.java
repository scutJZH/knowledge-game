package com.knowledgegame.core.infrastructure.adapter.repoadapter;

import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.domainenum.CardRarity;
import com.knowledgegame.core.domain.model.domainenum.CardTemplateStatus;
import com.knowledgegame.core.domain.model.domainenum.IpSeriesStatus;
import com.knowledgegame.core.domain.model.domainenum.ResourceType;
import com.knowledgegame.core.domain.port.outbound.FileCleanupPort;
import com.knowledgegame.core.domain.service.IpSeriesDomainService;
import com.knowledgegame.core.infrastructure.db.entity.CardTemplatePO;
import com.knowledgegame.core.infrastructure.db.entity.IpSeriesDeletedPO;
import com.knowledgegame.core.infrastructure.db.entity.IpSeriesPO;
import com.knowledgegame.core.infrastructure.db.entity.RecycleBinItemPO;
import com.knowledgegame.core.infrastructure.db.repository.CardTemplateJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.IpSeriesDeletedJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.IpSeriesJpaRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * IpSeriesRecycleBinStrategy 集成测试
 * <p>
 * 使用真实 MySQL 测试数据库，覆盖全部 4 方法 14 个测试场景。
 */
@DataJpaTest
@EntityScan(basePackages = "com.knowledgegame.core.infrastructure.db.entity")
@EnableJpaRepositories(basePackages = "com.knowledgegame.core.infrastructure.db.repository")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({IpSeriesRecycleBinStrategy.class, IpSeriesDomainService.class,
        IpSeriesRepositoryAdapter.class, CardTemplateRepositoryAdapter.class,
        RecycleBinItemRepositoryAdapter.class})
@ActiveProfiles("test")
class IpSeriesRecycleBinStrategyTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private IpSeriesRecycleBinStrategy strategy;

    @Autowired
    private IpSeriesJpaRepository ipSeriesJpaRepository;

    @Autowired
    private IpSeriesDeletedJpaRepository ipSeriesDeletedJpaRepository;

    @Autowired
    private RecycleBinItemJpaRepository recycleBinItemJpaRepository;

    @Autowired
    private CardTemplateJpaRepository cardTemplateJpaRepository;

    @MockBean
    private FileCleanupPort fileCleanupPort;

    @BeforeEach
    void setUp() {
        recycleBinItemJpaRepository.deleteAll();
        ipSeriesDeletedJpaRepository.deleteAll();
        cardTemplateJpaRepository.deleteAll();
        ipSeriesJpaRepository.deleteAll();
        entityManager.flush();
    }

    // ============================================================
    // validateDeletable
    // ============================================================

    @Test
    @DisplayName("validateDeletable — 无 ACTIVE 卡牌引用 → 正常返回")
    void validateDeletable_noActiveCards_shouldPass() {
        IpSeriesPO ip = persistIpSeries("CODE1", "Series A");

        assertThatCode(() -> strategy.validateDeletable(ip.getId())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateDeletable — 有 ACTIVE 卡牌引用 → BusinessException，消息含「无法删除」")
    void validateDeletable_hasActiveCards_shouldThrow() {
        IpSeriesPO ip = persistIpSeries("CODE1", "Series A");
        persistCardTemplate(ip.getId(), "CARD1", CardTemplateStatus.ACTIVE);

        assertThatThrownBy(() -> strategy.validateDeletable(ip.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无法删除");
    }

    // ============================================================
    // moveToRecycleBin
    // ============================================================

    @Test
    @DisplayName("moveToRecycleBin — 正常路径 → ip_series 行删除 + deleted 有快照 + recycle_bin 有记录")
    void moveToRecycleBin_normal_shouldMoveToRecycleBin() {
        IpSeriesPO ip = persistIpSeries("CODE1", "Series A");
        Long originalId = ip.getId();

        strategy.moveToRecycleBin(originalId, "admin");

        entityManager.flush();
        entityManager.clear();

        assertThat(ipSeriesJpaRepository.findById(originalId)).isEmpty();
        IpSeriesDeletedPO deletedPO = ipSeriesDeletedJpaRepository.findByOriginalId(originalId).orElseThrow();
        assertThat(deletedPO.getCode()).isEqualTo("CODE1");
        assertThat(deletedPO.getName()).isEqualTo("Series A");
        assertThat(deletedPO.getDeletedBy()).isEqualTo("admin");
        assertThat(deletedPO.getRelatedData()).isNull();
        RecycleBinItemPO recycleBinPO = recycleBinItemJpaRepository.findAll().get(0);
        assertThat(recycleBinPO.getResourceType()).isEqualTo(ResourceType.IP_SERIES);
        assertThat(recycleBinPO.getOriginalId()).isEqualTo(originalId);
        assertThat(recycleBinPO.getOriginalName()).isEqualTo("Series A");
    }

    @Test
    @DisplayName("moveToRecycleBin — IP 系列不存在 → BusinessException")
    void moveToRecycleBin_notFound_shouldThrow() {
        assertThatThrownBy(() -> strategy.moveToRecycleBin(9999L, "admin"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("IP 系列不存在: 9999");
    }

    // ============================================================
    // restore
    // ============================================================

    @Test
    @DisplayName("restore — 正常路径 → 行恢复(INACTIVE) + 快照删除 + 回收站删除；createdAt 保留，updatedAt=now")
    void restore_normal_shouldRestoreRow() {
        LocalDateTime originalCreatedAt = LocalDateTime.of(2025, 1, 15, 10, 0);
        Long recycleBinId = setupDeletedAndRecycleBin("CODE-S1", "星穹铁道", 100L, originalCreatedAt);

        strategy.restore(recycleBinId);

        entityManager.flush();
        entityManager.clear();

        IpSeriesPO restored = ipSeriesJpaRepository.findById(100L).orElseThrow();
        assertThat(restored.getCode()).isEqualTo("CODE-S1");
        assertThat(restored.getName()).isEqualTo("星穹铁道");
        assertThat(restored.getStatus()).isEqualTo(IpSeriesStatus.INACTIVE);
        assertThat(restored.getCreatedAt()).isEqualTo(originalCreatedAt);
        assertThat(restored.getUpdatedAt()).isCloseTo(LocalDateTime.now(), within(5, java.time.temporal.ChronoUnit.SECONDS));
        assertThat(ipSeriesDeletedJpaRepository.findByOriginalId(100L)).isEmpty();
        assertThat(recycleBinItemJpaRepository.findById(recycleBinId)).isEmpty();
    }

    @Test
    @DisplayName("restore — code 冲突 → BusinessException")
    void restore_codeConflict_shouldThrow() {
        persistIpSeries("CODE-CONFLICT", "Existing Series");
        Long recycleBinId = setupDeletedAndRecycleBin("CODE-CONFLICT", "Another Name", 200L, LocalDateTime.now());

        assertThatThrownBy(() -> strategy.restore(recycleBinId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("IP 系列编码已存在，无法恢复: CODE-CONFLICT");
    }

    @Test
    @DisplayName("restore — name 冲突 → BusinessException")
    void restore_nameConflict_shouldThrow() {
        persistIpSeries("CODE-X", "Conflicting Name");
        Long recycleBinId = setupDeletedAndRecycleBin("CODE-Y", "Conflicting Name", 300L, LocalDateTime.now());

        assertThatThrownBy(() -> strategy.restore(recycleBinId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("IP 系列名称已存在，无法恢复: Conflicting Name");
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
        RecycleBinItemPO bin = persistRecycleBinItem(777L, "Ghost");
        entityManager.flush();

        assertThatThrownBy(() -> strategy.restore(bin.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("IP 系列快照不存在: 777");
    }

    @Test
    @DisplayName("restore — 目标 ID 已被占用 → DB unique 约束抛异常（真实 MySQL 约束验证）")
    void restore_targetIdOccupied_shouldThrowConstraintViolation() {
        Long recycleBinId = setupDeletedAndRecycleBin("CODE-AAA", "Name AAA", 555L, LocalDateTime.now());
        persistIpSeriesWithId(555L, "CODE-BBB", "Name BBB");

        assertThatThrownBy(() -> strategy.restore(recycleBinId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Duplicate entry");
    }

    // ============================================================
    // purge
    // ============================================================

    @Test
    @DisplayName("purge — 无封面图 → deleted + recycle_bin 均删除")
    void purge_noCoverImage_shouldCleanBoth() {
        Long recycleBinId = setupDeletedAndRecycleBin("CODE-P1", "PurgeTest", 888L, LocalDateTime.now());

        strategy.purge(recycleBinId);

        entityManager.flush();
        entityManager.clear();

        assertThat(ipSeriesDeletedJpaRepository.findByOriginalId(888L)).isEmpty();
        assertThat(recycleBinItemJpaRepository.findById(recycleBinId)).isEmpty();
    }

    @Test
    @DisplayName("purge — 有封面图 → 调 fileCleanupPort.deleteFile(fileId)，然后清理 DB")
    void purge_withCoverImage_shouldCallFileCleanupThenClean() {
        Long recycleBinId = setupDeletedAndRecycleBinWithCover("CODE-P2", "PurgeWithFile", 999L, 42L);

        strategy.purge(recycleBinId);

        verify(fileCleanupPort).deleteFile(42L);
        entityManager.flush();
        entityManager.clear();

        assertThat(ipSeriesDeletedJpaRepository.findByOriginalId(999L)).isEmpty();
        assertThat(recycleBinItemJpaRepository.findById(recycleBinId)).isEmpty();
    }

    @Test
    @DisplayName("purge — FileCleanupPort 抛异常 → 仅 log.warn，DB 清理不受影响")
    void purge_fileCleanupThrows_shouldStillCleanDb() {
        Long recycleBinId = setupDeletedAndRecycleBinWithCover("CODE-P3", "PurgeFileError", 777L, 99L);
        doThrow(new RuntimeException("file service down")).when(fileCleanupPort).deleteFile(any());

        strategy.purge(recycleBinId);

        verify(fileCleanupPort).deleteFile(99L);
        entityManager.flush();
        entityManager.clear();

        assertThat(ipSeriesDeletedJpaRepository.findByOriginalId(777L)).isEmpty();
        assertThat(recycleBinItemJpaRepository.findById(recycleBinId)).isEmpty();
    }

    @Test
    @DisplayName("purge — 回收站记录不存在 → BusinessException")
    void purge_recycleBinNotFound_shouldThrow() {
        assertThatThrownBy(() -> strategy.purge(9999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("回收站记录不存在: 9999");
    }

    // ============================================================
    // helpers
    // ============================================================

    private IpSeriesPO persistIpSeries(String code, String name) {
        IpSeriesPO po = IpSeriesPO.builder()
                .code(code).name(name)
                .status(IpSeriesStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        return ipSeriesJpaRepository.saveAndFlush(po);
    }

    private IpSeriesPO persistIpSeriesWithId(Long id, String code, String name) {
        LocalDateTime now = LocalDateTime.now();
        entityManager.getEntityManager().createNativeQuery(
                "INSERT INTO ip_series (id, code, name, status, created_at, updated_at) "
                        + "VALUES (?,?,?,?,?,?)")
                .setParameter(1, id)
                .setParameter(2, code)
                .setParameter(3, name)
                .setParameter(4, IpSeriesStatus.ACTIVE.name())
                .setParameter(5, now)
                .setParameter(6, now)
                .executeUpdate();
        entityManager.flush();
        return ipSeriesJpaRepository.findById(id).orElseThrow();
    }

    private CardTemplatePO persistCardTemplate(Long ipSeriesId, String code, CardTemplateStatus status) {
        CardTemplatePO po = CardTemplatePO.builder()
                .ipSeriesId(ipSeriesId).code(code).name("Card " + code)
                .rarity(CardRarity.R).status(status)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        cardTemplateJpaRepository.save(po);
        entityManager.flush();
        return po;
    }

    private Long setupDeletedAndRecycleBin(String code, String name, Long originalId, LocalDateTime createdAt) {
        return setupDeletedAndRecycleBinWithCoverImageId(code, name, originalId, createdAt, null);
    }

    private Long setupDeletedAndRecycleBinWithCover(String code, String name, Long originalId, Long coverImageFileId) {
        return setupDeletedAndRecycleBinWithCoverImageId(code, name, originalId, LocalDateTime.now(), coverImageFileId);
    }

    private Long setupDeletedAndRecycleBinWithCoverImageId(String code, String name, Long originalId,
                                                            LocalDateTime createdAt, Long coverImageFileId) {
        IpSeriesDeletedPO deletedPO = IpSeriesDeletedPO.builder()
                .originalId(originalId).code(code).name(name)
                .status(IpSeriesStatus.ACTIVE)
                .coverImageFileId(coverImageFileId)
                .coverImageUrl(coverImageFileId != null ? "http://example.com/img/" + coverImageFileId : null)
                .createdAt(createdAt).updatedAt(LocalDateTime.now())
                .deletedBy("admin").deletedAt(LocalDateTime.now())
                .build();
        deletedPO = ipSeriesDeletedJpaRepository.saveAndFlush(deletedPO);

        RecycleBinItemPO recycleBinPO = RecycleBinItemPO.builder()
                .resourceType(ResourceType.IP_SERIES).originalId(originalId)
                .originalName(name).deletedBy("admin")
                .deletedAt(LocalDateTime.now()).restoreDeadline(LocalDateTime.now().plusDays(30))
                .build();
        recycleBinPO = recycleBinItemJpaRepository.saveAndFlush(recycleBinPO);
        return recycleBinPO.getId();
    }

    private RecycleBinItemPO persistRecycleBinItem(Long originalId, String originalName) {
        RecycleBinItemPO po = RecycleBinItemPO.builder()
                .resourceType(ResourceType.IP_SERIES).originalId(originalId)
                .originalName(originalName).deletedBy("admin")
                .deletedAt(LocalDateTime.now()).restoreDeadline(LocalDateTime.now().plusDays(30))
                .build();
        return recycleBinItemJpaRepository.saveAndFlush(po);
    }
}
