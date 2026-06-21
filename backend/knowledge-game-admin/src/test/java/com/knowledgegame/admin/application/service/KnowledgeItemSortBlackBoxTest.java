package com.knowledgegame.admin.application.service;

import com.knowledgegame.admin.api.controller.KnowledgeItemController;
import com.knowledgegame.admin.api.dto.response.KnowledgeItemListResponse;
import com.knowledgegame.components.exception.handler.GlobalExceptionHandler;
import com.knowledgegame.core.domain.model.vo.PageResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * KnowledgeItemController 排序参数解析黑盒测试（REQ-97）
 * <p>
 * 仅基于 PRD 协议信息编写：验证 Controller 对 sort/order 参数的解析、校验与透传。
 * 不依赖 AppService 实现细节，所有断言仅关注 Controller 传给 AppService 的参数值。
 * <p>
 * PRD 协议摘要：
 * <ul>
 *   <li>可排序字段白名单：id, title, categoryName, status, createdAt, updatedAt</li>
 *   <li>sort 不在白名单 → 400 + "不支持的排序字段"</li>
 *   <li>order 大小写不敏感，非 asc 一律视为 desc</li>
 *   <li>sort 不传/空字符串 → 默认排序（sortOrder ASC + createdAt DESC）</li>
 * </ul>
 */
@WebMvcTest(KnowledgeItemController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class KnowledgeItemSortBlackBoxTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KnowledgeItemAppService appService;

    /**
     * 让 AppService.list() 返回空分页，方便所有测试共用。
     */
    private void stubListReturnsEmptyPage() {
        PageResult<KnowledgeItemListResponse> page = PageResult.<KnowledgeItemListResponse>builder()
                .content(List.of())
                .totalElements(0L)
                .pageNumber(0)
                .pageSize(20)
                .totalPages(0)
                .build();
        when(appService.list(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(page);
    }

    // ========================================================================
    // 用例 1：sort 不传 — AppService 收到 sort=null
    // ========================================================================

    /**
     * 不传 sort/order → AppService 收到的 sort 应为 null（使用默认排序）
     */
    @Test
    void should_pass_null_sort_when_sort_not_provided() throws Exception {
        stubListReturnsEmptyPage();

        mockMvc.perform(get("/api/admin/knowledge-items"))
                .andExpect(status().isOk());

        ArgumentCaptor<String> sortCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> orderCaptor = ArgumentCaptor.forClass(String.class);
        verify(appService).list(any(), any(), any(), any(),
                sortCaptor.capture(), orderCaptor.capture(), eq(0), eq(20));

        assertNull(sortCaptor.getValue(), "sort 不传时 Controller 应传 null 给 AppService");
    }

    // ========================================================================
    // 用例 2：6 个合法字段 × ASC/DESC — AppService 收到正确的 sort/order
    // ========================================================================

    /**
     * 白名单内所有字段 × ASC/DESC 均能正确透传到 AppService
     */
    @ParameterizedTest
    @CsvSource({
            "id,      asc,  id,      asc",
            "id,      desc, id,      desc",
            "title,   asc,  title,   asc",
            "title,   desc, title,   desc",
            "categoryName, asc,  categoryName, asc",
            "categoryName, desc, categoryName, desc",
            "status,  asc,  status,  asc",
            "status,  desc, status,  desc",
            "createdAt, asc, createdAt, asc",
            "createdAt, desc, createdAt, desc",
            "updatedAt, asc, updatedAt, asc",
            "updatedAt, desc, updatedAt, desc",
    })
    void should_pass_correct_sort_field_and_order(
            String requestSort, String requestOrder,
            String expectedSort, String expectedOrder) throws Exception {
        stubListReturnsEmptyPage();

        mockMvc.perform(get("/api/admin/knowledge-items")
                        .param("sort", requestSort)
                        .param("order", requestOrder))
                .andExpect(status().isOk());

        ArgumentCaptor<String> sortCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> orderCaptor = ArgumentCaptor.forClass(String.class);
        verify(appService).list(any(), any(), any(), any(),
                sortCaptor.capture(), orderCaptor.capture(), eq(0), eq(20));

        assertEquals(expectedSort, sortCaptor.getValue(),
                "sort=" + requestSort + " → AppService 应收到 sort=" + expectedSort);
        assertEquals(expectedOrder, orderCaptor.getValue(),
                "order=" + requestOrder + " → AppService 应收到 order=" + expectedOrder);
    }

    // ========================================================================
    // 用例 3：categoryName 排序（PRD 特别提及 — JOIN category 表 + NULLS LAST）
    // ========================================================================

    /**
     * categoryName ASC → AppService 收到 sort=categoryName, order=asc
     */
    @Test
    void should_pass_category_name_sort_when_asc() throws Exception {
        stubListReturnsEmptyPage();

        mockMvc.perform(get("/api/admin/knowledge-items")
                        .param("sort", "categoryName")
                        .param("order", "asc"))
                .andExpect(status().isOk());

        ArgumentCaptor<String> sortCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> orderCaptor = ArgumentCaptor.forClass(String.class);
        verify(appService).list(any(), any(), any(), any(),
                sortCaptor.capture(), orderCaptor.capture(), eq(0), eq(20));

        assertEquals("categoryName", sortCaptor.getValue());
        assertEquals("asc", orderCaptor.getValue());
    }

    /**
     * categoryName DESC → AppService 收到 sort=categoryName, order=desc
     */
    @Test
    void should_pass_category_name_sort_when_desc() throws Exception {
        stubListReturnsEmptyPage();

        mockMvc.perform(get("/api/admin/knowledge-items")
                        .param("sort", "categoryName")
                        .param("order", "desc"))
                .andExpect(status().isOk());

        ArgumentCaptor<String> sortCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> orderCaptor = ArgumentCaptor.forClass(String.class);
        verify(appService).list(any(), any(), any(), any(),
                sortCaptor.capture(), orderCaptor.capture(), eq(0), eq(20));

        assertEquals("categoryName", sortCaptor.getValue());
        assertEquals("desc", orderCaptor.getValue());
    }

    // ========================================================================
    // 用例 4：order 参数大小写不敏感（asc / ASC / Asc 均视为升序）
    // ========================================================================

    /**
     * order=ASC（全大写）应等同于 asc
     */
    @Test
    void should_treat_order_as_asc_when_uppercase() throws Exception {
        stubListReturnsEmptyPage();

        mockMvc.perform(get("/api/admin/knowledge-items")
                        .param("sort", "id")
                        .param("order", "ASC"))
                .andExpect(status().isOk());

        ArgumentCaptor<String> sortCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> orderCaptor = ArgumentCaptor.forClass(String.class);
        verify(appService).list(any(), any(), any(), any(),
                sortCaptor.capture(), orderCaptor.capture(), eq(0), eq(20));

        assertEquals("id", sortCaptor.getValue());
        // 大小写不敏感：ASC 应视为升序
        assertTrue("asc".equalsIgnoreCase(orderCaptor.getValue()),
                "order=ASC 应视为升序，实际值: " + orderCaptor.getValue());
    }

    /**
     * order=Asc（混合大小写）应等同于 asc
     */
    @Test
    void should_treat_order_as_asc_when_mixed_case() throws Exception {
        stubListReturnsEmptyPage();

        mockMvc.perform(get("/api/admin/knowledge-items")
                        .param("sort", "title")
                        .param("order", "Asc"))
                .andExpect(status().isOk());

        ArgumentCaptor<String> sortCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> orderCaptor = ArgumentCaptor.forClass(String.class);
        verify(appService).list(any(), any(), any(), any(),
                sortCaptor.capture(), orderCaptor.capture(), eq(0), eq(20));

        assertEquals("title", sortCaptor.getValue());
        assertTrue("asc".equalsIgnoreCase(orderCaptor.getValue()),
                "order=Asc 应视为升序，实际值: " + orderCaptor.getValue());
    }

    // ========================================================================
    // 用例 5：order 非法值 → Controller 透传给 AppService（降级在 AppService 层处理）
    // ========================================================================

    /**
     * order=invalidValue → Controller 透传原始值（降级在 AppService 层处理）
     */
    @Test
    void should_pass_raw_order_when_order_is_invalid() throws Exception {
        stubListReturnsEmptyPage();

        mockMvc.perform(get("/api/admin/knowledge-items")
                        .param("sort", "id")
                        .param("order", "invalidValue"))
                .andExpect(status().isOk());

        ArgumentCaptor<String> sortCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> orderCaptor = ArgumentCaptor.forClass(String.class);
        verify(appService).list(any(), any(), any(), any(),
                sortCaptor.capture(), orderCaptor.capture(), eq(0), eq(20));

        assertEquals("id", sortCaptor.getValue());
        assertEquals("invalidValue", orderCaptor.getValue(),
                "Controller 应透传原始 order 值");
    }

    /**
     * order=randomString → Controller 透传原始值
     */
    @Test
    void should_pass_raw_order_when_order_is_random_string() throws Exception {
        stubListReturnsEmptyPage();

        mockMvc.perform(get("/api/admin/knowledge-items")
                        .param("sort", "createdAt")
                        .param("order", "randomString"))
                .andExpect(status().isOk());

        ArgumentCaptor<String> sortCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> orderCaptor = ArgumentCaptor.forClass(String.class);
        verify(appService).list(any(), any(), any(), any(),
                sortCaptor.capture(), orderCaptor.capture(), eq(0), eq(20));

        assertEquals("createdAt", sortCaptor.getValue());
        assertEquals("randomString", orderCaptor.getValue(),
                "Controller 应透传原始 order 值");
    }

    /**
     * 只传 sort 不传 order → Controller 传 null 给 AppService
     */
    @Test
    void should_pass_null_order_when_order_not_provided() throws Exception {
        stubListReturnsEmptyPage();

        mockMvc.perform(get("/api/admin/knowledge-items")
                        .param("sort", "updatedAt"))
                .andExpect(status().isOk());

        ArgumentCaptor<String> sortCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> orderCaptor = ArgumentCaptor.forClass(String.class);
        verify(appService).list(any(), any(), any(), any(),
                sortCaptor.capture(), orderCaptor.capture(), eq(0), eq(20));

        assertEquals("updatedAt", sortCaptor.getValue());
        assertNull(orderCaptor.getValue(), "order 不传时 Controller 应传 null");
    }

    // ========================================================================
    // 用例 6：sort 为空字符串 → Controller 透传（空字符串在 AppService 层视为默认排序）
    // ========================================================================

    /**
     * sort 传空字符串 → Controller 透传空字符串（SortField.parse 层处理默认排序）
     */
    @Test
    void should_pass_empty_string_when_sort_is_empty() throws Exception {
        stubListReturnsEmptyPage();

        mockMvc.perform(get("/api/admin/knowledge-items")
                        .param("sort", ""))
                .andExpect(status().isOk());

        ArgumentCaptor<String> sortCaptor = ArgumentCaptor.forClass(String.class);
        verify(appService).list(any(), any(), any(), any(),
                sortCaptor.capture(), any(), anyInt(), anyInt());

        assertEquals("", sortCaptor.getValue(),
                "Controller 应透传空字符串，由 AppService 层处理默认排序");
    }

    // ========================================================================
    // 用例 7：sort=sortOrder → 400（模拟 AppService 校验层抛出 BusinessException）
    // ========================================================================

    /**
     * sort=sortOrder 不在白名单 → 返回 400（由 AppService → GlobalExceptionHandler 链产生）
     */
    @Test
    void should_return_400_with_unsupported_sort_message_when_sort_is_sort_order() throws Exception {
        when(appService.list(any(), any(), any(), any(), eq("sortOrder"), any(), anyInt(), anyInt()))
                .thenThrow(new com.knowledgegame.core.common.exception.BusinessException(400,
                        "不支持的排序字段: sortOrder，允许的字段: [ID, 标题, 分类名称, 状态, 创建时间, 更新时间]"));

        mockMvc.perform(get("/api/admin/knowledge-items")
                        .param("sort", "sortOrder")
                        .param("order", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("不支持的排序字段")));
    }

    // ========================================================================
    // 用例 8：sort=invalidField → 400
    // ========================================================================

    /**
     * sort=invalidField 不在白名单 → 返回 400
     */
    @Test
    void should_return_400_with_unsupported_sort_message_when_sort_is_invalid_field() throws Exception {
        when(appService.list(any(), any(), any(), any(), eq("invalidField"), any(), anyInt(), anyInt()))
                .thenThrow(new com.knowledgegame.core.common.exception.BusinessException(400,
                        "不支持的排序字段: invalidField，允许的字段: [ID, 标题, 分类名称, 状态, 创建时间, 更新时间]"));

        mockMvc.perform(get("/api/admin/knowledge-items")
                        .param("sort", "invalidField")
                        .param("order", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("不支持的排序字段")));
    }

    // ========================================================================
    // 用例 9：sort + order 都传但 page/size 用默认值 → 分页正确
    // ========================================================================

    /**
     * 传 sort/order 但省略 page/size → AppService 收到默认分页 (page=0, size=20)
     */
    @Test
    void should_pass_default_pagination_when_page_and_size_not_provided() throws Exception {
        stubListReturnsEmptyPage();

        mockMvc.perform(get("/api/admin/knowledge-items")
                        .param("sort", "title")
                        .param("order", "asc"))
                .andExpect(status().isOk());

        ArgumentCaptor<String> sortCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> orderCaptor = ArgumentCaptor.forClass(String.class);
        // page/size 使用 eq 断言（ArgumentCaptor 对 int 基元类型会因 capture() 返回 null 而 NPE）
        verify(appService).list(any(), any(), any(), any(),
                sortCaptor.capture(), orderCaptor.capture(), eq(0), eq(20));

        assertEquals("title", sortCaptor.getValue());
        assertEquals("asc", orderCaptor.getValue());
    }

    /**
     * 显式传分页参数 → AppService 收到自定义值
     */
    @Test
    void should_pass_custom_pagination_when_page_and_size_are_specified() throws Exception {
        stubListReturnsEmptyPage();

        mockMvc.perform(get("/api/admin/knowledge-items")
                        .param("sort", "status")
                        .param("order", "desc")
                        .param("page", "2")
                        .param("size", "10"))
                .andExpect(status().isOk());

        ArgumentCaptor<String> sortCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> orderCaptor = ArgumentCaptor.forClass(String.class);
        verify(appService).list(any(), any(), any(), any(),
                sortCaptor.capture(), orderCaptor.capture(), eq(2), eq(10));

        assertEquals("status", sortCaptor.getValue());
        assertEquals("desc", orderCaptor.getValue());
    }
}
