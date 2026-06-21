package com.knowledgegame.admin.api.controller;

import com.knowledgegame.admin.api.dto.response.KnowledgeItemImportResult;
import com.knowledgegame.admin.application.service.KnowledgeItemAppService;
import com.knowledgegame.components.exception.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 知识条目批量导入 Controller 层黑盒测试。
 *
 * <p>只基于 PRD 约定的 HTTP 契约和 Controller 公开签名编写，不依赖任何业务实现细节。
 * AppService 通过 {@code @MockitoBean} 完全 mock，所有 400/异常场景由显式 mock 配置驱动。</p>
 */
@WebMvcTest(KnowledgeItemController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class KnowledgeItemImportBlackBoxTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KnowledgeItemAppService appService;

    // ── helpers ──────────────────────────────────────────────

    private KnowledgeItemImportResult emptyResult() {
        return KnowledgeItemImportResult.builder()
                .totalCount(0).successCount(0).failCount(0)
                .failDetails(List.of())
                .build();
    }

    private KnowledgeItemImportResult singleSuccessResult() {
        return KnowledgeItemImportResult.builder()
                .totalCount(1).successCount(1).failCount(0)
                .failDetails(List.of())
                .build();
    }

    // ── scenario a: POST /import ── MultipartFile parameter missing ─────────

    /**
     * 场景 a：发送 multipart 请求但文件参数名为 {@code notFile}（而非 {@code file}）。
     * Spring 框架层在校验 {@code @RequestParam(required=true)} 时抛出
     * {@code MissingServletRequestPartException}，GlobalExceptionHandler 兜底处理为 500。
     * 不进入 Controller，无需 mock AppService。
     */
    @Test
    void importExcel_missingFileParam_shouldReturn500() throws Exception {
        MockMultipartFile wrongNameFile = new MockMultipartFile(
                "notFile", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "content".getBytes());

        mockMvc.perform(multipart("/api/admin/knowledge-items/import")
                        .file(wrongNameFile))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }

    // ── scenario b: POST /import ── empty result ───────────────────────────

    /**
     * 场景 b：AppService 返回全零导入结果，Controller 应正确透传 200 + data。
     */
    @Test
    void importExcel_emptyFile_shouldReturn200WithZeroCounts() throws Exception {
        when(appService.importExcel(any())).thenReturn(emptyResult());
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[0]);

        mockMvc.perform(multipart("/api/admin/knowledge-items/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalCount").value(0))
                .andExpect(jsonPath("$.data.successCount").value(0))
                .andExpect(jsonPath("$.data.failCount").value(0));
    }

    // ── scenario c: POST /import ── Content-Type is NOT multipart/form-data ─

    /**
     * 场景 c：使用 {@code post()}（非 multipart）发送请求。
     * {@code @PostMapping(consumes = MULTIPART_FORM_DATA_VALUE)} 触发
     * {@code HttpMediaTypeNotSupportedException} → GlobalExceptionHandler 兜底 → 500。
     */
    @Test
    void importExcel_notMultipart_shouldReturn500() throws Exception {
        mockMvc.perform(post("/api/admin/knowledge-items/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }

    // ── scenario d: GET /import-template ── 200 + AppService invocation ──

    /**
     * 场景 d：Content-Type/Content-Disposition 在 AppService 中设置（mock 下不生效），
     * 黑盒层面验证端点返回 200 且 Controller 正确调用了 AppService。
     */
    @Test
    void downloadImportTemplate_shouldReturn200AndInvokeAppService() throws Exception {
        mockMvc.perform(get("/api/admin/knowledge-items/import-template"))
                .andExpect(status().isOk());

        verify(appService).downloadImportTemplate(any(jakarta.servlet.http.HttpServletResponse.class));
    }

    // ── scenario e: POST /import-markdown ── single success ─────────────────

    /**
     * 场景 e：Markdown zip 导入单条成功，验证 200 + data 正确透传。
     */
    @Test
    void importMarkdownZip_singleSuccess_shouldReturn200() throws Exception {
        when(appService.importMarkdownZip(any())).thenReturn(singleSuccessResult());
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.zip",
                "application/zip",
                "fake-zip-content".getBytes());

        mockMvc.perform(multipart("/api/admin/knowledge-items/import-markdown").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.successCount").value(1))
                .andExpect(jsonPath("$.data.failCount").value(0));
    }

    // ── scenario f: POST /import ── RuntimeException → 500 ─────────────────

    /**
     * 场景 f：AppService 抛出非 BusinessException 的 RuntimeException，
     * GlobalExceptionHandler 兜底处理，返回 500。
     */
    @Test
    void importExcel_runtimeException_shouldReturn500() throws Exception {
        when(appService.importExcel(any()))
                .thenThrow(new RuntimeException("internal error"));
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "fake content".getBytes());

        mockMvc.perform(multipart("/api/admin/knowledge-items/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }
}
