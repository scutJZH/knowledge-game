package com.knowledgegame.admin.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegame.admin.api.dto.request.CreateCardTemplateRequest;
import com.knowledgegame.admin.api.dto.request.UpdateCardTemplateRequest;
import com.knowledgegame.admin.api.dto.response.CardTemplateListResponse;
import com.knowledgegame.admin.api.dto.response.CardTemplateResponse;
import com.knowledgegame.admin.application.service.CardTemplateAppService;
import com.knowledgegame.admin.config.WebMvcConfig;
import com.knowledgegame.components.exception.handler.GlobalExceptionHandler;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.domainenum.CardRarity;
import com.knowledgegame.core.domain.model.domainenum.CardTemplateStatus;
import com.knowledgegame.core.domain.model.vo.PageResult;
import com.knowledgegame.core.infrastructure.adapter.repoadapter.CardTemplateRepositoryAdapter;
import com.knowledgegame.core.infrastructure.adapter.repoadapter.IpSeriesRepositoryAdapter;
import com.knowledgegame.core.infrastructure.adapter.repoadapter.UserRepositoryAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CardTemplateController 单元测试（MockMvc + @WebMvcTest）
 * <p>
 * 覆盖管理端卡牌模板的 CRUD 接口，包括创建、查询、更新、删除，
 * 以及参数校验和业务异常场景。
 * REQ-92 简化后：使用 imageUrl 替代 starImages，移除星级图片端点。
 */
@WebMvcTest(
        controllers = CardTemplateController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                        WebMvcConfig.class,
                        IpSeriesRepositoryAdapter.class,
                        CardTemplateRepositoryAdapter.class,
                        UserRepositoryAdapter.class
                }
        )
)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class CardTemplateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CardTemplateAppService cardTemplateAppService;

    /**
     * 构建测试用的 CardTemplateResponse DTO（含 imageUrl，无 starImages）
     */
    private CardTemplateResponse buildTestResponse(Long id) {
        return CardTemplateResponse.builder()
                .id(id)
                .ipSeriesId(1L)
                .ipSeriesName("火影忍者")
                .code("PIKACHU")
                .name("皮卡丘")
                .rarity("SR")
                .description("电气鼠")
                .status("ACTIVE")
                .imageUrl("https://example.com/card.png")
                .createdAt(1767225600000L)
                .updatedAt(1767225600000L)
                .build();
    }

    /**
     * 构建有效的创建��求体（含 imageUrl）
     */
    private CreateCardTemplateRequest buildCreateRequest() {
        CreateCardTemplateRequest request = new CreateCardTemplateRequest();
        request.setIpSeriesId(1L);
        request.setCode("PIKACHU");
        request.setName("皮卡丘");
        request.setRarity(CardRarity.SR);
        request.setDescription("电气鼠");
        request.setStatus(CardTemplateStatus.ACTIVE);
        request.setImageUrl("https://example.com/card.png");
        return request;
    }

    // ========== 创建接口测试 ==========

    /**
     * 创建卡牌模板 - 成功返回 200 + DTO（含 imageUrl）
     */
    @Test
    @DisplayName("创建卡牌模板 - 成功（含 imageUrl）")
    void create_shouldReturn200_whenValidRequest() throws Exception {
        // 准备数据
        CreateCardTemplateRequest request = buildCreateRequest();
        when(cardTemplateAppService.createCardTemplate(
                eq(1L), eq("PIKACHU"), eq("皮卡丘"),
                eq(CardRarity.SR), eq("电气鼠"),
                eq(CardTemplateStatus.ACTIVE), eq("https://example.com/card.png")
        )).thenReturn(buildTestResponse(1L));

        // 执行请求并断言
        mockMvc.perform(post("/api/admin/card-templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.code").value("PIKACHU"))
                .andExpect(jsonPath("$.data.name").value("皮卡丘"))
                .andExpect(jsonPath("$.data.rarity").value("SR"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.ipSeriesName").value("火影忍者"))
                .andExpect(jsonPath("$.data.imageUrl").value("https://example.com/card.png"));

        verify(cardTemplateAppService).createCardTemplate(
                eq(1L), eq("PIKACHU"), eq("皮卡丘"),
                eq(CardRarity.SR), eq("电气鼠"),
                eq(CardTemplateStatus.ACTIVE), eq("https://example.com/card.png"));
    }

    /**
     * 创建卡牌模板 - 参数校验失败（code 为空）
     */
    @Test
    @DisplayName("创建卡牌模板 - 参数校验失败（code 为空）")
    void create_shouldReturn400_whenCodeIsBlank() throws Exception {
        // 构建一个 code 为空的请求
        CreateCardTemplateRequest request = buildCreateRequest();
        request.setCode("");

        // 执行请求并断言返回 code=400
        mockMvc.perform(post("/api/admin/card-templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    // ========== 查询详情接口测试 ==========

    /**
     * 查询卡牌模板详情 - 成功（响应含 imageUrl，无 starImages）
     */
    @Test
    @DisplayName("查询卡牌模板详情 - 成功（响应含 imageUrl）")
    void getById_shouldReturn200_whenExists() throws Exception {
        // 模拟 AppService 返回 CardTemplateResponse DTO
        when(cardTemplateAppService.getCardTemplateById(1L)).thenReturn(buildTestResponse(1L));

        // 执行请求并断言
        mockMvc.perform(get("/api/admin/card-templates/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.code").value("PIKACHU"))
                .andExpect(jsonPath("$.data.name").value("皮卡丘"))
                .andExpect(jsonPath("$.data.rarity").value("SR"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.ipSeriesName").value("火影忍者"))
                .andExpect(jsonPath("$.data.imageUrl").value("https://example.com/card.png"))
                .andExpect(jsonPath("$.data.starImages").doesNotExist());

        verify(cardTemplateAppService).getCardTemplateById(1L);
    }

    /**
     * 查询卡牌模板详情 - 不存在返回 400
     */
    @Test
    @DisplayName("查询卡牌模板详情 - 不存在返回 400")
    void getById_shouldReturn400_whenNotFound() throws Exception {
        // 模拟 AppService 抛出业务异常
        when(cardTemplateAppService.getCardTemplateById(999L))
                .thenThrow(new BusinessException("卡牌模板不存在: 999"));

        // 执行请求并断言返回 code=400
        mockMvc.perform(get("/api/admin/card-templates/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("卡牌模板不存在: 999"));

        verify(cardTemplateAppService).getCardTemplateById(999L);
    }

    // ========== 分页查询接口测试 ==========

    /**
     * 分页查询卡牌模板 - 成功返回分页结果
     */
    @Test
    @DisplayName("分页查询卡牌模板 - 成功")
    void list_shouldReturn200_withPagedResult() throws Exception {
        // 构建模拟分页数据
        CardTemplateListResponse listResponse = CardTemplateListResponse.builder()
                .id(1L)
                .ipSeriesId(1L)
                .ipSeriesName("火影忍者")
                .code("PIKACHU")
                .name("皮卡丘")
                .rarity("SR")
                .description("电气鼠")
                .status("ACTIVE")
                .createdAt(1767225600000L)
                .updatedAt(1767225600000L)
                .build();
        PageResult<CardTemplateListResponse> mockPageResult = PageResult.<CardTemplateListResponse>builder()
                .content(List.of(listResponse))
                .totalElements(1)
                .pageNumber(0)
                .pageSize(20)
                .totalPages(1)
                .build();

        when(cardTemplateAppService.listCardTemplates(
                isNull(), isNull(), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(mockPageResult);

        // 执行请求并断言
        mockMvc.perform(get("/api/admin/card-templates")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(1))
                .andExpect(jsonPath("$.data.content[0].code").value("PIKACHU"))
                .andExpect(jsonPath("$.data.content[0].ipSeriesName").value("火影忍者"));

        verify(cardTemplateAppService).listCardTemplates(null, null, null, null, 0, 20);
    }

    // ========== 更新接口测试 ==========

    /**
     * 更新卡牌模板 - 成功（含 imageUrl）
     */
    @Test
    @DisplayName("更新卡牌模板 - 成功（含 imageUrl）")
    void update_shouldReturn200_whenValidRequest() throws Exception {
        // 构建更新请求
        UpdateCardTemplateRequest request = new UpdateCardTemplateRequest();
        request.setName("皮卡丘-进化");
        request.setDescription("进化后的电气鼠");
        request.setImageUrl("https://example.com/card-updated.png");

        // 模拟 AppService 返回更新后的 DTO
        CardTemplateResponse updatedResponse = CardTemplateResponse.builder()
                .id(1L)
                .ipSeriesId(1L)
                .ipSeriesName("火影忍者")
                .code("PIKACHU")
                .name("皮卡丘-进化")
                .rarity("SR")
                .description("进化后的电气鼠")
                .status("ACTIVE")
                .imageUrl("https://example.com/card-updated.png")
                .createdAt(1767225600000L)
                .updatedAt(1780315200000L)
                .build();
        when(cardTemplateAppService.updateCardTemplate(
                eq(1L), isNull(), eq("皮卡丘-进化"), isNull(),
                eq("进化后的电气鼠"), isNull(), eq("https://example.com/card-updated.png")
        )).thenReturn(updatedResponse);

        // 执行请求并断言
        mockMvc.perform(put("/api/admin/card-templates/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("皮卡丘-进化"))
                .andExpect(jsonPath("$.data.description").value("进化后的电气鼠"))
                .andExpect(jsonPath("$.data.imageUrl").value("https://example.com/card-updated.png"));

        verify(cardTemplateAppService).updateCardTemplate(
                1L, null, "皮卡丘-进化", null, "进化后的电气鼠", null, "https://example.com/card-updated.png");
    }

    // ========== 删除接口测试 ==========

    /**
     * 删除卡牌模板 - 成功
     */
    @Test
    @DisplayName("删除卡牌模板 - 成功")
    void delete_shouldReturn200_whenSuccessful() throws Exception {
        // 模拟 AppService 删除操作（void 方法）
        doNothing().when(cardTemplateAppService).deleteCardTemplate(1L);

        // 执行请求并断言
        mockMvc.perform(delete("/api/admin/card-templates/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(cardTemplateAppService).deleteCardTemplate(1L);
    }

    /**
     * 删除卡牌模板 - 不存在返回 400
     */
    @Test
    @DisplayName("删除卡牌模板 - 不存在返回 400")
    void delete_shouldReturn400_whenNotFound() throws Exception {
        // 模拟 AppService 抛出业务异常
        doThrow(new BusinessException("卡牌模板不存在: 999"))
                .when(cardTemplateAppService).deleteCardTemplate(999L);

        // 执行请求并断言返回 code=400
        mockMvc.perform(delete("/api/admin/card-templates/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("卡牌模板不存在: 999"));

        verify(cardTemplateAppService).deleteCardTemplate(999L);
    }

    // ========== 已删除端点测试 ==========

    /**
     * POST /star-images — 端点已删除，控制器不再处理此路径，
     * 请求将匹配不到任何 Handler 并触发 NoResourceFoundException，
     * 由 GlobalExceptionHandler 捕获后返回非 200 的错误响应。
     */
    @Test
    @DisplayName("POST /star-images 端点已删除，应返回错误响应")
    void addStarImage_shouldReturnError_whenEndpointDeleted() throws Exception {
        // 构建请求体（格式不重要，因为端点不存在）
        String body = "{\"starLevel\": 2, \"imageUrl\": \"https://example.com/star2.png\"}";

        // 执行请求并断言返回错误（非 200 的业务状态码）
        mockMvc.perform(post("/api/admin/card-templates/1/star-images")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }
}
