package com.knowledgegame.core.infrastructure.adapter.repoadapter;

import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.domainenum.ReferenceType;
import com.knowledgegame.core.domain.model.domainenum.TxType;
import com.knowledgegame.core.domain.model.entity.PointTransaction;
import com.knowledgegame.core.domain.model.vo.PageResult;
import com.knowledgegame.core.domain.model.vo.SortField;
import com.knowledgegame.core.infrastructure.db.entity.PointTransactionPO;
import com.knowledgegame.core.infrastructure.db.repository.PointTransactionJpaRepository;
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

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@EntityScan(basePackages = "com.knowledgegame.core.infrastructure.db.entity")
@EnableJpaRepositories(basePackages = "com.knowledgegame.core.infrastructure.db.repository")
@Import(PointTransactionRepositoryAdapter.class)
class PointTransactionRepositoryAdapterTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PointTransactionRepositoryAdapter adapter;

    @Autowired
    private PointTransactionJpaRepository jpaRepository;

    @Autowired
    private DataSource dataSource;

    private LocalDateTime now;
    private Long groupId;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now();
        groupId = 1L;

        // user=10 / EARN / 100 / GAME_REWARD / T-5d
        jpaRepository.save(PointTransactionPO.builder()
                .groupId(groupId).userId(10L).type(TxType.EARN).amount(100)
                .referenceType(ReferenceType.GAME_REWARD).referenceId(null).balanceAfter(100)
                .createdAt(now.minusDays(5)).build());
        // user=10 / SPEND / 50 / GACHA / T-4d
        jpaRepository.save(PointTransactionPO.builder()
                .groupId(groupId).userId(10L).type(TxType.SPEND).amount(50)
                .referenceType(ReferenceType.GACHA).referenceId(null).balanceAfter(50)
                .createdAt(now.minusDays(4)).build());
        // user=10 / EARN / 30 / CHECK_IN / T-3d
        jpaRepository.save(PointTransactionPO.builder()
                .groupId(groupId).userId(10L).type(TxType.EARN).amount(30)
                .referenceType(ReferenceType.CHECK_IN).referenceId(null).balanceAfter(80)
                .createdAt(now.minusDays(3)).build());
        // user=11 / EARN / 200 / GAME_REWARD / T-3d
        jpaRepository.save(PointTransactionPO.builder()
                .groupId(groupId).userId(11L).type(TxType.EARN).amount(200)
                .referenceType(ReferenceType.GAME_REWARD).referenceId(null).balanceAfter(200)
                .createdAt(now.minusDays(3)).build());
        // user=11 / SPEND / 80 / PURCHASE / T-2d
        jpaRepository.save(PointTransactionPO.builder()
                .groupId(groupId).userId(11L).type(TxType.SPEND).amount(80)
                .referenceType(ReferenceType.PURCHASE).referenceId(null).balanceAfter(120)
                .createdAt(now.minusDays(2)).build());
        // user=10 / EARN / 60 / FLIP_REWARD / T-1d
        jpaRepository.save(PointTransactionPO.builder()
                .groupId(groupId).userId(10L).type(TxType.EARN).amount(60)
                .referenceType(ReferenceType.FLIP_REWARD).referenceId(null).balanceAfter(140)
                .createdAt(now.minusDays(1)).build());

        entityManager.flush();
        entityManager.clear();
    }

    // ==================== save tests ====================

    @Test
    @DisplayName("save 正常保存 → jpaRepository.findById 返回字段全等")
    void save_shouldPersistAllFields() {
        PointTransaction tx = PointTransaction.record(groupId, 10L, TxType.EARN,
                50, ReferenceType.CHECK_IN, null, 190);

        PointTransaction saved = adapter.save(tx);

        assertNotNull(saved.getId());
        assertEquals(groupId, saved.getGroupId());
        assertEquals(10L, saved.getUserId());
        assertEquals(TxType.EARN, saved.getType());
        assertEquals(50, saved.getAmount());
        assertEquals(ReferenceType.CHECK_IN, saved.getReferenceType());
        assertNull(saved.getReferenceId());
        assertEquals(190, saved.getBalanceAfter());
        assertNotNull(saved.getCreatedAt());

        // 验证数据库持久化
        entityManager.flush();
        entityManager.clear();
        PointTransactionPO po = jpaRepository.findById(saved.getId()).orElseThrow();
        assertEquals(TxType.EARN, po.getType());
        assertEquals(50, po.getAmount());
        assertEquals(ReferenceType.CHECK_IN, po.getReferenceType());
        assertNull(po.getReferenceId());
        assertEquals(190, po.getBalanceAfter());
    }

    @Test
    @DisplayName("save referenceId 非 null → 字段正确持久化")
    void save_shouldPersistNonNullReferenceId() {
        PointTransaction tx = PointTransaction.record(groupId, 10L, TxType.SPEND,
                20, ReferenceType.GACHA, 42L, 170);

        PointTransaction saved = adapter.save(tx);

        assertEquals(42L, saved.getReferenceId());

        entityManager.flush();
        entityManager.clear();
        PointTransactionPO po = jpaRepository.findById(saved.getId()).orElseThrow();
        assertEquals(42L, po.getReferenceId());
    }

    // ==================== findByGroup tests ====================

    @Test
    @DisplayName("findByGroup 仅 groupId → 返回 6 条")
    void findByGroup_onlyGroupId_returnsAll() {
        PageResult<PointTransaction> page = adapter.findByGroup(groupId, null,
                null, null, null, null, null, 0, 20);

        assertEquals(6, page.getContent().size());
        assertEquals(6L, page.getTotalElements());
    }

    @Test
    @DisplayName("findByGroup groupId + userId=10 → 返回 4 条")
    void findByGroup_groupIdAndUser10_returns4() {
        PageResult<PointTransaction> page = adapter.findByGroup(groupId, 10L,
                null, null, null, null, null, 0, 20);

        assertEquals(4, page.getContent().size());
        assertEquals(4L, page.getTotalElements());
        page.getContent().forEach(tx -> assertEquals(10L, tx.getUserId()));
    }

    @Test
    @DisplayName("findByGroup groupId + userId=11 → 返回 2 条")
    void findByGroup_groupIdAndUser11_returns2() {
        PageResult<PointTransaction> page = adapter.findByGroup(groupId, 11L,
                null, null, null, null, null, 0, 20);

        assertEquals(2, page.getContent().size());
        assertEquals(2L, page.getTotalElements());
        page.getContent().forEach(tx -> assertEquals(11L, tx.getUserId()));
    }

    @Test
    @DisplayName("findByGroup groupId + type=EARN → 返回 4 条")
    void findByGroup_groupIdAndEarnType_returns4() {
        PageResult<PointTransaction> page = adapter.findByGroup(groupId, null,
                TxType.EARN, null, null, null, null, 0, 20);

        assertEquals(4, page.getContent().size());
        assertEquals(4L, page.getTotalElements());
        page.getContent().forEach(tx -> assertEquals(TxType.EARN, tx.getType()));
    }

    @Test
    @DisplayName("findByGroup groupId + userId=10 + type=EARN → 返回 3 条")
    void findByGroup_groupUser10AndEarn_returns3() {
        PageResult<PointTransaction> page = adapter.findByGroup(groupId, 10L,
                TxType.EARN, null, null, null, null, 0, 20);

        assertEquals(3, page.getContent().size());
        assertEquals(3L, page.getTotalElements());
        page.getContent().forEach(tx -> {
            assertEquals(10L, tx.getUserId());
            assertEquals(TxType.EARN, tx.getType());
        });
    }

    @Test
    @DisplayName("findByGroup groupId + userId=10 + type=EARN + dateRange(T-4d~T-2d) → 返回 1 条 CHECK_IN")
    void findByGroup_user10EarnDateRange_returns1() {
        LocalDateTime start = now.minusDays(4);
        LocalDateTime end = now.minusDays(2);

        PageResult<PointTransaction> page = adapter.findByGroup(groupId, 10L,
                TxType.EARN, null, start, end, null, 0, 20);

        assertEquals(1, page.getContent().size());
        assertEquals(1L, page.getTotalElements());
        PointTransaction tx = page.getContent().get(0);
        assertEquals(10L, tx.getUserId());
        assertEquals(TxType.EARN, tx.getType());
        assertEquals(ReferenceType.CHECK_IN, tx.getReferenceType());
    }

    // ==================== findByUser tests ====================

    @Test
    @DisplayName("findByUser userId=10 不传 groupId → 返回 4 条")
    void findByUser_user10NoGroup_returns4() {
        PageResult<PointTransaction> page = adapter.findByUser(10L, null,
                null, null, null, null, null, 0, 20);

        assertEquals(4, page.getContent().size());
        assertEquals(4L, page.getTotalElements());
        page.getContent().forEach(tx -> assertEquals(10L, tx.getUserId()));
    }

    @Test
    @DisplayName("findByUser userId=11 不传 groupId → 返回 2 条")
    void findByUser_user11NoGroup_returns2() {
        PageResult<PointTransaction> page = adapter.findByUser(11L, null,
                null, null, null, null, null, 0, 20);

        assertEquals(2, page.getContent().size());
        assertEquals(2L, page.getTotalElements());
        page.getContent().forEach(tx -> assertEquals(11L, tx.getUserId()));
    }

    // ==================== sort whitelist tests ====================

    @Test
    @DisplayName("sortField=createdAt ASC → 第 1 条最早（T-5d）")
    void sortByCreatedAtAsc_firstIsEarliest() {
        SortField sortField = new SortField("createdAt", SortField.Direction.ASC);

        PageResult<PointTransaction> page = adapter.findByGroup(groupId, null,
                null, null, null, null, sortField, 0, 20);

        assertEquals(6, page.getContent().size());
        // 最早创建的应该是 T-5d (first inserted)
        assertTrue(page.getContent().get(0).getCreatedAt()
                .isBefore(page.getContent().get(5).getCreatedAt()));
    }

    @Test
    @DisplayName("sortField=amount DESC → 按 amount 倒序")
    void sortByAmountDesc_largestFirst() {
        SortField sortField = new SortField("amount", SortField.Direction.DESC);

        PageResult<PointTransaction> page = adapter.findByGroup(groupId, null,
                null, null, null, null, sortField, 0, 20);

        assertEquals(6, page.getContent().size());
        // 最大 amount=200，应排第 1
        assertEquals(200, page.getContent().get(0).getAmount());
        // 最小 amount=30，应排最后
        assertEquals(30, page.getContent().get(5).getAmount());
    }

    @Test
    @DisplayName("sortField=invalid → 抛 BusinessException 含不支持的排序字段")
    void sortByInvalidField_throwsBusinessException() {
        SortField sortField = new SortField("invalid", SortField.Direction.ASC);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                adapter.findByGroup(groupId, null, null, null, null, null, sortField, 0, 20));

        assertTrue(ex.getMessage().contains("不支持的排序字段"));
        assertTrue(ex.getMessage().contains("invalid"));
    }

    // ==================== pagination test ====================

    @Test
    @DisplayName("分页 size=2 → 返回 2 条 + totalElements=6 + totalPages=3")
    void paginationSize2_returnsCorrectPage() {
        PageResult<PointTransaction> page = adapter.findByGroup(groupId, null,
                null, null, null, null, null, 0, 2);

        assertEquals(2, page.getContent().size());
        assertEquals(6L, page.getTotalElements());
        assertEquals(3, page.getTotalPages());
    }

    // ==================== EXPLAIN index verification ====================

    @Test
    @DisplayName("索引验证：information_schema 确认 4 个预期索引存在")
    void verifyIndexesExist() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT DISTINCT INDEX_NAME FROM information_schema.STATISTICS "
                             + "WHERE TABLE_SCHEMA='knowledge-game' AND TABLE_NAME='point_transaction' "
                             + "ORDER BY INDEX_NAME")) {

            List<String> indexes = new java.util.ArrayList<>();
            while (rs.next()) {
                String name = rs.getString("INDEX_NAME");
                if (!"PRIMARY".equals(name)) {
                    indexes.add(name);
                }
            }

            assertEquals(4, indexes.size(), "Should have exactly 4 secondary indexes");
            assertTrue(indexes.contains("idx_group_user_created"), "Missing idx_group_user_created");
            assertTrue(indexes.contains("idx_user_created"), "Missing idx_user_created");
            assertTrue(indexes.contains("idx_group_created"), "Missing idx_group_created");
            assertTrue(indexes.contains("idx_reference"), "Missing idx_reference");
        }
    }
}
