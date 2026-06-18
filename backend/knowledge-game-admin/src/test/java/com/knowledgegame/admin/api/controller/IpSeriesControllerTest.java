package com.knowledgegame.admin.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegame.admin.api.dto.request.CreateIpSeriesRequest;
import com.knowledgegame.admin.api.dto.request.UpdateIpSeriesRequest;
import com.knowledgegame.admin.api.dto.response.IpSeriesResponse;
import com.knowledgegame.admin.application.service.IpSeriesAppService;
import com.knowledgegame.admin.config.JacksonConfig;
import com.knowledgegame.admin.config.WebMvcConfig;
import com.knowledgegame.components.exception.handler.GlobalExceptionHandler;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.domainenum.IpSeriesStatus;
import com.knowledgegame.core.infrastructure.adapter.repoadapter.CardTemplateRepositoryAdapter;
import com.knowledgegame.core.infrastructure.adapter.repoadapter.IpSeriesRepositoryAdapter;
import com.knowledgegame.core.infrastructure.adapter.repoadapter.UserRepositoryAdapter;
import com.knowledgegame.core.domain.model.vo.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

@WebMvcTest(
        controllers = IpSeriesController.class,
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
@Import({GlobalExceptionHandler.class, JacksonConfig.class})
@AutoConfigureMockMvc(addFilters = false)
class IpSeriesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private IpSeriesAppService ipSeriesAppService;

    private IpSeriesResponse buildTestResponse(Long id) {
        return IpSeriesResponse.builder()
                .id(id)
                .code("NARUTO")
                .name("火影忍者")
                .description("忍者世界 IP 系列")
                .coverImageFileId(1L)
                .coverImageUrl("https://example.com/naruto.jpg")
                .status("ACTIVE")
                .createdAt(1767225600000L)
                .updatedAt(1767225600000L)
                .build();
    }

    private CreateIpSeriesRequest buildCreateRequest() {
        CreateIpSeriesRequest request = new CreateIpSeriesRequest();
        request.setCode("NARUTO");
        request.setName("火影忍者");
        request.setDescription("忍者世界 IP 系列");
        request.setCoverImageFileId(1L);
        request.setStatus(IpSeriesStatus.ACTIVE);
        return request;
    }

    // ========== 创建 ==========

    @Test
    @DisplayName("创建 IP 系列 - 成功")
    void create_shouldReturn200_whenValidRequest() throws Exception {
        CreateIpSeriesRequest request = buildCreateRequest();
        when(ipSeriesAppService.createIpSeries(
                eq("NARUTO"), eq("火影忍者"), eq("忍者世界 IP 系列"),
                eq(1L), eq(IpSeriesStatus.ACTIVE)
        )).thenReturn(buildTestResponse(1L));

        mockMvc.perform(post("/api/admin/ip-series")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.code").value("NARUTO"))
                .andExpect(jsonPath("$.data.name").value("火影忍者"))
                .andExpect(jsonPath("$.data.coverImageFileId").value(1))
                .andExpect(jsonPath("$.data.coverImageUrl").value("https://example.com/naruto.jpg"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("创建 IP 系列 - 参数校验失败（code 为空）")
    void create_shouldReturn400_whenCodeIsBlank() throws Exception {
        CreateIpSeriesRequest request = buildCreateRequest();
        request.setCode("");

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
        CreateIpSeriesRequest request = buildCreateRequest();
        request.setCode(null);

        mockMvc.perform(post("/api/admin/ip-series")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("创建 IP 系列 - 参数校验失败（name 为空）")
    void create_shouldReturn400_whenNameIsBlank() throws Exception {
        CreateIpSeriesRequest request = buildCreateRequest();
        request.setName("");

        mockMvc.perform(post("/api/admin/ip-series")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("创建 IP 系列 - 参数校验失败（status 为 null）")
    void create_shouldReturn400_whenStatusIsNull() throws Exception {
        CreateIpSeriesRequest request = buildCreateRequest();
        request.setStatus(null);

        mockMvc.perform(post("/api/admin/ip-series")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ========== 查询详情 ==========

    @Test
    @DisplayName("查询 IP 系列详情 - 成功")
    void getById_shouldReturn200_whenExists() throws Exception {
        when(ipSeriesAppService.getIpSeriesById(1L)).thenReturn(buildTestResponse(1L));

        mockMvc.perform(get("/api/admin/ip-series/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.code").value("NARUTO"))
                .andExpect(jsonPath("$.data.name").value("火影忍者"))
                .andExpect(jsonPath("$.data.coverImageFileId").value(1))
                .andExpect(jsonPath("$.data.coverImageUrl").value("https://example.com/naruto.jpg"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("查询 IP 系列详情 - 不存在时返回 400")
    void getById_shouldReturn400_whenNotFound() throws Exception {
        when(ipSeriesAppService.getIpSeriesById(999L))
                .thenThrow(new BusinessException("IP 系列不存在: 999"));

        mockMvc.perform(get("/api/admin/ip-series/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("IP 系列不存在: 999"));
    }

    // ========== 分页查询 ==========

    @Test
    @DisplayName("分页查询 IP 系列列表 - 成功")
    void list_shouldReturn200_withPagedResult() throws Exception {
        List<IpSeriesResponse> responses = List.of(buildTestResponse(1L), buildTestResponse(2L));
        PageResult<IpSeriesResponse> mockPageResult = PageResult.<IpSeriesResponse>builder()
                .content(responses).totalElements(2).pageNumber(0).pageSize(20).totalPages(1).build();

        when(ipSeriesAppService.listIpSeries(isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(mockPageResult);

        mockMvc.perform(get("/api/admin/ip-series")
                        .param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(2));
    }

    @Test
    @DisplayName("分页查询 IP 系列列表 - 按名称过滤")
    void list_shouldPassNameFilter() throws Exception {
        List<IpSeriesResponse> responses = List.of(buildTestResponse(1L));
        PageResult<IpSeriesResponse> mockPageResult = PageResult.<IpSeriesResponse>builder()
                .content(responses).totalElements(1).pageNumber(0).pageSize(10).totalPages(1).build();

        when(ipSeriesAppService.listIpSeries(eq("火影"), isNull(), eq(0), eq(10)))
                .thenReturn(mockPageResult);

        mockMvc.perform(get("/api/admin/ip-series")
                        .param("name", "火影").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content.length()").value(1));
    }

    // ========== 更新 ==========

    @Test
    @DisplayName("更新 IP 系列 - 成功")
    void update_shouldReturn200_whenValidRequest() throws Exception {
        UpdateIpSeriesRequest request = new UpdateIpSeriesRequest();
        request.setName("火影忍者-更新");
        request.setDescription(JsonNullable.of("更新后的描述"));

        IpSeriesResponse updatedResponse = IpSeriesResponse.builder()
                .id(1L).code("NARUTO").name("火影忍者-更新")
                .description("更新后的描述")
                .coverImageFileId(1L).coverImageUrl("https://example.com/naruto.jpg")
                .status("ACTIVE")
                .createdAt(1767225600000L).updatedAt(1780315200000L)
                .build();
        when(ipSeriesAppService.update(eq(1L), any(UpdateIpSeriesRequest.class))).thenReturn(updatedResponse);

        mockMvc.perform(put("/api/admin/ip-series/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("火影忍者-更新"))
                .andExpect(jsonPath("$.data.description").value("更新后的描述"));
    }

    /**
     * Jackson 三态反序列化：缺失字段 → JsonNullable.undefined()
     */
    @Test
    @DisplayName("更新 IP 系列 - 缺失字段反序列化为 undefined")
    void update_shouldDeserializeMissingFieldAsUndefined() throws Exception {
        IpSeriesResponse response = IpSeriesResponse.builder()
                .id(1L).code("NARUTO").name("火影").status("ACTIVE").build();
        when(ipSeriesAppService.update(eq(1L), any(UpdateIpSeriesRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/admin/ip-series/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"火影\"}"))
                .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<UpdateIpSeriesRequest> captor =
                org.mockito.ArgumentCaptor.forClass(UpdateIpSeriesRequest.class);
        verify(ipSeriesAppService).update(eq(1L), captor.capture());
        UpdateIpSeriesRequest req = captor.getValue();
        assertFalse(req.getDescription().isPresent(), "缺失字段应为 undefined");
        assertFalse(req.getCoverImageFileId().isPresent());
    }

    /**
     * Jackson 三态反序列化：null 字段 → JsonNullable.of(null)
     */
    @Test
    @DisplayName("更新 IP 系列 - null 字段反序列化为 of(null)（清空）")
    void update_shouldDeserializeNullFieldAsNullableOfNull() throws Exception {
        IpSeriesResponse response = IpSeriesResponse.builder()
                .id(1L).code("NARUTO").name("火影").status("ACTIVE").build();
        when(ipSeriesAppService.update(eq(1L), any(UpdateIpSeriesRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/admin/ip-series/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"火影\",\"description\":null,\"coverImageFileId\":null}"))
                .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<UpdateIpSeriesRequest> captor =
                org.mockito.ArgumentCaptor.forClass(UpdateIpSeriesRequest.class);
        verify(ipSeriesAppService).update(eq(1L), captor.capture());
        UpdateIpSeriesRequest req = captor.getValue();
        assertTrue(req.getDescription().isPresent(), "null 字段应为 present");
        assertNull(req.getDescription().get());
        assertTrue(req.getCoverImageFileId().isPresent());
        assertNull(req.getCoverImageFileId().get());
    }

    /**
     * Jackson 三态反序列化：数值字段 → JsonNullable.of(value)
     */
    @Test
    @DisplayName("更新 IP 系列 - 数值字段反序列化为 of(value)")
    void update_shouldDeserializeValueFieldAsNullableOfValue() throws Exception {
        IpSeriesResponse response = IpSeriesResponse.builder()
                .id(1L).code("NARUTO").name("火影").status("ACTIVE").build();
        when(ipSeriesAppService.update(eq(1L), any(UpdateIpSeriesRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/admin/ip-series/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"火影\",\"description\":\"新描述\",\"coverImageFileId\":200}"))
                .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<UpdateIpSeriesRequest> captor =
                org.mockito.ArgumentCaptor.forClass(UpdateIpSeriesRequest.class);
        verify(ipSeriesAppService).update(eq(1L), captor.capture());
        UpdateIpSeriesRequest req = captor.getValue();
        assertTrue(req.getDescription().isPresent());
        assertEquals("新描述", req.getDescription().get());
        assertTrue(req.getCoverImageFileId().isPresent());
        assertEquals(200L, req.getCoverImageFileId().get());
    }

    // ========== 删除 ==========

    @Test
    @DisplayName("删除 IP 系列 - 成功")
    void delete_shouldReturn200_whenSuccessful() throws Exception {
        doNothing().when(ipSeriesAppService).deleteIpSeries(1L);

        mockMvc.perform(delete("/api/admin/ip-series/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("删除 IP 系列 - 不存在时返回 400")
    void delete_shouldReturn400_whenNotFound() throws Exception {
        doThrow(new BusinessException("IP 系列不存在: 999"))
                .when(ipSeriesAppService).deleteIpSeries(999L);

        mockMvc.perform(delete("/api/admin/ip-series/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("IP 系列不存在: 999"));
    }
}
