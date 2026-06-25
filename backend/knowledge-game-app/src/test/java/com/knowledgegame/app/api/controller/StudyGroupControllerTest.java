package com.knowledgegame.app.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegame.app.api.dto.CreateStudyGroupRequest;
import com.knowledgegame.app.api.dto.GroupIpLibraryResponse;
import com.knowledgegame.app.api.dto.StudyGroupListResponse;
import com.knowledgegame.app.api.dto.StudyGroupResponse;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = StudyGroupController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, JacksonConfig.class})
class StudyGroupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StudyGroupAppService appService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("创建成功应返回 200 + 正确字段（含 joinPolicy + inviteCode）")
    void create_shouldReturn200() throws Exception {
        StudyGroupResponse response = new StudyGroupResponse();
        response.setId(1L);
        response.setName("测试群组");
        response.setDescription("描述");
        response.setAvatarFileId(10L);
        response.setAvatarUrl("https://example.com/avatar.png");
        response.setOwnerId(100L);
        response.setJoinPolicy("OPEN");
        response.setInviteCode("ABC12345");
        response.setCreatedAt(1718800000000L);
        response.setUpdatedAt(1718800000000L);
        when(appService.create(any())).thenReturn(response);

        CreateStudyGroupRequest request = new CreateStudyGroupRequest();
        request.setName("测试群组");
        request.setDescription("描述");
        request.setAvatarFileId(10L);

        mockMvc.perform(post("/api/study-groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("测试群组"))
                .andExpect(jsonPath("$.data.avatarFileId").value(10))
                .andExpect(jsonPath("$.data.avatarUrl").value("https://example.com/avatar.png"))
                .andExpect(jsonPath("$.data.ownerId").value(100))
                .andExpect(jsonPath("$.data.joinPolicy").value("OPEN"))
                .andExpect(jsonPath("$.data.inviteCode").value("ABC12345"))
                .andExpect(jsonPath("$.data.createdAt").value(1718800000000L))
                .andExpect(jsonPath("$.data.updatedAt").value(1718800000000L));
    }

    @Test
    @DisplayName("重新生成邀请码成功应返回 200 + 新邀请码")
    void regenerateInviteCode_owner_returns200WithNewInviteCode() throws Exception {
        StudyGroupResponse response = new StudyGroupResponse();
        response.setId(1L);
        response.setName("测试群组");
        response.setJoinPolicy("OPEN");
        response.setInviteCode("NEWCODE1");
        response.setCreatedAt(1718800000000L);
        response.setUpdatedAt(1719900000000L);
        when(appService.regenerateInviteCode(anyLong())).thenReturn(response);

        mockMvc.perform(post("/api/study-groups/1/invite-code/regenerate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.inviteCode").value("NEWCODE1"))
                .andExpect(jsonPath("$.data.updatedAt").value(1719900000000L));
    }

    @Test
    @DisplayName("非 OWNER 重新生成应返回 200 + NOT_GROUP_OWNER")
    void regenerateInviteCode_nonOwner_returns200WithNotGroupOwner() throws Exception {
        when(appService.regenerateInviteCode(anyLong()))
                .thenThrow(new BusinessException(ResultCode.NOT_GROUP_OWNER));

        mockMvc.perform(post("/api/study-groups/1/invite-code/regenerate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("仅群主可操作"));
    }

    @Test
    @DisplayName("生成失败应返回 200 + INVITE_CODE_GENERATION_FAILED")
    void regenerateInviteCode_appServiceThrowsGenerationFailed_returns200WithCode() throws Exception {
        when(appService.regenerateInviteCode(anyLong()))
                .thenThrow(new BusinessException(ResultCode.INVITE_CODE_GENERATION_FAILED));

        mockMvc.perform(post("/api/study-groups/1/invite-code/regenerate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("邀请码生成失败，请重试"));
    }

    @Test
    @DisplayName("查询我的群组列表应返回 200 + 正确 JSON 结构")
    void listMyGroups_shouldReturn200WithList() throws Exception {
        StudyGroupListResponse item = new StudyGroupListResponse();
        item.setId(1L);
        item.setName("测试群组");
        item.setDescription("描述");
        item.setAvatarFileId(10L);
        item.setAvatarUrl("https://example.com/avatar.png");
        item.setOwnerId(100L);
        item.setJoinPolicy("OPEN");
        item.setMyRole("OWNER");
        item.setMemberCount(12);
        item.setCreatedAt(1718800000000L);
        item.setUpdatedAt(1718800000000L);
        when(appService.listMyGroups()).thenReturn(List.of(item));

        mockMvc.perform(get("/api/study-groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].name").value("测试群组"))
                .andExpect(jsonPath("$.data[0].avatarFileId").value(10))
                .andExpect(jsonPath("$.data[0].avatarUrl").value("https://example.com/avatar.png"))
                .andExpect(jsonPath("$.data[0].joinPolicy").value("OPEN"))
                .andExpect(jsonPath("$.data[0].myRole").value("OWNER"))
                .andExpect(jsonPath("$.data[0].memberCount").value(12))
                .andExpect(jsonPath("$.data[0].createdAt").value(1718800000000L))
                .andExpect(jsonPath("$.data[0].updatedAt").value(1718800000000L));
    }

    @Test
    @DisplayName("查询我的群组列表为空时应返回 200 + 空数组")
    void listMyGroups_empty_returns200WithEmptyArray() throws Exception {
        when(appService.listMyGroups()).thenReturn(List.of());

        mockMvc.perform(get("/api/study-groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    // ---- IP Library 测试 ----

    @Test
    @DisplayName("GET ip-library 成功应返回 200 + 含 IP 名称/封面图")
    void listIpLibrary_shouldReturn200WithIpInfo() throws Exception {
        GroupIpLibraryResponse item = new GroupIpLibraryResponse();
        item.setId(1L);
        item.setGroupId(1L);
        item.setIpSeriesId(10L);
        item.setIpSeriesName("宝可梦");
        item.setIpSeriesCode("PKM");
        item.setCoverImageFileId(100L);
        item.setCoverImageUrl("https://example.com/cover.png");
        item.setAddedAt(1718800000000L);
        when(appService.listIpLibrary(1L)).thenReturn(List.of(item));

        mockMvc.perform(get("/api/study-groups/1/ip-library"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].ipSeriesId").value(10))
                .andExpect(jsonPath("$.data[0].ipSeriesName").value("宝可梦"))
                .andExpect(jsonPath("$.data[0].ipSeriesCode").value("PKM"))
                .andExpect(jsonPath("$.data[0].coverImageFileId").value(100))
                .andExpect(jsonPath("$.data[0].coverImageUrl").value("https://example.com/cover.png"))
                .andExpect(jsonPath("$.data[0].addedAt").value(1718800000000L));
    }

    @Test
    @DisplayName("GET ip-library 空列表应返回 200 + 空数组")
    void listIpLibrary_empty_returns200WithEmptyArray() throws Exception {
        when(appService.listIpLibrary(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/study-groups/1/ip-library"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("GET ip-library 非成员应返回 NOT_GROUP_MEMBER")
    void listIpLibrary_nonMember_returnsNotGroupMember() throws Exception {
        when(appService.listIpLibrary(1L))
                .thenThrow(new BusinessException(ResultCode.NOT_GROUP_MEMBER));

        mockMvc.perform(get("/api/study-groups/1/ip-library"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("非群组成员"));
    }

    @Test
    @DisplayName("GET ip-library 群组不存在应返回 GROUP_NOT_FOUND")
    void listIpLibrary_groupNotFound_returnsGroupNotFound() throws Exception {
        when(appService.listIpLibrary(1L))
                .thenThrow(new BusinessException(ResultCode.GROUP_NOT_FOUND));

        mockMvc.perform(get("/api/study-groups/1/ip-library"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("群组不存在"));
    }

    @Test
    @DisplayName("PUT ip-library 成功应返回 200 + 更新后列表")
    void updateIpLibrary_shouldReturn200() throws Exception {
        GroupIpLibraryResponse item = new GroupIpLibraryResponse();
        item.setId(1L);
        item.setGroupId(1L);
        item.setIpSeriesId(10L);
        item.setIpSeriesName("宝可梦");
        when(appService.updateIpLibrary(eq(1L), any())).thenReturn(List.of(item));

        UpdateGroupIpLibraryRequest request = new UpdateGroupIpLibraryRequest();
        request.setIpSeriesIds(List.of(10L));

        mockMvc.perform(put("/api/study-groups/1/ip-library")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].ipSeriesId").value(10));
    }

    @Test
    @DisplayName("PUT ip-library ipSeriesIds 为 null 应返回 200 + code=400")
    void updateIpLibrary_nullIpSeriesIds_returns200WithFail() throws Exception {
        UpdateGroupIpLibraryRequest request = new UpdateGroupIpLibraryRequest();

        mockMvc.perform(put("/api/study-groups/1/ip-library")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("PUT ip-library 非 ADMIN 应返回 NOT_GROUP_ADMIN")
    void updateIpLibrary_nonAdmin_returnsNotGroupAdmin() throws Exception {
        when(appService.updateIpLibrary(eq(1L), any()))
                .thenThrow(new BusinessException(ResultCode.NOT_GROUP_ADMIN));

        UpdateGroupIpLibraryRequest request = new UpdateGroupIpLibraryRequest();
        request.setIpSeriesIds(List.of(10L));

        mockMvc.perform(put("/api/study-groups/1/ip-library")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("仅群主或管理员可操作"));
    }

    @Test
    @DisplayName("PUT ip-library IP 不存在应返回 IP_SERIES_NOT_FOUND")
    void updateIpLibrary_ipNotFound_returnsIpSeriesNotFound() throws Exception {
        when(appService.updateIpLibrary(eq(1L), any()))
                .thenThrow(new BusinessException(ResultCode.IP_SERIES_NOT_FOUND));

        UpdateGroupIpLibraryRequest request = new UpdateGroupIpLibraryRequest();
        request.setIpSeriesIds(List.of(999L));

        mockMvc.perform(put("/api/study-groups/1/ip-library")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("IP系列不存在"));
    }

    @Test
    @DisplayName("PUT ip-library IP INACTIVE 应返回 IP_SERIES_NOT_ACTIVE")
    void updateIpLibrary_ipInactive_returnsIpSeriesNotActive() throws Exception {
        when(appService.updateIpLibrary(eq(1L), any()))
                .thenThrow(new BusinessException(ResultCode.IP_SERIES_NOT_ACTIVE));

        UpdateGroupIpLibraryRequest request = new UpdateGroupIpLibraryRequest();
        request.setIpSeriesIds(List.of(10L));

        mockMvc.perform(put("/api/study-groups/1/ip-library")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("IP系列未启用"));
    }
}
