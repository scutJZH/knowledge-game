package com.knowledgegame.core.infrastructure.adapter.repoadapter;

import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.domainenum.CardRarity;
import com.knowledgegame.core.domain.model.domainenum.CardTemplateStatus;
import com.knowledgegame.core.domain.model.domainenum.IpSeriesStatus;
import com.knowledgegame.core.domain.model.domainenum.ResourceType;
import com.knowledgegame.core.domain.port.outbound.FileCleanupPort;
import com.knowledgegame.core.infrastructure.db.entity.CardTemplateDeletedPO;
import com.knowledgegame.core.infrastructure.db.entity.CardTemplatePO;
import com.knowledgegame.core.infrastructure.db.entity.IpSeriesPO;
import com.knowledgegame.core.infrastructure.db.entity.RecycleBinItemPO;
import com.knowledgegame.core.infrastructure.db.repository.CardTemplateDeletedJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.CardTemplateJpaRepository;
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
 * CardTemplateRecycleBinStrategy 集成测试
 * <p>
 * 使用真实 MySQL 测试数据库，覆盖全部 4 方法。
 */
@DataJpaTest
@EntityScan(basePackages = "com.knowledgegame.core.infrastructure.db.entity")
@EnableJpaRepositories(basePackages = "com.knowledgegame.core.infrastructure.db.repository")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CardTemplateRecycleBinStrategy.class,
        CardTemplateRepositoryAdapter.class,
        RecycleBinItemRepositoryAdapter.class,
        IpSeriesRepositoryAdapter.class})
@ActiveProfiles("test")
class CardTemplateRecycleBinStrategyTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private CardTemplateRecycleBinStrategy strategy;

    @Autowired
    private CardTemplateJpaRepository cardTemplateJpaRepository;

    @Autowired
    private CardTemplateDeletedJpaRepository cardTemplateDeletedJpaRepository;

    @Autowired
    private RecycleBinItemJpaRepository recycleBinItemJpaRepository;

    @Autowired
    private IpSeriesJpaRepository ipSeriesJpaRepository;

    @MockBean
    private FileCleanupPort fileCleanupPort;

    @BeforeEach
    void setUp() {
        recycleBinItemJpaRepository.deleteAll();
        cardTemplateDeletedJpaRepository.deleteAll();
        cardTemplateJpaRepository.deleteAll();
        ipSeriesJpaRepository.deleteAll();
        entityManager.flush();
    }

    // ============================================================
    // validateDeletable
    // ============================================================

    @Test
    @DisplayName("validateDeletable — 卡牌存在 → 正常返回")
    void validateDeletable_exists_shouldPass() {
        IpSeriesPO ip = persistIpSeries("PKM");
        CardTemplatePO card = persistCardTemplate(ip.getId(), "CARD1", CardTemplateStatus.ACTIVE);

        assertThatCode(() -> strategy.validateDeletable(card.getId())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateDeletable — 卡牌不存在 → BusinessException")
    void validateDeletable_notFound_shouldThrow() {
        assertThatThrownBy(() -> strategy.validateDeletable(9999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("卡牌模板不存在: 9999");
    }

    // ============================================================
    // moveToRecycleBin
    // ============================================================

    @Test
    @DisplayName("moveToRecycleBin — 正常路径 → card_template 行删除 + deleted 有快照 + recycle_bin 有记录")
    void moveToRecycleBin_normal_shouldMoveToRecycleBin() {
        IpSeriesPO ip = persistIpSeries("PKM");
        CardTemplatePO card = persistCardTemplate(ip.getId(), "PIKACHU", "皮卡丘");
        Long originalId = card.getId();

        strategy.moveToRecycleBin(originalId, "admin");

        entityManager.flush();
        entityManager.clear();

        assertThat(cardTemplateJpaRepository.findById(originalId)).isEmpty();
        CardTemplateDeletedPO deletedPO = cardTemplateDeletedJpaRepository.findByOriginalId(originalId).orElseThrow();
        assertThat(deletedPO.getCode()).isEqualTo("PIKACHU");
        assertThat(deletedPO.getName()).isEqualTo("皮卡丘");
        assertThat(deletedPO.getDeletedBy()).isEqualTo("admin");
        assertThat(deletedPO.getRelatedData()).isNull();
        assertThat(deletedPO.getIpSeriesId()).isEqualTo(ip.getId());
        assertThat(deletedPO.getIpSeriesName()).isEqualTo("IP PKM");
        RecycleBinItemPO recycleBinPO = recycleBinItemJpaRepository.findAll().get(0);
        assertThat(recycleBinPO.getResourceType()).isEqualTo(ResourceType.CARD_TEMPLATE);
        assertThat(recycleBinPO.getOriginalId()).isEqualTo(originalId);
        assertThat(recycleBinPO.getOriginalName()).isEqualTo("皮卡丘");
    }

    @Test
    @DisplayName("moveToRecycleBin — 卡牌模板不存在 → BusinessException")
    void moveToRecycleBin_notFound_shouldThrow() {
        assertThatThrownBy(() -> strategy.moveToRecycleBin(9999L, "admin"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("卡牌模板不存在: 9999");
    }

    @Test
    @DisplayName("moveToRecycleBin — 含图片的卡牌 → 快照保留 imageFileId 和 imageUrl")
    void moveToRecycleBin_withImage_shouldPreserveImageFields() {
        IpSeriesPO ip = persistIpSeries("PKM");
        CardTemplatePO card = CardTemplatePO.builder()
                .ipSeriesId(ip.getId()).code("CHARMANDER").name("小火龙")
                .rarity(CardRarity.R).status(CardTemplateStatus.ACTIVE)
                .imageFileId(42L).imageUrl("http://example.com/img/42")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        card = cardTemplateJpaRepository.saveAndFlush(card);
        entityManager.flush();

        strategy.moveToRecycleBin(card.getId(), "admin");
        entityManager.flush();
        entityManager.clear();

        CardTemplateDeletedPO deletedPO = cardTemplateDeletedJpaRepository.findByOriginalId(card.getId()).orElseThrow();
        assertThat(deletedPO.getImageFileId()).isEqualTo(42L);
        assertThat(deletedPO.getImageUrl()).isEqualTo("http://example.com/img/42");
    }

    // ============================================================
    // restore
    // ============================================================

    @Test
    @DisplayName("restore — 正常路径 → 行恢复(INACTIVE) + 快照删除 + 回收站删除")
    void restore_normal_shouldRestoreRow() {
        IpSeriesPO ip = persistIpSeries("PKM");
        LocalDateTime originalCreatedAt = LocalDateTime.of(2025, 1, 15, 10, 0);
        Long recycleBinId = setupDeletedAndRecycleBin("PIKACHU", "皮卡丘", 100L, ip.getId(), originalCreatedAt);

        strategy.restore(recycleBinId);

        entityManager.flush();
        entityManager.clear();

        CardTemplatePO restored = cardTemplateJpaRepository.findById(100L).orElseThrow();
        assertThat(restored.getCode()).isEqualTo("PIKACHU");
        assertThat(restored.getName()).isEqualTo("皮卡丘");
        assertThat(restored.getStatus()).isEqualTo(CardTemplateStatus.INACTIVE);
        assertThat(restored.getCreatedAt()).isEqualTo(originalCreatedAt);
        assertThat(restored.getUpdatedAt()).isCloseTo(LocalDateTime.now(), within(5, java.time.temporal.ChronoUnit.SECONDS));
        assertThat(cardTemplateDeletedJpaRepository.findByOriginalId(100L)).isEmpty();
        assertThat(recycleBinItemJpaRepository.findById(recycleBinId)).isEmpty();
    }

    @Test
    @DisplayName("restore — IP 系列已不存在 → BusinessException，含 IP 名称")
    void restore_ipSeriesNotExists_shouldThrow() {
        Long recycleBinId = setupDeletedAndRecycleBinWithIpName(
                "PIKACHU", "皮卡丘", 200L, 9999L, "火影忍者", LocalDateTime.now());

        assertThatThrownBy(() -> strategy.restore(recycleBinId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("卡牌模板关联的 IP 系列《火影忍者》(ID=9999) 已不存在，无法恢复");
    }

    @Test
    @DisplayName("restore — code 冲突 → BusinessException")
    void restore_codeConflict_shouldThrow() {
        IpSeriesPO ip = persistIpSeries("PKM");
        persistCardTemplateWithCode(ip.getId(), "PIKACHU", "冲突卡牌");
        Long recycleBinId = setupDeletedAndRecycleBin("PIKACHU", "皮卡丘", 300L, ip.getId(), LocalDateTime.now());

        assertThatThrownBy(() -> strategy.restore(recycleBinId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("卡牌编码已存在，无法恢复: PIKACHU");
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
                .hasMessageContaining("卡牌模板快照不存在: 777");
    }

    // ============================================================
    // purge
    // ============================================================

    @Test
    @DisplayName("purge — 无图片 → deleted + recycle_bin 均删除")
    void purge_noImage_shouldCleanBoth() {
        Long recycleBinId = setupDeletedAndRecycleBin("CHARIZARD", "喷火龙", 888L, 1L, LocalDateTime.now());

        strategy.purge(recycleBinId);

        entityManager.flush();
        entityManager.clear();

        assertThat(cardTemplateDeletedJpaRepository.findByOriginalId(888L)).isEmpty();
        assertThat(recycleBinItemJpaRepository.findById(recycleBinId)).isEmpty();
    }

    @Test
    @DisplayName("purge — 有图片 → 调 fileCleanupPort.deleteFile(fileId)，然后清理 DB")
    void purge_withImage_shouldCallFileCleanupThenClean() {
        Long recycleBinId = setupDeletedAndRecycleBinWithImage("CHARIZARD", "喷火龙", 999L, 1L, 42L);

        strategy.purge(recycleBinId);

        verify(fileCleanupPort).deleteFile(42L);
        entityManager.flush();
        entityManager.clear();

        assertThat(cardTemplateDeletedJpaRepository.findByOriginalId(999L)).isEmpty();
        assertThat(recycleBinItemJpaRepository.findById(recycleBinId)).isEmpty();
    }

    @Test
    @DisplayName("purge — FileCleanupPort 抛异常 → 仅 log.warn，DB 清理不受影响")
    void purge_fileCleanupThrows_shouldStillCleanDb() {
        Long recycleBinId = setupDeletedAndRecycleBinWithImage("CHARIZARD", "喷火龙", 777L, 1L, 99L);
        doThrow(new RuntimeException("file service down")).when(fileCleanupPort).deleteFile(any());

        strategy.purge(recycleBinId);

        verify(fileCleanupPort).deleteFile(99L);
        entityManager.flush();
        entityManager.clear();

        assertThat(cardTemplateDeletedJpaRepository.findByOriginalId(777L)).isEmpty();
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
    @DisplayName("purge — 快照已被并发删除 → 静默跳过，仅删 recycle_bin")
    void purge_snapshotConcurrentlyDeleted_shouldSilentlySkip() {
        RecycleBinItemPO bin = persistRecycleBinItem(555L, "GhostCard");
        entityManager.flush();

        strategy.purge(bin.getId());

        entityManager.flush();
        entityManager.clear();

        verifyNoInteractions(fileCleanupPort);
        assertThat(recycleBinItemJpaRepository.findById(bin.getId())).isEmpty();
    }

    // ============================================================
    // helpers
    // ============================================================

    private IpSeriesPO persistIpSeries(String code) {
        IpSeriesPO po = IpSeriesPO.builder()
                .code(code).name("IP " + code)
                .status(IpSeriesStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        return ipSeriesJpaRepository.saveAndFlush(po);
    }

    private CardTemplatePO persistCardTemplate(Long ipSeriesId, String code, CardTemplateStatus status) {
        return persistCardTemplateWithCode(ipSeriesId, code, "Card " + code);
    }

    private CardTemplatePO persistCardTemplate(Long ipSeriesId, String code, String name) {
        return persistCardTemplateWithCode(ipSeriesId, code, name);
    }

    private CardTemplatePO persistCardTemplateWithCode(Long ipSeriesId, String code, String name) {
        CardTemplatePO po = CardTemplatePO.builder()
                .ipSeriesId(ipSeriesId).code(code).name(name)
                .rarity(CardRarity.R).status(CardTemplateStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        cardTemplateJpaRepository.save(po);
        entityManager.flush();
        return po;
    }

    private Long setupDeletedAndRecycleBin(String code, String name, Long originalId,
                                            Long ipSeriesId, LocalDateTime createdAt) {
        return setupDeletedAndRecycleBinWithIpName(code, name, originalId, ipSeriesId, null, createdAt);
    }

    private Long setupDeletedAndRecycleBinWithIpName(String code, String name, Long originalId,
                                                      Long ipSeriesId, String ipSeriesName,
                                                      LocalDateTime createdAt) {
        return setupDeletedAndRecycleBinWithImageIdAndIpName(code, name, originalId, ipSeriesId, ipSeriesName, createdAt, null);
    }

    private Long setupDeletedAndRecycleBinWithImage(String code, String name, Long originalId,
                                                     Long ipSeriesId, Long imageFileId) {
        return setupDeletedAndRecycleBinWithImageIdAndIpName(code, name, originalId, ipSeriesId, null,
                LocalDateTime.now(), imageFileId);
    }

    private Long setupDeletedAndRecycleBinWithImageIdAndIpName(String code, String name, Long originalId,
                                                                Long ipSeriesId, String ipSeriesName,
                                                                LocalDateTime createdAt, Long imageFileId) {
        CardTemplateDeletedPO deletedPO = CardTemplateDeletedPO.builder()
                .originalId(originalId).ipSeriesId(ipSeriesId).ipSeriesName(ipSeriesName)
                .code(code).name(name)
                .rarity(CardRarity.SR).status(CardTemplateStatus.ACTIVE)
                .imageFileId(imageFileId)
                .imageUrl(imageFileId != null ? "http://example.com/img/" + imageFileId : null)
                .createdAt(createdAt).updatedAt(LocalDateTime.now())
                .deletedBy("admin").deletedAt(LocalDateTime.now())
                .build();
        deletedPO = cardTemplateDeletedJpaRepository.saveAndFlush(deletedPO);

        RecycleBinItemPO recycleBinPO = RecycleBinItemPO.builder()
                .resourceType(ResourceType.CARD_TEMPLATE).originalId(originalId)
                .originalName(name).deletedBy("admin")
                .deletedAt(LocalDateTime.now()).restoreDeadline(LocalDateTime.now().plusDays(30))
                .build();
        recycleBinPO = recycleBinItemJpaRepository.saveAndFlush(recycleBinPO);
        return recycleBinPO.getId();
    }

    private RecycleBinItemPO persistRecycleBinItem(Long originalId, String originalName) {
        RecycleBinItemPO po = RecycleBinItemPO.builder()
                .resourceType(ResourceType.CARD_TEMPLATE).originalId(originalId)
                .originalName(originalName).deletedBy("admin")
                .deletedAt(LocalDateTime.now()).restoreDeadline(LocalDateTime.now().plusDays(30))
                .build();
        return recycleBinItemJpaRepository.saveAndFlush(po);
    }
}
