package com.knowledgegame.admin.api.controller;

import com.knowledgegame.admin.api.dto.response.RecycleBinItemResponse;
import com.knowledgegame.admin.application.service.RecycleBinAppService;
import com.knowledgegame.admin.config.JacksonConfig;
import com.knowledgegame.components.exception.handler.GlobalExceptionHandler;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.vo.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RecycleBinController WebMvcTest
 * <p>
 * 覆盖：空列表响应、非法 resourceType 异常处理、supported-types 空列表、时间戳序列化。
 */
@WebMvcTest(RecycleBinController.class)
@Import({GlobalExceptionHandler.class, JacksonConfig.class})
@AutoConfigureMockMvc(addFilters = false)
class RecycleBinControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RecycleBinAppService appService;

    @Test
    @DisplayName("GET /api/admin/recycle-bin → 200 + 空列表")
    void list_shouldReturn200WithEmptyList() throws Exception {
        when(appService.list(any())).thenReturn(PageResult.<RecycleBinItemResponse>builder()
                .content(Collections.emptyList())
                .totalElements(0)
                .pageNumber(0)
                .pageSize(20)
                .totalPages(0)
                .build());

        mockMvc.perform(get("/api/admin/recycle-bin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.content").isEmpty());
    }

    @Test
    @DisplayName("GET /api/admin/recycle-bin?resourceType=INVALID → 400")
    void list_withInvalidResourceType_shouldReturn400() throws Exception {
        when(appService.list(any())).thenThrow(new BusinessException(400, "不支持的资源类型: INVALID"));

        mockMvc.perform(get("/api/admin/recycle-bin")
                        .param("resourceType", "INVALID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message", containsString("INVALID")));
    }

    @Test
    @DisplayName("GET /api/admin/recycle-bin/supported-types → 200 + []")
    void supportedTypes_shouldReturn200WithEmptyList() throws Exception {
        when(appService.supportedTypes()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/admin/recycle-bin/supported-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("GET /api/admin/recycle-bin → Long 时间戳序列化验证")
    void list_shouldSerializeTimestampsAsEpochMillis() throws Exception {
        RecycleBinItemResponse mockItem = RecycleBinItemResponse.builder()
                .id(42L)
                .resourceType("IP_SERIES")
                .resourceTypeDisplay("IP 系列")
                .originalId(17L)
                .originalName("测试")
                .originalCreatedAt(1718000000000L)
                .originalUpdatedAt(1718100000000L)
                .deletedBy("admin")
                .deletedAt(1720000000000L)
                .restoreDeadline(1722592000000L)
                .daysUntilPurge(23)
                .build();

        when(appService.list(any())).thenReturn(PageResult.<RecycleBinItemResponse>builder()
                .content(List.of(mockItem))
                .totalElements(1)
                .pageNumber(0)
                .pageSize(20)
                .totalPages(1)
                .build());

        mockMvc.perform(get("/api/admin/recycle-bin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(42))
                .andExpect(jsonPath("$.data.content[0].resourceType").value("IP_SERIES"))
                .andExpect(jsonPath("$.data.content[0].originalCreatedAt").value(1718000000000L))
                .andExpect(jsonPath("$.data.content[0].deletedAt").value(1720000000000L))
                .andExpect(jsonPath("$.data.content[0].daysUntilPurge").value(23));
    }
}
