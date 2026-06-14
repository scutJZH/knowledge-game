package com.knowledgegame.admin.api.controller;

import com.knowledgegame.admin.api.dto.response.QuestionResponse;
import com.knowledgegame.admin.application.service.QuestionAppService;
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
 * QuestionController 单元测试
 */
@WebMvcTest(QuestionController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class QuestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QuestionAppService appService;

    private final Long now = 1767225600000L;

    /**
     * 创建题目 - 正常返回 200
     */
    @Test
    void create_shouldReturn200() throws Exception {
        QuestionResponse.OptionItem optA = QuestionResponse.OptionItem.builder().key("A").content("面向对象").build();
        QuestionResponse.OptionItem optB = QuestionResponse.OptionItem.builder().key("B").content("面向过程").build();
        QuestionResponse response = QuestionResponse.builder()
                .id(1L).type("SINGLE_CHOICE").content("Java 是什么语言？")
                .options(List.of(optA, optB)).answer("A").difficulty(1)
                .status("ACTIVE").createdAt(now).updatedAt(now).build();
        when(appService.create(any())).thenReturn(response);

        mockMvc.perform(post("/api/admin/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SINGLE_CHOICE\",\"content\":\"Java 是什么语言？\","
                                + "\"options\":[{\"key\":\"A\",\"content\":\"面向对象\"},"
                                + "{\"key\":\"B\",\"content\":\"面向过程\"}],"
                                + "\"answer\":\"A\",\"difficulty\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content").value("Java 是什么语言？"));
    }

    /**
     * 创建题目 - 缺少必填字段返回 400
     */
    @Test
    void create_shouldReturn400_whenRequiredFieldsMissing() throws Exception {
        mockMvc.perform(post("/api/admin/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    /**
     * 创建题目 - 缺少 content 返回 400
     */
    @Test
    void create_shouldReturn400_whenContentMissing() throws Exception {
        mockMvc.perform(post("/api/admin/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SINGLE_CHOICE\",\"answer\":\"A\",\"difficulty\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    /**
     * 查询详情 - 正常返回
     */
    @Test
    void getById_shouldReturn200() throws Exception {
        QuestionResponse response = QuestionResponse.builder()
                .id(1L).type("SINGLE_CHOICE").content("测试题目")
                .answer("A").difficulty(1).status("ACTIVE")
                .createdAt(now).updatedAt(now).build();
        when(appService.getById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/admin/questions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("测试题目"));
    }

    /**
     * 分页查询 - 正常返回
     */
    @Test
    void list_shouldReturn200() throws Exception {
        PageResult<QuestionResponse> page = PageResult.<QuestionResponse>builder()
                .content(List.of()).totalElements(0).pageNumber(0).pageSize(20).totalPages(0).build();
        when(appService.list(any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(page);

        mockMvc.perform(get("/api/admin/questions")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    /**
     * 分页查询 - 带筛选和排序参数
     */
    @Test
    void list_shouldPassSortParams() throws Exception {
        PageResult<QuestionResponse> page = PageResult.<QuestionResponse>builder()
                .content(List.of()).totalElements(0).pageNumber(0).pageSize(10).totalPages(0).build();
        when(appService.list(eq("关键字"), eq("SINGLE_CHOICE"), eq(1), eq(10L),
                eq("Java"), eq("ACTIVE"), eq("createdAt"), eq("asc"), eq(0), eq(10)))
                .thenReturn(page);

        mockMvc.perform(get("/api/admin/questions")
                        .param("keyword", "关键字")
                        .param("type", "SINGLE_CHOICE")
                        .param("difficulty", "1")
                        .param("categoryId", "10")
                        .param("tag", "Java")
                        .param("status", "ACTIVE")
                        .param("sort", "createdAt")
                        .param("order", "asc")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        verify(appService).list("关键字", "SINGLE_CHOICE", 1, 10L,
                "Java", "ACTIVE", "createdAt", "asc", 0, 10);
    }

    /**
     * 更新题目 - 正常返回
     */
    @Test
    void update_shouldReturn200() throws Exception {
        QuestionResponse response = QuestionResponse.builder()
                .id(1L).type("SINGLE_CHOICE").content("新内容")
                .answer("A").difficulty(2).status("ACTIVE")
                .createdAt(now).updatedAt(now).build();
        when(appService.update(eq(1L), any())).thenReturn(response);

        mockMvc.perform(put("/api/admin/questions/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"新内容\",\"difficulty\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("新内容"));
    }

    /**
     * 删除题目 - 正常返回
     */
    @Test
    void delete_shouldReturn200() throws Exception {
        mockMvc.perform(delete("/api/admin/questions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(appService).delete(1L);
    }

    /**
     * 查询题目分类 - 正常返回
     */
    @Test
    void getCategories_shouldReturn200() throws Exception {
        when(appService.getCategoryIds(1L)).thenReturn(List.of(10L, 20L));

        mockMvc.perform(get("/api/admin/questions/1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value(10))
                .andExpect(jsonPath("$.data[1]").value(20));
    }

    /**
     * 更新题目分类 - 正常返回
     */
    @Test
    void updateCategories_shouldReturn200() throws Exception {
        mockMvc.perform(put("/api/admin/questions/1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryIds\":[1,2,3]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(appService).updateCategories(1L, List.of(1L, 2L, 3L));
    }

    /**
     * 批量启用 - 正常返回
     */
    @Test
    void batchActivate_shouldReturn200() throws Exception {
        mockMvc.perform(put("/api/admin/questions/batch-activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[1,2,3]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(appService).batchActivate(List.of(1L, 2L, 3L));
    }

    /**
     * 批量禁用 - 正常返回
     */
    @Test
    void batchDeactivate_shouldReturn200() throws Exception {
        mockMvc.perform(put("/api/admin/questions/batch-deactivate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[4,5,6]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(appService).batchDeactivate(List.of(4L, 5L, 6L));
    }
}
