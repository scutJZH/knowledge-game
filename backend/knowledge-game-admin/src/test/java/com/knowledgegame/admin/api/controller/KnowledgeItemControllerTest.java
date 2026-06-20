package com.knowledgegame.admin.api.controller;

import com.knowledgegame.admin.api.dto.response.KnowledgeItemResponse;
import com.knowledgegame.admin.application.service.KnowledgeItemAppService;
import com.knowledgegame.components.exception.handler.GlobalExceptionHandler;
import com.knowledgegame.core.domain.model.vo.PageResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
}
