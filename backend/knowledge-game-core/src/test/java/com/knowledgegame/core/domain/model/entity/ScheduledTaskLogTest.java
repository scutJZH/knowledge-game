package com.knowledgegame.core.domain.model.entity;

import com.knowledgegame.core.domain.model.domainenum.TaskExecutionStatus;
import com.knowledgegame.core.domain.model.vo.FailureDetail;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * ScheduledTaskLog 领域实体单元测试
 */
class ScheduledTaskLogTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 21, 3, 0, 0);

    @Test
    void constructor_shouldSetAllFields() {
        List<FailureDetail> failures = List.of(
                new FailureDetail(42L, "IP_SERIES", "test", "err"));

        ScheduledTaskLog log = new ScheduledTaskLog(
                1L, "RECYCLE_BIN_CLEANUP", "回收站定时清理",
                NOW, 1234L, 10, 9, 1,
                failures, TaskExecutionStatus.PARTIAL_FAILURE);

        assertEquals(1L, log.getId());
        assertEquals("RECYCLE_BIN_CLEANUP", log.getTaskName());
        assertEquals("回收站定时清理", log.getTaskDisplay());
        assertEquals(NOW, log.getExecutedAt());
        assertEquals(1234L, log.getDurationMs());
        assertEquals(10, log.getTotalCount());
        assertEquals(9, log.getSuccessCount());
        assertEquals(1, log.getFailureCount());
        assertEquals(failures, log.getFailureDetails());
        assertEquals(TaskExecutionStatus.PARTIAL_FAILURE, log.getStatus());
    }

    @Test
    void constructor_shouldAllowNullId() {
        ScheduledTaskLog log = new ScheduledTaskLog(
                null, "T", "T", NOW, 0, 0, 0, 0,
                null, TaskExecutionStatus.SUCCESS);

        assertNull(log.getId());
    }

    @Test
    void constructor_shouldAllowNullFailureDetails() {
        ScheduledTaskLog log = new ScheduledTaskLog(
                1L, "T", "T", NOW, 100L, 5, 5, 0,
                null, TaskExecutionStatus.SUCCESS);

        assertNull(log.getFailureDetails());
        assertEquals(TaskExecutionStatus.SUCCESS, log.getStatus());
        assertEquals(0, log.getFailureCount());
    }

    @Test
    void taskExecutionStatus_shouldHaveThreeValues() {
        TaskExecutionStatus[] values = TaskExecutionStatus.values();
        assertEquals(3, values.length);
    }

    @Test
    void failureDetail_shouldAccessAllFields() {
        FailureDetail fd = new FailureDetail(42L, "IP_SERIES", "火影忍者", "文件服务不可达");

        assertEquals(42L, fd.recycleBinId());
        assertEquals("IP_SERIES", fd.resourceType());
        assertEquals("火影忍者", fd.name());
        assertEquals("文件服务不可达", fd.reason());
    }
}
