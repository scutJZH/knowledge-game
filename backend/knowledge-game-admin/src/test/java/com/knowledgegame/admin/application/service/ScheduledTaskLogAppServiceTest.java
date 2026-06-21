package com.knowledgegame.admin.application.service;

import com.knowledgegame.admin.api.dto.request.ScheduledTaskLogListRequest;
import com.knowledgegame.admin.api.dto.response.ScheduledTaskLogResponse;
import com.knowledgegame.core.domain.model.domainenum.TaskExecutionStatus;
import com.knowledgegame.core.domain.model.entity.ScheduledTaskLog;
import com.knowledgegame.core.domain.model.vo.PageResult;
import com.knowledgegame.core.domain.port.outbound.ScheduledTaskLogRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScheduledTaskLogAppService 单元测试
 * <p>
 * Mock ScheduledTaskLogRepositoryPort，验证分页参数透传。
 */
@ExtendWith(MockitoExtension.class)
class ScheduledTaskLogAppServiceTest {

    @Mock
    private ScheduledTaskLogRepositoryPort repositoryPort;

    @InjectMocks
    private ScheduledTaskLogAppService appService;

    static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 21, 3, 0, 0);

    @Test
    void list_shouldUseDefaults() {
        ScheduledTaskLogListRequest request = new ScheduledTaskLogListRequest();
        when(repositoryPort.findAll(isNull(), eq(0), eq(20)))
                .thenReturn(PageResult.<ScheduledTaskLog>builder()
                        .content(List.of()).totalElements(0L).pageNumber(0).pageSize(20).totalPages(0).build());

        appService.list(request);

        verify(repositoryPort).findAll(null, 0, 20);
    }

    @Test
    void list_shouldUseProvidedPageAndSize() {
        ScheduledTaskLogListRequest request = new ScheduledTaskLogListRequest();
        request.setPage(2);
        request.setSize(10);
        when(repositoryPort.findAll(isNull(), eq(2), eq(10)))
                .thenReturn(PageResult.<ScheduledTaskLog>builder()
                        .content(List.of()).totalElements(0L).pageNumber(2).pageSize(10).totalPages(0).build());

        appService.list(request);

        verify(repositoryPort).findAll(null, 2, 10);
    }

    @Test
    void list_shouldPassTaskNameFilter() {
        ScheduledTaskLogListRequest request = new ScheduledTaskLogListRequest();
        request.setTaskName("RECYCLE_BIN_CLEANUP");
        request.setPage(0);
        request.setSize(20);
        when(repositoryPort.findAll(eq("RECYCLE_BIN_CLEANUP"), eq(0), eq(20)))
                .thenReturn(PageResult.<ScheduledTaskLog>builder()
                        .content(List.of()).totalElements(0L).pageNumber(0).pageSize(20).totalPages(0).build());

        appService.list(request);

        verify(repositoryPort).findAll("RECYCLE_BIN_CLEANUP", 0, 20);
    }

    @Test
    void list_shouldMapEntitiesToResponse() {
        ScheduledTaskLogListRequest request = new ScheduledTaskLogListRequest();
        request.setPage(0);
        request.setSize(20);
        ScheduledTaskLog entity = new ScheduledTaskLog(
                1L, "TASK", "任务", NOW, 100L, 1, 1, 0,
                null, TaskExecutionStatus.SUCCESS);
        PageResult<ScheduledTaskLog> pageResult = PageResult.<ScheduledTaskLog>builder()
                .content(List.of(entity)).totalElements(1L)
                .pageNumber(0).pageSize(20).totalPages(1).build();
        when(repositoryPort.findAll(isNull(), eq(0), eq(20))).thenReturn(pageResult);

        PageResult<ScheduledTaskLogResponse> result = appService.list(request);

        assertEquals(1, result.getContent().size());
        assertEquals("TASK", result.getContent().get(0).getTaskName());
        assertEquals(1L, result.getTotalElements());
    }
}
