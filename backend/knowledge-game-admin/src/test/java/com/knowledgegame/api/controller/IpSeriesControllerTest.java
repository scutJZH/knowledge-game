package com.knowledgegame.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegame.api.dto.request.CreateIpSeriesRequest;
import com.knowledgegame.api.dto.request.UpdateIpSeriesRequest;
import com.knowledgegame.api.dto.response.IpSeriesResponse;
import com.knowledgegame.application.service.IpSeriesAppService;
import com.knowledgegame.common.exception.BusinessException;
import com.knowledgegame.domain.model.domainenum.IpSeriesStatus;
import com.knowledgegame.domain.model.vo.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
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
 * IpSeriesController 单元测试（MockMvc + @WebMvcTest）
 * <p>
 * 覆盖管理端 IP 系列的 CRUD 接口，包括创建、查询、更新、删除，
 * 以及参数校验和业务异常场景。
 */
@WebMvcTest(IpSeriesController.class)
class IpSeriesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private IpSeriesAppService ipSeriesAppService;

    /**
     * 构建测试用的 IpSeriesResponse DTO
     */
    private IpSeriesResponse buildTestResponse(Long id) {
        return IpSeriesResponse.builder()
                .id(id)
                .code("NARUTO")
                .name("火影忍者")
                .description("忍者世界 IP 系列")
                .coverImageUrl("https://example.com/naruto.jpg")
                .status("ACTIVE")
                .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0, 0))
                .updatedAt(LocalDateTime.of(2026, 1, 1, 0, 0, 0))
                .build();
    }

    /**
     * 构建有效的创建请求体
     */
    private CreateIpSeriesRequest buildCreateRequest() {
        CreateIpSeriesRequest request = new CreateIpSeriesRequest();
        request.setCode("NARUTO");
        request.setName("火影忍者");
        request.setDescription("忍者世界 IP 系列");
        request.setCoverImageUrl("https://example.com/naruto.jpg");
        request.setStatus(IpSeriesStatus.ACTIVE);
        return request;
    }

    // ========== 创建接口测试 ==========

    @Test
    @DisplayName("创建 IP 系列 - 成功")
    void create_shouldReturn200_whenValidRequest() throws Exception {
        // 准备数据
        CreateIpSeriesRequest request = buildCreateRequest();
        // 模拟 AppService 返回 IpSeriesResponse DTO
        when(ipSeriesAppService.createIpSeries(
                eq("NARUTO"), eq("火影忍者"), eq("忍者世界 IP 系列"),
                eq("https://example.com/naruto.jpg"), eq(IpSeriesStatus.ACTIVE)
        )).thenReturn(buildTestResponse(1L));

        // 执行请求并断言
        mockMvc.perform(post("/api/admin/ip-series")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.code").value("NARUTO"))
                .andExpect(jsonPath("$.data.name").value("火影忍者"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        verify(ipSeriesAppService).createIpSeries(
                "NARUTO", "火影忍者", "忍者世界 IP 系列",
                "https://example.com/naruto.jpg", IpSeriesStatus.ACTIVE
        );
    }

    @Test
    @DisplayName("创建 IP 系列 - 参数校验失败（code 为空）")
    void create_shouldReturn400_whenCodeIsBlank() throws Exception {
        // 构建一个 code 为空的请求
        CreateIpSeriesRequest request = buildCreateRequest();
        request.setCode("");

        // 执行请求并断言返回 400
        mockMvc.perform(post("/api/admin/ip-series")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("创建 IP 系列 - 参数校验失败（code 为 null）")
    void create_shouldReturn400_whenCodeIsNull() throws Exception {
        // 构建一个 code 为 null 的请求
        CreateIpSeriesRequest request = buildCreateRequest();
        request.setCode(null);

        // 执行请求并断言返回 400
        mockMvc.perform(post("/api/admin/ip-series")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("创建 IP 系列 - 参数校验失败（name 为空）")
    void create_shouldReturn400_whenNameIsBlank() throws Exception {
        // 构建一个 name 为空的请求
        CreateIpSeriesRequest request = buildCreateRequest();
        request.setName("");

        // 执行请求并断言返回 400
        mockMvc.perform(post("/api/admin/ip-series")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("创建 IP 系列 - 参数校验失败（status 为 null）")
    void create_shouldReturn400_whenStatusIsNull() throws Exception {
        // 构建一个 status 为 null 的请求
        CreateIpSeriesRequest request = buildCreateRequest();
        request.setStatus(null);

        // 执行请求并断言返回 400
        mockMvc.perform(post("/api/admin/ip-series")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ========== 查询详情接口测试 ==========

    @Test
    @DisplayName("查询 IP 系列详情 - 成功")
    void getById_shouldReturn200_whenExists() throws Exception {
        // 模拟 AppService 返回 IpSeriesResponse DTO
        when(ipSeriesAppService.getIpSeriesById(1L)).thenReturn(buildTestResponse(1L));

        // 执行请求并断言
        mockMvc.perform(get("/api/admin/ip-series/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.code").value("NARUTO"))
                .andExpect(jsonPath("$.data.name").value("火影忍者"))
                .andExpect(jsonPath("$.data.description").value("忍者世界 IP 系列"))
                .andExpect(jsonPath("$.data.coverImageUrl").value("https://example.com/naruto.jpg"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        verify(ipSeriesAppService).getIpSeriesById(1L);
    }

    @Test
    @DisplayName("查询 IP 系列详情 - 不存在时返回 400")
    void getById_shouldReturn400_whenNotFound() throws Exception {
        // 模拟 AppService 抛出业务异常
        when(ipSeriesAppService.getIpSeriesById(999L))
                .thenThrow(new BusinessException("IP 系列不存在: 999"));

        // 执行请求并断言返回 code=400
        mockMvc.perform(get("/api/admin/ip-series/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("IP 系列不存在: 999"));

        verify(ipSeriesAppService).getIpSeriesById(999L);
    }

    // ========== 分页查询接口测试 ==========

    @Test
    @DisplayName("分页查询 IP 系列列表 - 成功")
    void list_shouldReturn200_withPagedResult() throws Exception {
        // 构建模拟分页数据（AppService 返回 PageResult<IpSeriesResponse>）
        List<IpSeriesResponse> responses = List.of(
                buildTestResponse(1L),
                buildTestResponse(2L)
        );
        PageResult<IpSeriesResponse> mockPageResult = PageResult.<IpSeriesResponse>builder()
                .content(responses)
                .totalElements(2)
                .pageNumber(0)
                .pageSize(20)
                .totalPages(1)
                .build();

        // 模拟 AppService 返回分页结果（status 为 String 参数）
        when(ipSeriesAppService.listIpSeries(eq(null), eq(null), eq(0), eq(20)))
                .thenReturn(mockPageResult);

        // 执行请求并断言
        mockMvc.perform(get("/api/admin/ip-series")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].id").value(1))
                .andExpect(jsonPath("$.data.content[1].id").value(2));

        verify(ipSeriesAppService).listIpSeries(null, null, 0, 20);
    }

    @Test
    @DisplayName("分页查询 IP 系列列表 - 按名称过滤")
    void list_shouldPassNameFilter() throws Exception {
        // 构建模拟分页数据
        List<IpSeriesResponse> responses = List.of(buildTestResponse(1L));
        PageResult<IpSeriesResponse> mockPageResult = PageResult.<IpSeriesResponse>builder()
                .content(responses)
                .totalElements(1)
                .pageNumber(0)
                .pageSize(10)
                .totalPages(1)
                .build();

        // 模拟 AppService 按名称过滤（status 为 String 参数）
        when(ipSeriesAppService.listIpSeries(eq("火影"), eq(null), eq(0), eq(10)))
                .thenReturn(mockPageResult);

        // 执行请求并断言
        mockMvc.perform(get("/api/admin/ip-series")
                        .param("name", "火影")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(1));

        verify(ipSeriesAppService).listIpSeries("火影", null, 0, 10);
    }

    // ========== 更新接口测试 ==========

    @Test
    @DisplayName("更新 IP 系列 - 成功")
    void update_shouldReturn200_whenValidRequest() throws Exception {
        // 构建更新请求
        UpdateIpSeriesRequest request = new UpdateIpSeriesRequest();
        request.setName("火影忍者-更新");
        request.setDescription("更新后的描述");

        // 模拟 AppService 返回更新后的 IpSeriesResponse DTO
        IpSeriesResponse updatedResponse = IpSeriesResponse.builder()
                .id(1L)
                .code("NARUTO")
                .name("火影忍者-更新")
                .description("更新后的描述")
                .coverImageUrl("https://example.com/naruto.jpg")
                .status("ACTIVE")
                .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0, 0))
                .updatedAt(LocalDateTime.of(2026, 6, 1, 12, 0, 0))
                .build();
        when(ipSeriesAppService.updateIpSeries(
                eq(1L), eq(null), eq("火影忍者-更新"), eq("更新后的描述"),
                eq(null), eq(null)
        )).thenReturn(updatedResponse);

        // 执行请求并断言
        mockMvc.perform(put("/api/admin/ip-series/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("火影忍者-更新"))
                .andExpect(jsonPath("$.data.description").value("更新后的描述"));

        verify(ipSeriesAppService).updateIpSeries(
                1L, null, "火影忍者-更新", "更新后的描述", null, null
        );
    }

    // ========== 删除接口测试 ==========

    @Test
    @DisplayName("删除 IP 系列 - 成功")
    void delete_shouldReturn200_whenSuccessful() throws Exception {
        // 模拟 AppService 删除操作（void 方法）
        doNothing().when(ipSeriesAppService).deleteIpSeries(1L);

        // 执行请求并断言
        mockMvc.perform(delete("/api/admin/ip-series/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(ipSeriesAppService).deleteIpSeries(1L);
    }

    @Test
    @DisplayName("删除 IP 系列 - 不存在时返回 400")
    void delete_shouldReturn400_whenNotFound() throws Exception {
        // 模拟 AppService 抛出业务异常
        doThrow(new BusinessException("IP 系列不存在: 999"))
                .when(ipSeriesAppService).deleteIpSeries(999L);

        // 执行请求并断言返回 code=400
        mockMvc.perform(delete("/api/admin/ip-series/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("IP 系列不存在: 999"));

        verify(ipSeriesAppService).deleteIpSeries(999L);
    }
}
