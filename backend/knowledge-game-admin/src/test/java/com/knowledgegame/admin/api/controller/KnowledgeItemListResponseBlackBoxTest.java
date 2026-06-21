package com.knowledgegame.admin.api.controller;

import com.knowledgegame.admin.api.dto.response.KnowledgeItemListResponse;
import com.knowledgegame.admin.api.dto.response.KnowledgeItemResponse;
import com.knowledgegame.admin.application.service.KnowledgeItemAppService;
import com.knowledgegame.components.exception.handler.GlobalExceptionHandler;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Black-box @WebMvcTest for KnowledgeItemController list endpoint response shape.
 * <p>
 * REQ-114: List API no longer returns content/contentHtml fields.
 * Detail, create, and update APIs remain unchanged and must still return content/contentHtml.
 */
@WebMvcTest(KnowledgeItemController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class KnowledgeItemListResponseBlackBoxTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KnowledgeItemAppService appService;

    private final long now = 1767225600000L;

    /**
     * List response MUST NOT contain content or contentHtml fields.
     * Asserts title and status are still present.
     */
    @Test
    @DisplayName("GET /api/admin/knowledge-items → 200, list response excludes content and contentHtml")
    void listResponse_shouldNotContainContentFields() throws Exception {
        KnowledgeItemListResponse item = KnowledgeItemListResponse.builder()
                .id(1L).title("知识条目标题").status("ACTIVE").sortOrder(0)
                .categoryIds(List.of(10L, 20L))
                .tags(List.of("tag1", "tag2"))
                .coverImageFileId(100L)
                .coverImageUrl("https://example.com/cover.png")
                .createdAt(now).updatedAt(now).build();
        PageResult<KnowledgeItemListResponse> page = PageResult.<KnowledgeItemListResponse>builder()
                .content(List.of(item)).totalElements(1).pageNumber(0).pageSize(20).totalPages(1).build();
        when(appService.list(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(page);

        mockMvc.perform(get("/api/admin/knowledge-items?page=0&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                // content/contentHtml MUST NOT exist
                .andExpect(jsonPath("$.data.content[0].content").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].contentHtml").doesNotExist())
                // other fields MUST exist
                .andExpect(jsonPath("$.data.content[0].title").value("知识条目标题"))
                .andExpect(jsonPath("$.data.content[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.content[0].id").value(1));
    }

    /**
     * List response must return categoryIds array correctly.
     */
    @Test
    @DisplayName("GET /api/admin/knowledge-items → 200, list response includes categoryIds array")
    void listResponse_shouldIncludeCategoryIds() throws Exception {
        KnowledgeItemListResponse item = KnowledgeItemListResponse.builder()
                .id(2L).title("分类条目").status("ACTIVE").sortOrder(1)
                .categoryIds(List.of(10L, 20L))
                .createdAt(now).updatedAt(now).build();
        PageResult<KnowledgeItemListResponse> page = PageResult.<KnowledgeItemListResponse>builder()
                .content(List.of(item)).totalElements(1).pageNumber(0).pageSize(20).totalPages(1).build();
        when(appService.list(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(page);

        mockMvc.perform(get("/api/admin/knowledge-items?page=0&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content[0].categoryIds[0]").value(10))
                .andExpect(jsonPath("$.data.content[0].categoryIds[1]").value(20));
    }

    /**
     * Detail response MUST contain content and contentHtml (backward compatibility).
     */
    @Test
    @DisplayName("GET /api/admin/knowledge-items/{id} → 200, detail response includes content and contentHtml")
    void getById_shouldContainContentFields() throws Exception {
        KnowledgeItemResponse response = KnowledgeItemResponse.builder()
                .id(1L).title("详情标题").content("Markdown 内容").contentHtml("<p>HTML 内容</p>")
                .status("ACTIVE").sortOrder(0).createdAt(now).updatedAt(now).build();
        when(appService.getById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/admin/knowledge-items/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content").value("Markdown 内容"))
                .andExpect(jsonPath("$.data.contentHtml").value("<p>HTML 内容</p>"))
                .andExpect(jsonPath("$.data.title").value("详情标题"));
    }

    /**
     * Create response MUST contain content.
     */
    @Test
    @DisplayName("POST /api/admin/knowledge-items → 200, create response includes content")
    void create_shouldContainContent() throws Exception {
        KnowledgeItemResponse response = KnowledgeItemResponse.builder()
                .id(3L).title("新知识条目").content("新建内容").contentHtml("<p>新建HTML</p>")
                .status("ACTIVE").sortOrder(0).categoryIds(List.of(1L))
                .createdAt(now).updatedAt(now).build();
        when(appService.create(any())).thenReturn(response);

        mockMvc.perform(post("/api/admin/knowledge-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"新知识条目\",\"content\":\"新建内容\",\"categoryIds\":[1]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content").exists())
                .andExpect(jsonPath("$.data.contentHtml").exists())
                .andExpect(jsonPath("$.data.title").value("新知识条目"));
    }

    /**
     * Update response MUST contain content.
     */
    @Test
    @DisplayName("PUT /api/admin/knowledge-items/{id} → 200, update response includes content")
    void update_shouldContainContent() throws Exception {
        KnowledgeItemResponse response = KnowledgeItemResponse.builder()
                .id(1L).title("更新后标题").content("更新后内容").contentHtml("<p>更新后HTML</p>")
                .status("ACTIVE").sortOrder(0).createdAt(now).updatedAt(now).build();
        when(appService.update(eq(1L), any())).thenReturn(response);

        mockMvc.perform(put("/api/admin/knowledge-items/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"更新后标题\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content").exists())
                .andExpect(jsonPath("$.data.contentHtml").exists())
                .andExpect(jsonPath("$.data.title").value("更新后标题"));
    }
}
