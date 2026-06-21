package com.knowledgegame.admin.application.scheduler;

import com.knowledgegame.core.domain.model.domainenum.ResourceType;
import com.knowledgegame.core.domain.model.domainenum.TaskExecutionStatus;
import com.knowledgegame.core.domain.model.entity.ScheduledTaskLog;
import com.knowledgegame.core.domain.model.vo.FailureDetail;
import com.knowledgegame.core.domain.port.outbound.ScheduledTaskLogRepositoryPort;
import com.knowledgegame.core.domain.service.recyclebin.RecycleBinItemStrategy;
import com.knowledgegame.core.domain.service.recyclebin.RecycleBinItemStrategyRegistry;
import com.knowledgegame.core.infrastructure.db.entity.RecycleBinItemPO;
import com.knowledgegame.core.infrastructure.db.repository.RecycleBinItemJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RecycleBinCleanupScheduler 单元测试
 * <p>
 * Mock 全部依赖，覆盖 4 种场景。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecycleBinCleanupSchedulerTest {

    @Mock
    private RecycleBinItemJpaRepository jpaRepository;

    @Mock
    private RecycleBinItemStrategyRegistry strategyRegistry;

    @Mock
    private ScheduledTaskLogRepositoryPort logRepositoryPort;

    @Mock
    private RecycleBinItemStrategy<?> strategy;

    private RecycleBinCleanupScheduler scheduler;

    @BeforeEach
    void setUp() throws Exception {
        scheduler = new RecycleBinCleanupScheduler(jpaRepository, strategyRegistry, logRepositoryPort, null);
        java.lang.reflect.Field selfField = RecycleBinCleanupScheduler.class.getDeclaredField("self");
        selfField.setAccessible(true);
        selfField.set(scheduler, scheduler);
    }

    // ===== 场景 1：无过期记录 =====

    @Test
    void shouldWriteZeroLogWhenNoExpiredRecords() {
        when(jpaRepository.findByRestoreDeadlineBefore(any())).thenReturn(List.of());

        scheduler.cleanupExpiredRecords();

        ArgumentCaptor<ScheduledTaskLog> captor = ArgumentCaptor.forClass(ScheduledTaskLog.class);
        verify(logRepositoryPort).save(captor.capture());
        ScheduledTaskLog log = captor.getValue();
        assertEquals(0, log.getTotalCount());
        assertEquals(0, log.getSuccessCount());
        assertEquals(0, log.getFailureCount());
        assertEquals(TaskExecutionStatus.SUCCESS, log.getStatus());
        assertNull(log.getFailureDetails());
    }

    // ===== 场景 2：全部成功 =====

    @Test
    @SuppressWarnings("unchecked")
    void shouldProcessAllAndReportSuccess() {
        when(strategyRegistry.get(any())).thenReturn((RecycleBinItemStrategy) strategy);
        List<RecycleBinItemPO> expired = List.of(
                buildPO(1L, ResourceType.IP_SERIES, "ip1"),
                buildPO(2L, ResourceType.IP_SERIES, "ip2"),
                buildPO(3L, ResourceType.IP_SERIES, "ip3"),
                buildPO(4L, ResourceType.IP_SERIES, "ip4"),
                buildPO(5L, ResourceType.IP_SERIES, "ip5"));
        when(jpaRepository.findByRestoreDeadlineBefore(any())).thenReturn(expired);

        scheduler.cleanupExpiredRecords();

        verify(strategy).purge(1L);
        verify(strategy).purge(2L);
        verify(strategy).purge(3L);
        verify(strategy).purge(4L);
        verify(strategy).purge(5L);

        ArgumentCaptor<ScheduledTaskLog> captor = ArgumentCaptor.forClass(ScheduledTaskLog.class);
        verify(logRepositoryPort).save(captor.capture());
        ScheduledTaskLog log = captor.getValue();
        assertEquals(5, log.getTotalCount());
        assertEquals(5, log.getSuccessCount());
        assertEquals(0, log.getFailureCount());
        assertEquals(TaskExecutionStatus.SUCCESS, log.getStatus());
    }

    // ===== 场景 3：部分失败 =====

    @Test
    @SuppressWarnings("unchecked")
    void shouldContinueProcessingOnPartialFailure() {
        when(strategyRegistry.get(any())).thenReturn((RecycleBinItemStrategy) strategy);
        List<RecycleBinItemPO> expired = List.of(
                buildPO(1L, ResourceType.IP_SERIES, "pass1"),
                buildPO(2L, ResourceType.IP_SERIES, "fail1"),
                buildPO(3L, ResourceType.IP_SERIES, "pass2"),
                buildPO(4L, ResourceType.IP_SERIES, "fail2"),
                buildPO(5L, ResourceType.IP_SERIES, "pass3"));
        when(jpaRepository.findByRestoreDeadlineBefore(any())).thenReturn(expired);
        doThrow(new RuntimeException("文件服务不可达")).when(strategy).purge(2L);
        doThrow(new RuntimeException("详情表已不存在")).when(strategy).purge(4L);

        scheduler.cleanupExpiredRecords();

        verify(strategy).purge(1L);
        verify(strategy).purge(2L);
        verify(strategy).purge(3L);
        verify(strategy).purge(4L);
        verify(strategy).purge(5L);

        ArgumentCaptor<ScheduledTaskLog> captor = ArgumentCaptor.forClass(ScheduledTaskLog.class);
        verify(logRepositoryPort).save(captor.capture());
        ScheduledTaskLog log = captor.getValue();

        assertEquals(5, log.getTotalCount());
        assertEquals(3, log.getSuccessCount());
        assertEquals(2, log.getFailureCount());
        assertEquals(TaskExecutionStatus.PARTIAL_FAILURE, log.getStatus());

        assertNotNull(log.getFailureDetails());
        assertEquals(2, log.getFailureDetails().size());
        FailureDetail fd1 = log.getFailureDetails().get(0);
        assertEquals(2L, fd1.recycleBinId());
        assertEquals("IP_SERIES", fd1.resourceType());
        assertEquals("fail1", fd1.name());
        assertEquals("文件服务不可达", fd1.reason());

        FailureDetail fd2 = log.getFailureDetails().get(1);
        assertEquals(4L, fd2.recycleBinId());
        assertEquals("fail2", fd2.name());
    }

    // ===== 场景 4：全部失败 =====

    @Test
    @SuppressWarnings("unchecked")
    void shouldReportFailureWhenAllFail() {
        when(strategyRegistry.get(any())).thenReturn((RecycleBinItemStrategy) strategy);
        List<RecycleBinItemPO> expired = List.of(
                buildPO(1L, ResourceType.IP_SERIES, "e1"),
                buildPO(2L, ResourceType.IP_SERIES, "e2"));
        when(jpaRepository.findByRestoreDeadlineBefore(any())).thenReturn(expired);
        doThrow(new RuntimeException("err")).when(strategy).purge(anyLong());

        scheduler.cleanupExpiredRecords();

        ArgumentCaptor<ScheduledTaskLog> captor = ArgumentCaptor.forClass(ScheduledTaskLog.class);
        verify(logRepositoryPort).save(captor.capture());
        ScheduledTaskLog log = captor.getValue();

        assertEquals(2, log.getTotalCount());
        assertEquals(0, log.getSuccessCount());
        assertEquals(2, log.getFailureCount());
        assertEquals(TaskExecutionStatus.FAILURE, log.getStatus());
    }

    // ===== helper =====

    private RecycleBinItemPO buildPO(Long id, ResourceType type, String name) {
        return RecycleBinItemPO.builder()
                .id(id)
                .resourceType(type)
                .originalId(id * 10)
                .originalName(name)
                .deletedBy("admin")
                .deletedAt(LocalDateTime.now())
                .restoreDeadline(LocalDateTime.now().minusDays(1))
                .build();
    }
}
