package com.knowledgegame.app.api.controller;

import com.knowledgegame.app.api.dto.response.KnowledgeCategoryTreeResponse;
import com.knowledgegame.app.application.service.KnowledgeCategoryAppService;
import com.knowledgegame.app.config.JacksonConfig;
import com.knowledgegame.components.exception.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * KnowledgeCategoryController 单元测试（禁用 Spring Security Filter，专注测试 Controller 逻辑）
 */
@WebMvcTest(controllers = KnowledgeCategoryController.class)
@Import({GlobalExceptionHandler.class, JacksonConfig.class})
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("KnowledgeCategoryController")
class KnowledgeCategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KnowledgeCategoryAppService appService;

    private final Long now = 1767225600000L;

    /**
     * GET /api/knowledge-categories/tree — 正常返回 200，JSON 结构与 PRD 一致
     */
    @Test
    @DisplayName("GET /api/knowledge-categories/tree 正常返回分类树")
    void tree_shouldReturn200() throws Exception {
        KnowledgeCategoryTreeResponse child = KnowledgeCategoryTreeResponse.builder()
                .id(2L).parentId(1L).name("Java").description("Java 相关").sortOrder(0)
                .iconFileId(10L).iconUrl("https://example.com/icons/java.png")
                .color("#00AA00")
                .coverImageFileId(20L).coverImageUrl("https://example.com/covers/java.jpg")
                .status("ACTIVE").createdAt(now).updatedAt(now)
                .children(List.of()).build();
        KnowledgeCategoryTreeResponse root = KnowledgeCategoryTreeResponse.builder()
                .id(1L).parentId(null).name("编程").description("编程相关知识分类").sortOrder(0)
                .iconFileId(10L).iconUrl("https://example.com/icons/code.png")
                .color("#FF5500")
                .coverImageFileId(20L).coverImageUrl("https://example.com/covers/code.jpg")
                .status("ACTIVE").createdAt(now).updatedAt(now)
                .children(List.of(child)).build();
        given(appService.tree()).willReturn(List.of(root));

        mockMvc.perform(get("/api/knowledge-categories/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].parentId").isEmpty())
                .andExpect(jsonPath("$.data[0].name").value("编程"))
                .andExpect(jsonPath("$.data[0].description").value("编程相关知识分类"))
                .andExpect(jsonPath("$.data[0].iconFileId").value(10))
                .andExpect(jsonPath("$.data[0].iconUrl").value("https://example.com/icons/code.png"))
                .andExpect(jsonPath("$.data[0].color").value("#FF5500"))
                .andExpect(jsonPath("$.data[0].coverImageFileId").value(20))
                .andExpect(jsonPath("$.data[0].coverImageUrl").value("https://example.com/covers/code.jpg"))
                .andExpect(jsonPath("$.data[0].sortOrder").value(0))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data[0].createdAt").value(now))
                .andExpect(jsonPath("$.data[0].updatedAt").value(now))
                .andExpect(jsonPath("$.data[0].children[0].id").value(2))
                .andExpect(jsonPath("$.data[0].children[0].parentId").value(1))
                .andExpect(jsonPath("$.data[0].children[0].name").value("Java"));
    }

    /**
     * GET /api/knowledge-categories/tree — 空树返回 200 + 空数组
     */
    @Test
    @DisplayName("GET /api/knowledge-categories/tree 空树返回空数组")
    void tree_shouldReturnEmptyArray_whenNoCategories() throws Exception {
        given(appService.tree()).willReturn(List.of());

        mockMvc.perform(get("/api/knowledge-categories/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
