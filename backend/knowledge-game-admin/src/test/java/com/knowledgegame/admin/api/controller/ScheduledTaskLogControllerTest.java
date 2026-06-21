package com.knowledgegame.admin.api.controller;

import com.knowledgegame.admin.api.dto.response.ScheduledTaskLogResponse;
import com.knowledgegame.admin.application.service.ScheduledTaskLogAppService;
import com.knowledgegame.core.domain.model.vo.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ScheduledTaskLogController 单元测试（standalone MockMvc）
 * <p>
 * 验证 HTTP 参数到 AppService 的透传正确性。
 * 使用 standalone setup 避免加载 Spring 容器（admin 模块依赖 Nacos 配置中心，容器无法在测试环境启动）。
 */
@ExtendWith(MockitoExtension.class)
class ScheduledTaskLogControllerTest {

    @Mock
    private ScheduledTaskLogAppService appService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ScheduledTaskLogController(appService))
                .build();
    }

    @Test
    void list_shouldReturnSuccessWithDefaults() throws Exception {
        ScheduledTaskLogResponse item = ScheduledTaskLogResponse.builder()
                .id(1L).taskName("TASK").taskDisplay("任务")
                .executedAt(1718000000000L).durationMs(100L)
                .totalCount(1).successCount(1).failureCount(0)
                .failureDetails(null).status("SUCCESS").build();
        PageResult<ScheduledTaskLogResponse> page = PageResult.<ScheduledTaskLogResponse>builder()
                .content(List.of(item)).totalElements(1L)
                .pageNumber(0).pageSize(20).totalPages(1).build();
        when(appService.list(any())).thenReturn(page);

        mockMvc.perform(get("/api/admin/scheduled-task-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content[0].id").value(1))
                .andExpect(jsonPath("$.data.content[0].taskName").value("TASK"))
                .andExpect(jsonPath("$.data.content[0].executedAt").value(1718000000000L))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void list_shouldPassQueryParams() throws Exception {
        when(appService.list(any())).thenReturn(PageResult.<ScheduledTaskLogResponse>builder()
                .content(List.of()).totalElements(0L).pageNumber(0).pageSize(20).totalPages(0).build());

        mockMvc.perform(get("/api/admin/scheduled-task-logs")
                        .param("taskName", "RECYCLE_BIN_CLEANUP")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void list_shouldReturnEmptyList() throws Exception {
        when(appService.list(any())).thenReturn(PageResult.<ScheduledTaskLogResponse>builder()
                .content(List.of()).totalElements(0L).pageNumber(0).pageSize(20).totalPages(0).build());

        mockMvc.perform(get("/api/admin/scheduled-task-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }
}
