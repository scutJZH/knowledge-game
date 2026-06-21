package com.knowledgegame.core.infrastructure.db.repository;

import com.knowledgegame.core.domain.model.domainenum.ResourceType;
import com.knowledgegame.core.infrastructure.db.entity.RecycleBinItemPO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RecycleBinItemJpaRepository 集成测试（REQ-101 findByRestoreDeadlineBefore 方法）
 */
@DataJpaTest
@EntityScan(basePackages = "com.knowledgegame.core.infrastructure.db.entity")
@EnableJpaRepositories(basePackages = "com.knowledgegame.core.infrastructure.db.repository")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class RecycleBinItemJpaRepositoryIT {

    @Autowired
    private RecycleBinItemJpaRepository jpaRepository;

    @Autowired
    private TestEntityManager em;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 21, 12, 0, 0);

    @BeforeEach
    void setUp() {
        em.getEntityManager().createQuery("DELETE FROM RecycleBinItemPO").executeUpdate();
    }

    @Test
    void findByRestoreDeadlineBefore_shouldReturnExpiredRecords() {
        em.persist(buildPO("expired", 10L, NOW.minusDays(1)));
        em.persist(buildPO("notExpired", 20L, NOW.plusDays(10)));
        em.flush();

        List<RecycleBinItemPO> result = jpaRepository.findByRestoreDeadlineBefore(NOW);

        assertEquals(1, result.size());
        assertEquals("expired", result.get(0).getOriginalName());
    }

    @Test
    void findByRestoreDeadlineBefore_shouldReturnEmptyWhenNoExpired() {
        em.persist(buildPO("future", 30L, NOW.plusDays(5)));
        em.flush();

        List<RecycleBinItemPO> result = jpaRepository.findByRestoreDeadlineBefore(NOW);

        assertTrue(result.isEmpty());
    }

    @Test
    void findByRestoreDeadlineBefore_shouldNotReturnExactDeadline() {
        em.persist(buildPO("exact", 40L, NOW));
        em.flush();

        List<RecycleBinItemPO> result = jpaRepository.findByRestoreDeadlineBefore(NOW);

        // BEFORE 语义不含等号 — 恰好等于 NOW 的不应返回
        assertTrue(result.isEmpty());
    }

    @Test
    void findByRestoreDeadlineBefore_shouldReturnMultipleExpired() {
        em.persist(buildPO("e1", 50L, NOW.minusDays(3)));
        em.persist(buildPO("future", 60L, NOW.plusDays(1)));
        em.persist(buildPO("e2", 70L, NOW.minusDays(1)));
        em.flush();

        List<RecycleBinItemPO> result = jpaRepository.findByRestoreDeadlineBefore(NOW);

        assertEquals(2, result.size());
    }

    private RecycleBinItemPO buildPO(String name, long originalId, LocalDateTime restoreDeadline) {
        RecycleBinItemPO po = RecycleBinItemPO.builder()
                .resourceType(ResourceType.IP_SERIES)
                .originalId(originalId)
                .originalName(name)
                .deletedBy("test")
                .deletedAt(NOW)
                .restoreDeadline(restoreDeadline)
                .build();
        return po;
    }
}
