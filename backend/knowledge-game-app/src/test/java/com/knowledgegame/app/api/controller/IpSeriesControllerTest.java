package com.knowledgegame.app.api.controller;

import com.knowledgegame.app.api.dto.response.ActiveIpSeriesResponse;
import com.knowledgegame.app.application.service.IpSeriesAppService;
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

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = IpSeriesController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, JacksonConfig.class})
class IpSeriesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IpSeriesAppService appService;

    @Test
    @DisplayName("GET /api/ip-series → 委托 AppService.listActive()")
    void listActive_shouldDelegateToAppService() throws Exception {
        when(appService.listActive()).thenReturn(List.of());

        mockMvc.perform(get("/api/ip-series"))
                .andExpect(status().isOk());

        verify(appService).listActive();
    }

    @Test
    @DisplayName("GET /api/ip-series → 响应体 code=0 + data 非空")
    void listActive_shouldReturnCode0WithData() throws Exception {
        ActiveIpSeriesResponse item = new ActiveIpSeriesResponse();
        item.setId(1L);
        item.setName("宝可梦");
        item.setCode("PKM");
        item.setCoverImageFileId(100L);
        item.setCoverImageUrl("https://example.com/cover.png");
        when(appService.listActive()).thenReturn(List.of(item));

        mockMvc.perform(get("/api/ip-series"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].name").value("宝可梦"))
                .andExpect(jsonPath("$.data[0].code").value("PKM"))
                .andExpect(jsonPath("$.data[0].coverImageFileId").value(100))
                .andExpect(jsonPath("$.data[0].coverImageUrl").value("https://example.com/cover.png"));
    }

    @Test
    @DisplayName("Jackson 序列化 → null coverImage 字段显式为 null 不省略")
    void listActive_shouldSerializeNullCoverImageFields() throws Exception {
        ActiveIpSeriesResponse item = new ActiveIpSeriesResponse();
        item.setId(2L);
        item.setName("数码宝贝");
        item.setCode("DM");
        item.setCoverImageFileId(null);
        item.setCoverImageUrl(null);
        when(appService.listActive()).thenReturn(List.of(item));

        mockMvc.perform(get("/api/ip-series"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(2))
                .andExpect(jsonPath("$.data[0].coverImageFileId").value(nullValue()))
                .andExpect(jsonPath("$.data[0].coverImageUrl").value(nullValue()));
    }
}
