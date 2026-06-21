package com.knowledgegame.core.infrastructure.db.converter;

import com.knowledgegame.core.domain.model.domainenum.TaskExecutionStatus;
import com.knowledgegame.core.domain.model.entity.ScheduledTaskLog;
import com.knowledgegame.core.domain.model.vo.FailureDetail;
import com.knowledgegame.core.infrastructure.db.entity.ScheduledTaskLogPO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * ScheduledTaskLogConverter 单元测试
 * <p>
 * 重点覆盖 JSON 序列化往返，验证 Long 类型不被 Jackson 截断为 Integer。
 */
class ScheduledTaskLogConverterTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 21, 3, 0, 0);

    // ===== toDomain =====

    @Test
    void toDomain_shouldMapAllFields() {
        ScheduledTaskLogPO po = ScheduledTaskLogPO.builder()
                .id(1L)
                .taskName("RECYCLE_BIN_CLEANUP")
                .taskDisplay("回收站定时清理")
                .executedAt(NOW)
                .durationMs(1234L)
                .totalCount(10)
                .successCount(9)
                .failureCount(1)
                .failureDetails("[{\"resourceType\":\"IP_SERIES\",\"name\":\"test\",\"reason\":\"err\",\"recycleBinId\":42}]")
                .status(TaskExecutionStatus.PARTIAL_FAILURE)
                .build();

        ScheduledTaskLog domain = ScheduledTaskLogConverter.INSTANCE.toDomain(po);

        assertEquals(1L, domain.getId());
        assertEquals("RECYCLE_BIN_CLEANUP", domain.getTaskName());
        assertEquals("回收站定时清理", domain.getTaskDisplay());
        assertEquals(NOW, domain.getExecutedAt());
        assertEquals(1234L, domain.getDurationMs());
        assertEquals(10, domain.getTotalCount());
        assertEquals(9, domain.getSuccessCount());
        assertEquals(1, domain.getFailureCount());
        assertEquals(TaskExecutionStatus.PARTIAL_FAILURE, domain.getStatus());

        assertNotNull(domain.getFailureDetails());
        assertEquals(1, domain.getFailureDetails().size());
        FailureDetail fd = domain.getFailureDetails().get(0);
        assertEquals(42L, fd.recycleBinId());
        assertEquals("IP_SERIES", fd.resourceType());
        assertEquals("test", fd.name());
        assertEquals("err", fd.reason());
    }

    @Test
    void toDomain_shouldHandleNullFailureDetails() {
        ScheduledTaskLogPO po = ScheduledTaskLogPO.builder()
                .id(1L)
                .taskName("T")
                .taskDisplay("T")
                .executedAt(NOW)
                .durationMs(0)
                .totalCount(0)
                .successCount(0)
                .failureCount(0)
                .failureDetails(null)
                .status(TaskExecutionStatus.SUCCESS)
                .build();

        ScheduledTaskLog domain = ScheduledTaskLogConverter.INSTANCE.toDomain(po);

        assertNull(domain.getFailureDetails());
    }

    @Test
    void toDomain_shouldHandleBlankFailureDetails() {
        ScheduledTaskLogPO po = ScheduledTaskLogPO.builder()
                .id(1L)
                .taskName("T")
                .taskDisplay("T")
                .executedAt(NOW)
                .durationMs(0)
                .totalCount(0)
                .successCount(0)
                .failureCount(0)
                .failureDetails("  ")
                .status(TaskExecutionStatus.SUCCESS)
                .build();

        ScheduledTaskLog domain = ScheduledTaskLogConverter.INSTANCE.toDomain(po);

        assertNull(domain.getFailureDetails());
    }

    @Test
    void toDomain_shouldHandleMultipleFailureDetails() {
        ScheduledTaskLogPO po = ScheduledTaskLogPO.builder()
                .id(2L)
                .taskName("RECYCLE_BIN_CLEANUP")
                .taskDisplay("回收站定时清理")
                .executedAt(NOW)
                .durationMs(5000L)
                .totalCount(3)
                .successCount(1)
                .failureCount(2)
                .failureDetails("[{\"recycleBinId\":1,\"resourceType\":\"IP_SERIES\",\"name\":\"a\",\"reason\":\"e1\"}"
                        + ",{\"recycleBinId\":2,\"resourceType\":\"CARD_TEMPLATE\",\"name\":\"b\",\"reason\":\"e2\"}]")
                .status(TaskExecutionStatus.PARTIAL_FAILURE)
                .build();

        ScheduledTaskLog domain = ScheduledTaskLogConverter.INSTANCE.toDomain(po);

        assertEquals(2, domain.getFailureDetails().size());
        assertEquals(1L, domain.getFailureDetails().get(0).recycleBinId());
        assertEquals(2L, domain.getFailureDetails().get(1).recycleBinId());
    }

    // ===== toPO =====

    @Test
    void toPO_shouldMapAllFields() {
        ScheduledTaskLog domain = new ScheduledTaskLog(
                null, "RECYCLE_BIN_CLEANUP", "回收站定时清理",
                NOW, 1234L, 10, 9, 1,
                List.of(new FailureDetail(42L, "IP_SERIES", "test", "err")),
                TaskExecutionStatus.PARTIAL_FAILURE);

        ScheduledTaskLogPO po = ScheduledTaskLogConverter.INSTANCE.toPO(domain);

        assertNull(po.getId());
        assertEquals("RECYCLE_BIN_CLEANUP", po.getTaskName());
        assertEquals(NOW, po.getExecutedAt());
        assertEquals(1, po.getFailureCount());
        assertNotNull(po.getFailureDetails());
        // 验证 JSON 字符串可以反序列化回去
        ScheduledTaskLog roundTripped = ScheduledTaskLogConverter.INSTANCE.toDomain(po);
        assertEquals(1, roundTripped.getFailureDetails().size());
        assertEquals(42L, roundTripped.getFailureDetails().get(0).recycleBinId());
    }

    @Test
    void toPO_shouldHandleNullFailureDetails() {
        ScheduledTaskLog domain = new ScheduledTaskLog(
                null, "T", "T", NOW, 0, 0, 0, 0,
                null, TaskExecutionStatus.SUCCESS);

        ScheduledTaskLogPO po = ScheduledTaskLogConverter.INSTANCE.toPO(domain);

        assertNull(po.getFailureDetails());
    }

    // ===== JSON roundtrip: Long preservation =====

    @Test
    void jsonRoundtrip_shouldPreserveLongType() {
        // recycleBinId 值超过 Integer.MAX_VALUE，验证不截断
        long largeId = Integer.MAX_VALUE + 1000L;
        List<FailureDetail> original = List.of(
                new FailureDetail(largeId, "IP_SERIES", "name", "reason"));

        ScheduledTaskLog domain = new ScheduledTaskLog(
                null, "T", "T", NOW, 0, 1, 0, 1,
                original, TaskExecutionStatus.PARTIAL_FAILURE);

        // PO write → read back
        ScheduledTaskLogPO po = ScheduledTaskLogConverter.INSTANCE.toPO(domain);
        ScheduledTaskLog roundTripped = ScheduledTaskLogConverter.INSTANCE.toDomain(po);

        assertEquals(1, roundTripped.getFailureDetails().size());
        FailureDetail fd = roundTripped.getFailureDetails().get(0);
        // 严格类型断言：必须为 Long（非 Integer）
        assertEquals(Long.valueOf(largeId), fd.recycleBinId());
        assertEquals("IP_SERIES", fd.resourceType());
    }

    @Test
    void jsonRoundtrip_shouldPreserveMultipleEntries() {
        List<FailureDetail> original = List.of(
                new FailureDetail(1L, "IP_SERIES", "n1", "r1"),
                new FailureDetail(2L, "QUESTION", "n2", "r2"));

        ScheduledTaskLog domain = new ScheduledTaskLog(
                null, "T", "T", NOW, 0, 2, 0, 2,
                original, TaskExecutionStatus.PARTIAL_FAILURE);

        ScheduledTaskLogPO po = ScheduledTaskLogConverter.INSTANCE.toPO(domain);
        ScheduledTaskLog roundTripped = ScheduledTaskLogConverter.INSTANCE.toDomain(po);

        assertEquals(2, roundTripped.getFailureDetails().size());
        assertEquals(1L, roundTripped.getFailureDetails().get(0).recycleBinId());
        assertEquals(2L, roundTripped.getFailureDetails().get(1).recycleBinId());
    }
}
