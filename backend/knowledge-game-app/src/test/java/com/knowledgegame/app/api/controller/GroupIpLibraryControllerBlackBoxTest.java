package com.knowledgegame.app.api.controller;

import com.knowledgegame.app.api.dto.GroupIpLibraryResponse;
import com.knowledgegame.app.api.dto.UpdateGroupIpLibraryRequest;
import com.knowledgegame.app.application.service.StudyGroupAppService;
import com.knowledgegame.app.config.JacksonConfig;
import com.knowledgegame.components.exception.handler.GlobalExceptionHandler;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.common.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 黑盒测试：仅凭 PRD 行为描述编写，不参考 AppService/Controller 实现代码。
 * 侧重开发者测试未覆盖的异常路径和 JSON 结构完整性。
 */
@WebMvcTest(controllers = StudyGroupController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, JacksonConfig.class})
class GroupIpLibraryControllerBlackBoxTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StudyGroupAppService appService;

    // ---- PUT 异常路径（开发者测试覆盖了 GET 异常路径，本测试补 PUT 侧） ----

    @Test
    @DisplayName("PUT 非成员应返回 NOT_GROUP_MEMBER")
    void putIpLibrary_nonMember_returnsNotGroupMember() throws Exception {
        when(appService.updateIpLibrary(eq(1L), any()))
                .thenThrow(new BusinessException(ResultCode.NOT_GROUP_MEMBER));

        UpdateGroupIpLibraryRequest request = new UpdateGroupIpLibraryRequest();
        request.setIpSeriesIds(List.of(10L));

        mockMvc.perform(put("/api/study-groups/1/ip-library")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ipSeriesIds\":[10]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("非群组成员"));
    }

    @Test
    @DisplayName("PUT 群组不存在应返回 GROUP_NOT_FOUND")
    void putIpLibrary_groupNotFound_returnsGroupNotFound() throws Exception {
        when(appService.updateIpLibrary(eq(1L), any()))
                .thenThrow(new BusinessException(ResultCode.GROUP_NOT_FOUND));

        UpdateGroupIpLibraryRequest request = new UpdateGroupIpLibraryRequest();
        request.setIpSeriesIds(List.of(10L));

        mockMvc.perform(put("/api/study-groups/1/ip-library")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ipSeriesIds\":[10]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("群组不存在"));
    }

    @Test
    @DisplayName("PUT 空数组应正常返回空列表")
    void putIpLibrary_emptyArray_returnsEmptyList() throws Exception {
        when(appService.updateIpLibrary(eq(1L), any())).thenReturn(List.of());

        mockMvc.perform(put("/api/study-groups/1/ip-library")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ipSeriesIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    // ---- GET JSON 结构完整性（coverageImage 为 null 的边界） ----

    @Test
    @DisplayName("GET 应返回完整 JSON 结构含 coverImage 为 null")
    void getIpLibrary_shouldReturnFullJsonWithNullCoverImage() throws Exception {
        GroupIpLibraryResponse item = new GroupIpLibraryResponse();
        item.setId(1L);
        item.setGroupId(1L);
        item.setIpSeriesId(10L);
        item.setIpSeriesName("宝可梦");
        item.setIpSeriesCode("PKM");
        item.setCoverImageFileId(null);
        item.setCoverImageUrl(null);
        item.setAddedAt(1718800000000L);
        when(appService.listIpLibrary(1L)).thenReturn(List.of(item));

        mockMvc.perform(get("/api/study-groups/1/ip-library"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].groupId").value(1))
                .andExpect(jsonPath("$.data[0].ipSeriesId").value(10))
                .andExpect(jsonPath("$.data[0].ipSeriesName").value("宝可梦"))
                .andExpect(jsonPath("$.data[0].ipSeriesCode").value("PKM"))
                .andExpect(jsonPath("$.data[0].coverImageFileId").isEmpty())
                .andExpect(jsonPath("$.data[0].coverImageUrl").isEmpty())
                .andExpect(jsonPath("$.data[0].addedAt").value(1718800000000L));
    }

    @Test
    @DisplayName("GET 列表含多条目应返回正确数量")
    void getIpLibrary_multipleItems_returnsAll() throws Exception {
        GroupIpLibraryResponse item1 = new GroupIpLibraryResponse();
        item1.setId(1L);
        item1.setGroupId(1L);
        item1.setIpSeriesId(10L);
        item1.setIpSeriesName("A");
        item1.setAddedAt(1718800000000L);
        GroupIpLibraryResponse item2 = new GroupIpLibraryResponse();
        item2.setId(2L);
        item2.setGroupId(1L);
        item2.setIpSeriesId(20L);
        item2.setIpSeriesName("B");
        item2.setAddedAt(1718800000000L);
        when(appService.listIpLibrary(1L)).thenReturn(List.of(item1, item2));

        mockMvc.perform(get("/api/study-groups/1/ip-library"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].ipSeriesName").value("A"))
                .andExpect(jsonPath("$.data[1].ipSeriesName").value("B"));
    }
}
