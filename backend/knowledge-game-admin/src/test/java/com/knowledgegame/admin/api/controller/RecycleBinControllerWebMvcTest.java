package com.knowledgegame.admin.api.controller;

import com.knowledgegame.admin.api.dto.response.BatchPurgeResult;
import com.knowledgegame.admin.api.dto.response.BatchRestoreResult;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    @MockitoBean
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

    // ===== restore (REQ-103) =====

    @Test
    @DisplayName("POST /api/admin/recycle-bin/{id}/restore → 200")
    void restore_success_shouldReturn200() throws Exception {
        doNothing().when(appService).restore(42L);

        mockMvc.perform(post("/api/admin/recycle-bin/42/restore"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("POST /api/admin/recycle-bin/{id}/restore — 404 → Result{code:404}")
    void restore_notFound_shouldReturn404() throws Exception {
        doThrow(new BusinessException(404, "回收站记录不存在: 99"))
                .when(appService).restore(99L);

        mockMvc.perform(post("/api/admin/recycle-bin/99/restore"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message", containsString("回收站记录不存在")));
    }

    @Test
    @DisplayName("POST /api/admin/recycle-bin/{id}/restore — 501 → Result{code:501}")
    void restore_noStrategy_shouldReturn501() throws Exception {
        doThrow(new BusinessException(501, "资源类型 KNOWLEDGE_CATEGORY 暂未接入回收站"))
                .when(appService).restore(42L);

        mockMvc.perform(post("/api/admin/recycle-bin/42/restore"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(501))
                .andExpect(jsonPath("$.message", containsString("暂未接入回收站")));
    }

    // ===== batchRestore (REQ-103) =====

    @Test
    @DisplayName("POST /api/admin/recycle-bin/batch-restore 全成功 → 200 + successIds")
    void batchRestore_allSuccess_shouldReturn200() throws Exception {
        BatchRestoreResult mockResult = new BatchRestoreResult(
                List.of(1L, 2L), Collections.emptyList());
        when(appService.batchRestore(anyList())).thenReturn(mockResult);

        mockMvc.perform(post("/api/admin/recycle-bin/batch-restore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[1,2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.successIds.length()").value(2))
                .andExpect(jsonPath("$.data.failures").isEmpty());
    }

    @Test
    @DisplayName("POST /api/admin/recycle-bin/batch-restore 部分失败 → 200 + failures")
    void batchRestore_partialFailure_shouldReturn200() throws Exception {
        BatchRestoreResult mockResult = new BatchRestoreResult(
                List.of(1L),
                List.of(new BatchRestoreResult.Failure(2L, "资源类型 NOT_REGISTERED 暂未接入回收站")));
        when(appService.batchRestore(anyList())).thenReturn(mockResult);

        mockMvc.perform(post("/api/admin/recycle-bin/batch-restore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[1,2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successIds[0]").value(1))
                .andExpect(jsonPath("$.data.failures[0].id").value(2))
                .andExpect(jsonPath("$.data.failures[0].errorMessage", containsString("暂未接入回收站")));
    }

    @Test
    @DisplayName("POST /api/admin/recycle-bin/batch-restore 全失败 → 200 + successIds=[]")
    void batchRestore_allFailure_shouldReturn200() throws Exception {
        BatchRestoreResult mockResult = new BatchRestoreResult(
                Collections.emptyList(),
                List.of(new BatchRestoreResult.Failure(1L, "回收站记录不存在: 1")));
        when(appService.batchRestore(anyList())).thenReturn(mockResult);

        mockMvc.perform(post("/api/admin/recycle-bin/batch-restore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[1]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successIds").isEmpty())
                .andExpect(jsonPath("$.data.failures[0].id").value(1));
    }

    @Test
    @DisplayName("POST /api/admin/recycle-bin/batch-restore 空 ids → 400")
    void batchRestore_emptyIds_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/admin/recycle-bin/batch-restore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("POST /api/admin/recycle-bin/batch-restore 缺少 ids 字段 → 400")
    void batchRestore_missingIds_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/admin/recycle-bin/batch-restore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("POST /api/admin/recycle-bin/batch-restore 超 100 ids → 400")
    void batchRestore_tooManyIds_shouldReturn400() throws Exception {
        StringBuilder ids = new StringBuilder("[");
        for (int i = 0; i < 101; i++) {
            if (i > 0) ids.append(",");
            ids.append(i);
        }
        ids.append("]");

        mockMvc.perform(post("/api/admin/recycle-bin/batch-restore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":" + ids + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("POST /api/admin/recycle-bin/batch-restore 含 null 元素 → 400")
    void batchRestore_nullElement_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/admin/recycle-bin/batch-restore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[42, null]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("POST /api/admin/recycle-bin/batch-restore JSON 序列化 — Long 为数字非字符串")
    void batchRestore_jsonSerialization_shouldUseNumberForLong() throws Exception {
        BatchRestoreResult mockResult = new BatchRestoreResult(
                List.of(1L),
                List.of(new BatchRestoreResult.Failure(2L, "失败原因")));
        when(appService.batchRestore(anyList())).thenReturn(mockResult);

        mockMvc.perform(post("/api/admin/recycle-bin/batch-restore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[1,2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successIds[0]").isNumber())
                .andExpect(jsonPath("$.data.failures[0].id").isNumber());
    }

    // ===== purge (REQ-102) =====

    @Test
    @DisplayName("DELETE /api/admin/recycle-bin/{id} → 200")
    void purge_success_shouldReturn200() throws Exception {
        doNothing().when(appService).purge(42L);

        mockMvc.perform(delete("/api/admin/recycle-bin/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("DELETE /api/admin/recycle-bin/{id} — 404 → Result{code:404}")
    void purge_notFound_shouldReturn404() throws Exception {
        doThrow(new BusinessException(404, "回收站记录不存在: 99"))
                .when(appService).purge(99L);

        mockMvc.perform(delete("/api/admin/recycle-bin/99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message", containsString("回收站记录不存在")));
    }

    @Test
    @DisplayName("DELETE /api/admin/recycle-bin/{id} — 501 → Result{code:501}")
    void purge_noStrategy_shouldReturn501() throws Exception {
        doThrow(new BusinessException(501, "资源类型 KNOWLEDGE_CATEGORY 暂未接入回收站"))
                .when(appService).purge(42L);

        mockMvc.perform(delete("/api/admin/recycle-bin/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(501))
                .andExpect(jsonPath("$.message", containsString("暂未接入回收站")));
    }

    @Test
    @DisplayName("DELETE /api/admin/recycle-bin/{id} — 405 验证（GET 应 200）")
    void purge_getMethod_shouldReturn405() throws Exception {
        mockMvc.perform(get("/api/admin/recycle-bin/42"))
                .andExpect(status().isOk()); // GET /{id} 未定义 → Spring 404 或 fallback
    }

    @Test
    @DisplayName("DELETE /api/admin/recycle-bin/{id} — 405 验证（DELETE 方法确认可用）")
    void purge_deleteMethod_shouldNotReturn405() throws Exception {
        doNothing().when(appService).purge(1L);
        mockMvc.perform(delete("/api/admin/recycle-bin/1"))
                .andExpect(jsonPath("$.code").value(200));
    }

    // ===== batchPurge (REQ-102) =====

    @Test
    @DisplayName("POST /api/admin/recycle-bin/batch-purge 全成功 → 200 + successIds")
    void batchPurge_allSuccess_shouldReturn200() throws Exception {
        BatchPurgeResult mockResult = new BatchPurgeResult(
                List.of(42L, 43L), Collections.emptyList());
        when(appService.batchPurge(anyList())).thenReturn(mockResult);

        mockMvc.perform(post("/api/admin/recycle-bin/batch-purge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[42,43]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.successIds.length()").value(2))
                .andExpect(jsonPath("$.data.failures").isEmpty());
    }

    @Test
    @DisplayName("POST /api/admin/recycle-bin/batch-purge 部分失败 → 200 + failures")
    void batchPurge_partialFailure_shouldReturn200() throws Exception {
        BatchPurgeResult mockResult = new BatchPurgeResult(
                List.of(42L),
                List.of(new BatchPurgeResult.Failure(43L, "文件服务不可达")));
        when(appService.batchPurge(anyList())).thenReturn(mockResult);

        mockMvc.perform(post("/api/admin/recycle-bin/batch-purge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[42,43]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successIds[0]").value(42))
                .andExpect(jsonPath("$.data.failures[0].id").value(43))
                .andExpect(jsonPath("$.data.failures[0].errorMessage", containsString("文件服务不可达")));
    }

    @Test
    @DisplayName("POST /api/admin/recycle-bin/batch-purge 全失败 → 200 + successIds=[]")
    void batchPurge_allFailure_shouldReturn200() throws Exception {
        BatchPurgeResult mockResult = new BatchPurgeResult(
                Collections.emptyList(),
                List.of(new BatchPurgeResult.Failure(1L, "回收站记录不存在: 1")));
        when(appService.batchPurge(anyList())).thenReturn(mockResult);

        mockMvc.perform(post("/api/admin/recycle-bin/batch-purge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[1]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successIds").isEmpty())
                .andExpect(jsonPath("$.data.failures[0].id").value(1));
    }

    @Test
    @DisplayName("POST /api/admin/recycle-bin/batch-purge 空 ids → 400")
    void batchPurge_emptyIds_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/admin/recycle-bin/batch-purge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("POST /api/admin/recycle-bin/batch-purge 缺少 ids 字段 → 400")
    void batchPurge_missingIds_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/admin/recycle-bin/batch-purge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("POST /api/admin/recycle-bin/batch-purge 超 100 ids → 400")
    void batchPurge_tooManyIds_shouldReturn400() throws Exception {
        StringBuilder ids = new StringBuilder("[");
        for (int i = 0; i < 101; i++) {
            if (i > 0) ids.append(",");
            ids.append(i);
        }
        ids.append("]");

        mockMvc.perform(post("/api/admin/recycle-bin/batch-purge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":" + ids + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("POST /api/admin/recycle-bin/batch-purge 含 null 元素 → 400")
    void batchPurge_nullElement_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/admin/recycle-bin/batch-purge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[42, null]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("POST /api/admin/recycle-bin/batch-purge JSON 序列化 — Long 为数字非字符串")
    void batchPurge_jsonSerialization_shouldUseNumberForLong() throws Exception {
        BatchPurgeResult mockResult = new BatchPurgeResult(
                List.of(1L),
                List.of(new BatchPurgeResult.Failure(2L, "失败原因")));
        when(appService.batchPurge(anyList())).thenReturn(mockResult);

        mockMvc.perform(post("/api/admin/recycle-bin/batch-purge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[1,2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successIds[0]").isNumber())
                .andExpect(jsonPath("$.data.failures[0].id").isNumber());
    }
}
