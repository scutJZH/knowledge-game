package com.knowledgegame.admin.api.controller;

import com.knowledgegame.admin.api.dto.response.ImportFailDetail;
import com.knowledgegame.admin.api.dto.response.KnowledgeItemImportResult;
import com.knowledgegame.admin.api.dto.response.KnowledgeItemResponse;
import com.knowledgegame.admin.application.service.KnowledgeItemAppService;
import com.knowledgegame.components.exception.handler.GlobalExceptionHandler;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.vo.PageResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * KnowledgeItemController 单元测试
 */
@WebMvcTest(KnowledgeItemController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class KnowledgeItemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KnowledgeItemAppService appService;

    private final long now = 1767225600000L;

    /**
     * 创建知识条目 - 正常
     */
    @Test
    void create_shouldReturn200() throws Exception {
        KnowledgeItemResponse response = KnowledgeItemResponse.builder()
                .id(1L).title("标题").content("内容").status("ACTIVE")
                .sortOrder(0).createdAt(now).updatedAt(now).build();
        when(appService.create(any())).thenReturn(response);

        mockMvc.perform(post("/api/admin/knowledge-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"标题\",\"content\":\"内容\",\"categoryIds\":[1]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.title").value("标题"));
    }

    /**
     * 创建 - 缺少必填字段返回 400
     */
    @Test
    void create_shouldReturn400_whenRequiredFieldsMissing() throws Exception {
        mockMvc.perform(post("/api/admin/knowledge-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    /**
     * 查询详情
     */
    @Test
    void getById_shouldReturn200() throws Exception {
        KnowledgeItemResponse response = KnowledgeItemResponse.builder()
                .id(1L).title("标题").content("内容").status("ACTIVE")
                .sortOrder(0).createdAt(now).updatedAt(now).build();
        when(appService.getById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/admin/knowledge-items/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    /**
     * 分页查询
     */
    @Test
    void list_shouldReturn200() throws Exception {
        KnowledgeItemResponse item = KnowledgeItemResponse.builder()
                .id(1L).title("标题").status("ACTIVE").sortOrder(0)
                .createdAt(now).updatedAt(now).build();
        PageResult<KnowledgeItemResponse> page = PageResult.<KnowledgeItemResponse>builder()
                .content(List.of(item)).totalElements(1).pageNumber(0).pageSize(20).totalPages(1).build();
        when(appService.list(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(page);

        mockMvc.perform(get("/api/admin/knowledge-items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content[0].title").value("标题"));
    }

    /**
     * 更新
     */
    @Test
    void update_shouldReturn200() throws Exception {
        KnowledgeItemResponse response = KnowledgeItemResponse.builder()
                .id(1L).title("新标题").status("ACTIVE").sortOrder(0)
                .createdAt(now).updatedAt(now).build();
        when(appService.update(eq(1L), any())).thenReturn(response);

        mockMvc.perform(put("/api/admin/knowledge-items/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"新标题\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("新标题"));
    }

    /**
     * 删除
     */
    @Test
    void delete_shouldReturn200() throws Exception {
        mockMvc.perform(delete("/api/admin/knowledge-items/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(appService).delete(1L);
    }

    /**
     * 查询分类
     */
    @Test
    void getCategories_shouldReturn200() throws Exception {
        when(appService.getCategoryIds(1L)).thenReturn(List.of(10L, 20L));

        mockMvc.perform(get("/api/admin/knowledge-items/1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0]").value(10));
    }

    /**
     * 更新分类
     */
    @Test
    void updateCategories_shouldReturn200() throws Exception {
        mockMvc.perform(put("/api/admin/knowledge-items/1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryIds\":[10,20]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    /**
     * 批量启用
     */
    @Test
    void batchActivate_shouldReturn200() throws Exception {
        mockMvc.perform(put("/api/admin/knowledge-items/batch-activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[1,2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    /**
     * 批量禁用
     */
    @Test
    void batchDeactivate_shouldReturn200() throws Exception {
        mockMvc.perform(put("/api/admin/knowledge-items/batch-deactivate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[1,2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    /**
     * 批量排序
     */
    @Test
    void batchSort_shouldReturn200() throws Exception {
        mockMvc.perform(put("/api/admin/knowledge-items/batch-sort")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"id\":1,\"sortOrder\":3},{\"id\":2,\"sortOrder\":5}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    /**
     * 下载导入模板 - 200（Content-Type 由 AppService 设置，mock 环境下不验证）
     */
    @Test
    void downloadImportTemplate_shouldReturn200() throws Exception {
        mockMvc.perform(get("/api/admin/knowledge-items/import-template"))
                .andExpect(status().isOk());
    }

    /**
     * Excel 导入 - 正常返回 200
     */
    @Test
    void importExcel_shouldReturn200() throws Exception {
        KnowledgeItemImportResult importResult = KnowledgeItemImportResult.builder()
                .totalCount(1).successCount(1).failCount(0).failDetails(List.of()).build();
        when(appService.importExcel(any())).thenReturn(importResult);

        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "fake excel content".getBytes());

        mockMvc.perform(multipart("/api/admin/knowledge-items/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.successCount").value(1))
                .andExpect(jsonPath("$.data.failCount").value(0));
    }

    /**
     * Excel 导入 - 非 xlsx 文件抛 BusinessException → 400
     */
    @Test
    void importExcel_nonXlsx_shouldReturn400() throws Exception {
        when(appService.importExcel(any()))
                .thenThrow(new BusinessException("仅支持 .xlsx 格式文件"));

        MockMultipartFile file = new MockMultipartFile("file", "test.txt",
                "text/plain", "not excel".getBytes());

        mockMvc.perform(multipart("/api/admin/knowledge-items/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("仅支持 .xlsx 格式文件"));
    }

    /**
     * Excel 导入 - 超 200 行抛 BusinessException → 400
     */
    @Test
    void importExcel_exceeds200_shouldReturn400() throws Exception {
        when(appService.importExcel(any()))
                .thenThrow(new BusinessException("单次导入上限 200 行，当前 201 行"));

        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "fake excel content".getBytes());

        mockMvc.perform(multipart("/api/admin/knowledge-items/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("单次导入上限 200 行，当前 201 行"));
    }

    /**
     * Excel 导入 - 空模板返回 totalCount=0
     */
    @Test
    void importExcel_emptyTemplate_shouldReturn200() throws Exception {
        KnowledgeItemImportResult importResult = KnowledgeItemImportResult.builder()
                .totalCount(0).successCount(0).failCount(0).failDetails(List.of()).build();
        when(appService.importExcel(any())).thenReturn(importResult);

        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "fake excel content".getBytes());

        mockMvc.perform(multipart("/api/admin/knowledge-items/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalCount").value(0));
    }

    /**
     * Markdown zip 导入 - 正常返回 200
     */
    @Test
    void importMarkdownZip_shouldReturn200() throws Exception {
        KnowledgeItemImportResult importResult = KnowledgeItemImportResult.builder()
                .totalCount(2).successCount(2).failCount(0).failDetails(List.of()).build();
        when(appService.importMarkdownZip(any())).thenReturn(importResult);

        MockMultipartFile file = new MockMultipartFile("file", "test.zip",
                "application/zip", "fake zip content".getBytes());

        mockMvc.perform(multipart("/api/admin/knowledge-items/import-markdown").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.successCount").value(2));
    }

    /**
     * Markdown zip 导入 - 非 zip 文件抛 BusinessException → 400
     */
    @Test
    void importMarkdownZip_nonZip_shouldReturn400() throws Exception {
        when(appService.importMarkdownZip(any()))
                .thenThrow(new BusinessException("仅支持 .zip 格式文件"));

        MockMultipartFile file = new MockMultipartFile("file", "test.rar",
                "application/x-rar-compressed", "not zip".getBytes());

        mockMvc.perform(multipart("/api/admin/knowledge-items/import-markdown").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("仅支持 .zip 格式文件"));
    }

    /**
     * Markdown zip 导入 - 超 200 行抛 BusinessException → 400
     */
    @Test
    void importMarkdownZip_exceeds200_shouldReturn400() throws Exception {
        when(appService.importMarkdownZip(any()))
                .thenThrow(new BusinessException("单次导入上限 200 行，当前 201 行"));

        MockMultipartFile file = new MockMultipartFile("file", "test.zip",
                "application/zip", "fake zip content".getBytes());

        mockMvc.perform(multipart("/api/admin/knowledge-items/import-markdown").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("单次导入上限 200 行，当前 201 行"));
    }
}
