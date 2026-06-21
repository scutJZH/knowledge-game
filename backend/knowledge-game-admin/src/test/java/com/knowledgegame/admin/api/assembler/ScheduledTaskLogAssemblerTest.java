package com.knowledgegame.admin.api.assembler;

import com.knowledgegame.admin.api.dto.response.ScheduledTaskLogResponse;
import com.knowledgegame.core.domain.model.domainenum.TaskExecutionStatus;
import com.knowledgegame.core.domain.model.entity.ScheduledTaskLog;
import com.knowledgegame.core.domain.model.vo.FailureDetail;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * ScheduledTaskLogAssembler 单元测试
 */
class ScheduledTaskLogAssemblerTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 21, 3, 0, 0);

    @Test
    void toResponse_shouldConvertAllFields() {
        ScheduledTaskLog entity = new ScheduledTaskLog(
                1L, "RECYCLE_BIN_CLEANUP", "回收站定时清理",
                NOW, 1234L, 10, 9, 1,
                List.of(new FailureDetail(42L, "IP_SERIES", "test", "err")),
                TaskExecutionStatus.PARTIAL_FAILURE);

        ScheduledTaskLogResponse response = ScheduledTaskLogAssembler.INSTANCE.toResponse(entity);

        assertEquals(1L, response.getId());
        assertEquals("RECYCLE_BIN_CLEANUP", response.getTaskName());
        assertEquals("回收站定时清理", response.getTaskDisplay());
        assertEquals(NOW.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), response.getExecutedAt());
        assertEquals(1234L, response.getDurationMs());
        assertEquals(10, response.getTotalCount());
        assertEquals(9, response.getSuccessCount());
        assertEquals(1, response.getFailureCount());
        assertEquals("PARTIAL_FAILURE", response.getStatus());
        assertNotNull(response.getFailureDetails());
    }

    @Test
    void toResponse_shouldHandleNullTime() {
        ScheduledTaskLog entity = new ScheduledTaskLog(
                1L, "T", "T", null, 0, 0, 0, 0,
                null, TaskExecutionStatus.SUCCESS);

        ScheduledTaskLogResponse response = ScheduledTaskLogAssembler.INSTANCE.toResponse(entity);

        assertNull(response.getExecutedAt());
    }

    @Test
    void toResponse_shouldHandleNullFailureDetails() {
        ScheduledTaskLog entity = new ScheduledTaskLog(
                1L, "T", "T", NOW, 0, 0, 0, 0,
                null, TaskExecutionStatus.SUCCESS);

        ScheduledTaskLogResponse response = ScheduledTaskLogAssembler.INSTANCE.toResponse(entity);

        assertNull(response.getFailureDetails());
    }

    @Test
    void toResponse_shouldConvertEpochMilliCorrectly() {
        LocalDateTime time = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
        long expectedMs = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        ScheduledTaskLog entity = new ScheduledTaskLog(
                1L, "T", "T", time, 0, 0, 0, 0,
                null, TaskExecutionStatus.SUCCESS);

        ScheduledTaskLogResponse response = ScheduledTaskLogAssembler.INSTANCE.toResponse(entity);

        assertEquals(expectedMs, response.getExecutedAt());
    }
}
