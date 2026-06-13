package com.knowledgegame.admin.api.controller;

import com.knowledgegame.admin.api.dto.response.KnowledgeCategoryResponse;
import com.knowledgegame.admin.api.dto.response.KnowledgeCategoryTreeResponse;
import com.knowledgegame.admin.application.service.KnowledgeCategoryAppService;
import com.knowledgegame.components.exception.handler.GlobalExceptionHandler;
import com.knowledgegame.core.domain.model.vo.PageResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
 * KnowledgeCategoryController 单元测试
 */
@WebMvcTest(KnowledgeCategoryController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class KnowledgeCategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KnowledgeCategoryAppService appService;

    private LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);

    /**
     * 创建分类 - 正常返回 200
     */
    @Test
    void create_shouldReturn200() throws Exception {
        KnowledgeCategoryResponse response = KnowledgeCategoryResponse.builder()
                .id(1L).name("编程").status("ACTIVE").createdAt(now).updatedAt(now).build();
        when(appService.create(any(), any(), any(), any(), any(), any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/admin/knowledge-categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"编程\",\"sortOrder\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("编程"));
    }

    /**
     * 创建分类 - 缺少必填字段返回 400
     */
    @Test
    void create_shouldReturn400_whenNameMissing() throws Exception {
        mockMvc.perform(post("/api/admin/knowledge-categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    /**
     * 查询详情 - 正常返回
     */
    @Test
    void getById_shouldReturn200() throws Exception {
        KnowledgeCategoryResponse response = KnowledgeCategoryResponse.builder()
                .id(1L).name("编程").status("ACTIVE").createdAt(now).updatedAt(now).build();
        when(appService.getById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/admin/knowledge-categories/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("编程"));
    }

    /**
     * 分页查询 - 正常返回
     */
    @Test
    void list_shouldReturn200() throws Exception {
        PageResult<KnowledgeCategoryResponse> page = PageResult.<KnowledgeCategoryResponse>builder()
                .content(List.of()).totalElements(0).pageNumber(0).pageSize(20).totalPages(0).build();
        when(appService.list(any(), any(), any(), anyInt(), anyInt())).thenReturn(page);

        mockMvc.perform(get("/api/admin/knowledge-categories")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    /**
     * 分类树 - 正常返回
     */
    @Test
    void tree_shouldReturn200() throws Exception {
        KnowledgeCategoryTreeResponse child = KnowledgeCategoryTreeResponse.builder()
                .id(2L).parentId(1L).name("Java").status("ACTIVE").sortOrder(0)
                .children(List.of()).build();
        KnowledgeCategoryTreeResponse root = KnowledgeCategoryTreeResponse.builder()
                .id(1L).parentId(null).name("编程").status("ACTIVE").sortOrder(0)
                .children(List.of(child)).build();
        when(appService.tree()).thenReturn(List.of(root));

        mockMvc.perform(get("/api/admin/knowledge-categories/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("编程"))
                .andExpect(jsonPath("$.data[0].children[0].name").value("Java"));
    }

    /**
     * 更新分类 - 正常返回
     */
    @Test
    void update_shouldReturn200() throws Exception {
        KnowledgeCategoryResponse response = KnowledgeCategoryResponse.builder()
                .id(1L).name("新名称").status("ACTIVE").createdAt(now).updatedAt(now).build();
        when(appService.update(eq(1L), any(), any(), any(), any(), any(), any())).thenReturn(response);

        mockMvc.perform(put("/api/admin/knowledge-categories/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新名称\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("新名称"));
    }

    /**
     * 移动分类 - 正常返回
     */
    @Test
    void move_shouldReturn200() throws Exception {
        KnowledgeCategoryResponse response = KnowledgeCategoryResponse.builder()
                .id(2L).parentId(5L).name("Java").status("ACTIVE").createdAt(now).updatedAt(now).build();
        when(appService.move(2L, 5L)).thenReturn(response);

        mockMvc.perform(put("/api/admin/knowledge-categories/2/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newParentId\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parentId").value(5));
    }

    /**
     * 删除分类 - 正常返回
     */
    @Test
    void delete_shouldReturn200() throws Exception {
        mockMvc.perform(delete("/api/admin/knowledge-categories/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(appService).delete(1L);
    }

    /**
     * 批量排序 - 正常返回 200
     */
    @Test
    void batchSort_shouldReturn200() throws Exception {
        mockMvc.perform(put("/api/admin/knowledge-categories/batch-sort")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"id\":1,\"sortOrder\":0},{\"id\":2,\"sortOrder\":1}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(appService).batchSort(any());
    }
}
