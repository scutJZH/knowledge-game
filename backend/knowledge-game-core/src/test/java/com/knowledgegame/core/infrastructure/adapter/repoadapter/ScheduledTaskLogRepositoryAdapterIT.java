package com.knowledgegame.core.infrastructure.adapter.repoadapter;

import com.knowledgegame.core.domain.model.domainenum.TaskExecutionStatus;
import com.knowledgegame.core.domain.model.entity.ScheduledTaskLog;
import com.knowledgegame.core.domain.model.vo.FailureDetail;
import com.knowledgegame.core.domain.model.vo.PageResult;
import com.knowledgegame.core.infrastructure.db.converter.ScheduledTaskLogConverter;
import com.knowledgegame.core.infrastructure.db.entity.ScheduledTaskLogPO;
import com.knowledgegame.core.infrastructure.db.repository.ScheduledTaskLogJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * ScheduledTaskLogRepositoryAdapter 集成测试
 * <p>
 * 真实 MySQL 验证：DDL 自动生成、save + findAll 端到端、JSON 列读写。
 */
@DataJpaTest
@EntityScan(basePackages = "com.knowledgegame.core.infrastructure.db.entity")
@EnableJpaRepositories(basePackages = "com.knowledgegame.core.infrastructure.db.repository")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ScheduledTaskLogRepositoryAdapter.class)
@ActiveProfiles("test")
class ScheduledTaskLogRepositoryAdapterIT {

    @Autowired
    private ScheduledTaskLogRepositoryAdapter adapter;

    @Autowired
    private TestEntityManager em;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 21, 3, 0, 0);

    @BeforeEach
    void setUp() {
        // 清空测试数据
        em.getEntityManager().createQuery("DELETE FROM ScheduledTaskLogPO").executeUpdate();
    }

    // ===== save =====

    @Test
    void save_shouldPersistWithoutFailureDetails() {
        ScheduledTaskLog domain = new ScheduledTaskLog(
                null, "RECYCLE_BIN_CLEANUP", "回收站定时清理",
                NOW, 500L, 5, 5, 0,
                null, TaskExecutionStatus.SUCCESS);

        adapter.save(domain);

        // 从 DB 读回验证
        ScheduledTaskLogPO saved = em.getEntityManager()
                .createQuery("SELECT s FROM ScheduledTaskLogPO s WHERE s.taskName = 'RECYCLE_BIN_CLEANUP'",
                        ScheduledTaskLogPO.class)
                .getSingleResult();

        assertNotNull(saved.getId());
        assertEquals("回收站定时清理", saved.getTaskDisplay());
        assertEquals(TaskExecutionStatus.SUCCESS, saved.getStatus());
        assertNull(saved.getFailureDetails());
    }

    @Test
    void save_shouldPersistWithFailureDetails() {
        ScheduledTaskLog domain = new ScheduledTaskLog(
                null, "RECYCLE_BIN_CLEANUP", "回收站定时清理",
                NOW, 1000L, 3, 1, 2,
                List.of(
                        new FailureDetail(1L, "IP_SERIES", "name1", "reason1"),
                        new FailureDetail(2L, "QUESTION", "name2", "reason2")),
                TaskExecutionStatus.PARTIAL_FAILURE);

        adapter.save(domain);

        ScheduledTaskLogPO saved = em.getEntityManager()
                .createQuery("SELECT s FROM ScheduledTaskLogPO s WHERE s.taskName = 'RECYCLE_BIN_CLEANUP'",
                        ScheduledTaskLogPO.class)
                .getSingleResult();

        assertEquals(3, saved.getTotalCount());
        assertEquals(1, saved.getSuccessCount());
        assertEquals(2, saved.getFailureCount());
        assertNotNull(saved.getFailureDetails());
        // 验证 JSON 包含正确数据
        ScheduledTaskLog roundTripped = ScheduledTaskLogConverter.INSTANCE.toDomain(saved);
        assertEquals(2, roundTripped.getFailureDetails().size());
        assertEquals(1L, roundTripped.getFailureDetails().get(0).recycleBinId());
    }

    // ===== findAll =====

    @Test
    void findAll_shouldReturnAllWhenTaskNameNull() {
        em.persist(buildPO("TASK_A", "任务A", NOW.minusDays(1)));
        em.persist(buildPO("TASK_B", "任务B", NOW));
        em.flush();

        PageResult<ScheduledTaskLog> result = adapter.findAll(null, 0, 20);

        assertEquals(2, result.getTotalElements());
        assertEquals(2, result.getContent().size());
    }

    @Test
    void findAll_shouldFilterByTaskName() {
        em.persist(buildPO("TASK_A", "任务A", NOW));
        em.persist(buildPO("TASK_B", "任务B", NOW));
        em.flush();

        PageResult<ScheduledTaskLog> result = adapter.findAll("TASK_A", 0, 20);

        assertEquals(1, result.getTotalElements());
        assertEquals("TASK_A", result.getContent().get(0).getTaskName());
    }

    @Test
    void findAll_shouldReturnEmptyWhenNoMatch() {
        em.persist(buildPO("TASK_A", "任务A", NOW));
        em.flush();

        PageResult<ScheduledTaskLog> result = adapter.findAll("NONEXISTENT", 0, 20);

        assertEquals(0, result.getTotalElements());
    }

    @Test
    void findAll_shouldSortByExecutedAtDesc() {
        em.persist(buildPO("TASK", "old", NOW.minusDays(2)));
        em.persist(buildPO("TASK", "new", NOW));
        em.persist(buildPO("TASK", "mid", NOW.minusDays(1)));
        em.flush();

        PageResult<ScheduledTaskLog> result = adapter.findAll(null, 0, 20);

        assertEquals(3, result.getTotalElements());
        assertEquals("new", result.getContent().get(0).getTaskDisplay());
        assertEquals("mid", result.getContent().get(1).getTaskDisplay());
        assertEquals("old", result.getContent().get(2).getTaskDisplay());
    }

    @Test
    void findAll_shouldPaginateCorrectly() {
        for (int i = 0; i < 5; i++) {
            em.persist(buildPO("TASK", "task" + i, NOW.minusDays(i)));
        }
        em.flush();

        PageResult<ScheduledTaskLog> page1 = adapter.findAll(null, 0, 2);
        assertEquals(5, page1.getTotalElements());
        assertEquals(2, page1.getContent().size());
        assertEquals(0, page1.getPageNumber());

        PageResult<ScheduledTaskLog> page2 = adapter.findAll(null, 1, 2);
        assertEquals(2, page2.getContent().size());
        assertEquals(1, page2.getPageNumber());
    }

    // ===== helper =====

    private ScheduledTaskLogPO buildPO(String taskName, String taskDisplay, LocalDateTime executedAt) {
        return ScheduledTaskLogPO.builder()
                .taskName(taskName)
                .taskDisplay(taskDisplay)
                .executedAt(executedAt)
                .durationMs(100)
                .totalCount(1)
                .successCount(1)
                .failureCount(0)
                .failureDetails(null)
                .status(TaskExecutionStatus.SUCCESS)
                .build();
    }
}
