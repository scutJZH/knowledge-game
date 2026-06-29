package com.knowledgegame.core.integration;

import com.knowledgegame.core.domain.model.domainenum.GroupRole;
import com.knowledgegame.core.domain.model.domainenum.ReferenceType;
import com.knowledgegame.core.domain.model.domainenum.TxType;
import com.knowledgegame.core.domain.model.entity.GroupMember;
import com.knowledgegame.core.domain.port.outbound.GroupMemberRepository;
import com.knowledgegame.core.domain.port.outbound.PointTransactionRepository;
import com.knowledgegame.core.domain.service.PointTransactionService;
import com.knowledgegame.core.infrastructure.adapter.repoadapter.GroupMemberRepositoryAdapter;
import com.knowledgegame.core.infrastructure.adapter.repoadapter.PointTransactionRepositoryAdapter;
import com.knowledgegame.core.infrastructure.db.entity.GroupMemberPO;
import com.knowledgegame.core.infrastructure.db.repository.PointTransactionJpaRepository;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@EntityScan(basePackages = "com.knowledgegame.core.infrastructure.db.entity")
@EnableJpaRepositories(basePackages = "com.knowledgegame.core.infrastructure.db.repository")
@Import({GroupMemberRepositoryAdapter.class, PointTransactionRepositoryAdapter.class})
class PointTransactionConcurrencyTest {

    @Autowired
    private GroupMemberRepository memberRepo;

    @Autowired
    private PointTransactionRepository txRepo;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private PointTransactionJpaRepository txJpaRepository;

    @Autowired
    private javax.sql.DataSource dataSource;

    private Long groupId;
    private Long userId;

    @BeforeEach
    void setUp() {
        groupId = 1L;
        userId = 10L;

        // 清理上次测试残留数据（@DataJpaTest 回滚不覆盖独立事务提交的数据）
        transactionTemplate.executeWithoutResult(status -> {
            if (memberRepo.existsByGroupIdAndUserId(groupId, userId)) {
                memberRepo.deleteByGroupIdAndUserId(groupId, userId);
            }
            // 清理残留的 point_transaction 记录（无 FK 约束，删 member 不会级联删流水）
            txJpaRepository.deleteAll(txJpaRepository.findAll(
                    (root, query, cb) -> cb.equal(root.get("groupId"), groupId)));
        });

        // 在独立事务中提交测试数据，使后续线程可见
        transactionTemplate.executeWithoutResult(status -> {
            GroupMember member = GroupMember.createOwner(groupId, userId);
            memberRepo.save(member);
            // 设置初始积分 150：先查出已持久化的 entity，再 earnPoints + save
            GroupMember loaded = memberRepo.findByGroupIdAndUserId(groupId, userId).orElseThrow();
            loaded.earnPoints(150, ReferenceType.GAME_REWARD, null);
            memberRepo.save(loaded);
        });
    }

    @AfterEach
    void tearDown() {
        // 清理本次测试提交的数据（走 JDBC 直连，避免 JPA 缓存/事务干扰）
        transactionTemplate.executeWithoutResult(status -> {
            if (memberRepo.existsByGroupIdAndUserId(groupId, userId)) {
                memberRepo.deleteByGroupIdAndUserId(groupId, userId);
            }
        });
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM point_transaction WHERE group_id=" + groupId);
        } catch (Exception e) {
            // 清理失败不阻塞测试
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("并发扣减积分：一个成功一个触发乐观锁冲突")
    void concurrentSpend_oneSucceeds_oneOptimisticLockFailure() {
        PointTransactionService txService = new PointTransactionService(memberRepo, txRepo);
        AtomicReference<Throwable> errorHolder = new AtomicReference<>();

        CompletableFuture<Void> t1 = CompletableFuture.runAsync(() -> {
            try {
                transactionTemplate.executeWithoutResult(status ->
                    txService.record(groupId, userId, TxType.SPEND,
                            100, ReferenceType.GACHA, null));
            } catch (Exception e) {
                // 两个线程之一会触发乐观锁冲突，捕获后存入 errorHolder
                if (errorHolder.get() == null) {
                    errorHolder.set(e);
                }
            }
        });

        CompletableFuture<Void> t2 = CompletableFuture.runAsync(() -> {
            try {
                transactionTemplate.executeWithoutResult(status ->
                    txService.record(groupId, userId, TxType.SPEND,
                            100, ReferenceType.GACHA, null));
            } catch (Exception e) {
                if (errorHolder.get() == null) {
                    errorHolder.set(e);
                }
            }
        });

        CompletableFuture.allOf(t1, t2).join();

        // 验证余额：150 - 100 = 50（只有一次成功扣减）
        transactionTemplate.executeWithoutResult(status -> {
            GroupMember member = memberRepo.findByGroupIdAndUserId(groupId, userId).orElseThrow();
            assertEquals(50, member.getPoints());
        });

        // 验证乐观锁冲突
        Throwable error = errorHolder.get();
        assertNotNull(error, "第二个线程应触发异常");
        if (!containsOptimisticLockException(error)) {
            fail("Expected ObjectOptimisticLockingFailureException but got: "
                    + error.getClass().getName() + ": " + error.getMessage());
        }
    }

    private boolean containsOptimisticLockException(Throwable t) {
        Throwable current = t;
        while (current != null) {
            if (current instanceof ObjectOptimisticLockingFailureException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
