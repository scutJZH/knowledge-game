package com.knowledgegame.app.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegame.app.api.dto.GroupMemberResponse;
import com.knowledgegame.app.api.dto.JoinByInviteRequest;
import com.knowledgegame.app.application.service.GroupMemberAppService;
import com.knowledgegame.app.config.JacksonConfig;
import com.knowledgegame.components.exception.handler.GlobalExceptionHandler;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.common.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GroupMemberController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, JacksonConfig.class})
class GroupMemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GroupMemberAppService appService;

    @Autowired
    private ObjectMapper objectMapper;

    @Nested
    @DisplayName("POST /{id}/members（直接加入）")
    class JoinDirectlyTests {

        @Test
        @DisplayName("成功应返回 200 + GroupMemberResponse（joinedAt 为毫秒时间戳）")
        void joinDirectly_validRequest_returns200WithMemberResponse() throws Exception {
            GroupMemberResponse response = new GroupMemberResponse();
            response.setId(99L);
            response.setGroupId(1L);
            response.setUserId(100L);
            response.setRole("MEMBER");
            response.setPoints(0);
            response.setJoinedAt(1718800000000L);
            when(appService.joinDirectly(anyLong())).thenReturn(response);

            mockMvc.perform(post("/api/study-groups/1/members"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(99))
                    .andExpect(jsonPath("$.data.groupId").value(1))
                    .andExpect(jsonPath("$.data.userId").value(100))
                    .andExpect(jsonPath("$.data.role").value("MEMBER"))
                    .andExpect(jsonPath("$.data.points").value(0))
                    .andExpect(jsonPath("$.data.joinedAt").value(1718800000000L));
        }

        @Test
        @DisplayName("AppService 抛 BusinessException 应透传为 200 + 对应 error code")
        void joinDirectly_appServiceThrowsBusinessException_returns200WithResultCode() throws Exception {
            when(appService.joinDirectly(anyLong()))
                    .thenThrow(new BusinessException(ResultCode.GROUP_NOT_FOUND));

            mockMvc.perform(post("/api/study-groups/999/members"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value("群组不存在"));
        }
    }

    @Nested
    @DisplayName("POST /join-by-invite（凭邀请码加入）")
    class JoinByInviteTests {

        @Test
        @DisplayName("合法邀请码成功应返回 200 + GroupMemberResponse")
        void joinByInvite_validRequest_returns200WithMemberResponse() throws Exception {
            GroupMemberResponse response = new GroupMemberResponse();
            response.setId(99L);
            response.setGroupId(2L);
            response.setUserId(100L);
            response.setRole("MEMBER");
            response.setPoints(0);
            response.setJoinedAt(1718800000000L);
            when(appService.joinByInvite(anyString())).thenReturn(response);

            JoinByInviteRequest request = new JoinByInviteRequest();
            request.setInviteCode("ABC12345");

            mockMvc.perform(post("/api/study-groups/join-by-invite")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(99))
                    .andExpect(jsonPath("$.data.groupId").value(2))
                    .andExpect(jsonPath("$.data.joinedAt").value(1718800000000L));
        }

        @Test
        @DisplayName("inviteCode 为空应返回 400（@NotBlank 触发）")
        void joinByInvite_blankInviteCode_returns400() throws Exception {
            JoinByInviteRequest request = new JoinByInviteRequest();
            request.setInviteCode("");

            mockMvc.perform(post("/api/study-groups/join-by-invite")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("inviteCode 格式错（7 位）应返回 400（@Pattern 触发）")
        void joinByInvite_invalidFormat_returns400() throws Exception {
            JoinByInviteRequest request = new JoinByInviteRequest();
            request.setInviteCode("ABC1234");

            mockMvc.perform(post("/api/study-groups/join-by-invite")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("AppService 抛 BusinessException 应透传为 200 + 对应 error code")
        void joinByInvite_appServiceThrowsBusinessException_returns200WithResultCode() throws Exception {
            when(appService.joinByInvite(anyString()))
                    .thenThrow(new BusinessException(ResultCode.INVITE_CODE_INVALID));

            JoinByInviteRequest request = new JoinByInviteRequest();
            request.setInviteCode("XXXXXXXX");

            mockMvc.perform(post("/api/study-groups/join-by-invite")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("邀请码无效"));
        }
    }

    @Nested
    @DisplayName("DELETE /{id}/members/me（退出群组）")
    class LeaveTests {

        @Test
        @DisplayName("成功退出应返回 200 + null data")
        void leave_validMember_returns200WithNullData() throws Exception {
            mockMvc.perform(delete("/api/study-groups/1/members/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isEmpty());
        }

        @Test
        @DisplayName("OWNER 退出应返回 200 + OWNER_CANNOT_LEAVE")
        void leave_owner_returns200WithOwnerCannotLeave() throws Exception {
            doThrow(new BusinessException(ResultCode.OWNER_CANNOT_LEAVE))
                    .when(appService).leave(1L);

            mockMvc.perform(delete("/api/study-groups/1/members/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("群主不能退出，请先转让群组"));
        }

        @Test
        @DisplayName("非成员退出应返回 200 + NOT_GROUP_MEMBER")
        void leave_nonMember_returns200WithNotGroupMember() throws Exception {
            doThrow(new BusinessException(ResultCode.NOT_GROUP_MEMBER))
                    .when(appService).leave(1L);

            mockMvc.perform(delete("/api/study-groups/1/members/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(403))
                    .andExpect(jsonPath("$.message").value("非群组成员"));
        }
    }

    @Nested
    @DisplayName("GET /{id}/members/me（查询当前身份）")
    class GetCurrentMemberTests {

        @Test
        @DisplayName("成员查询成功应返回 200 + GroupMemberResponse")
        void getCurrentMember_member_returns200WithResponse() throws Exception {
            GroupMemberResponse response = new GroupMemberResponse();
            response.setId(99L);
            response.setGroupId(1L);
            response.setUserId(100L);
            response.setRole("MEMBER");
            response.setPoints(10);
            response.setJoinedAt(1718800000000L);
            when(appService.getCurrentMember(anyLong())).thenReturn(response);

            mockMvc.perform(get("/api/study-groups/1/members/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.role").value("MEMBER"))
                    .andExpect(jsonPath("$.data.points").value(10));
        }

        @Test
        @DisplayName("AppService 抛 BusinessException 应透传")
        void getCurrentMember_appServiceThrowsBusinessException_returns200WithResultCode() throws Exception {
            when(appService.getCurrentMember(anyLong()))
                    .thenThrow(new BusinessException(ResultCode.NOT_GROUP_MEMBER));

            mockMvc.perform(get("/api/study-groups/1/members/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(403))
                    .andExpect(jsonPath("$.message").value("非群组成员"));
        }
    }

    @Nested
    @DisplayName("PUT /{id}/members/{userId}（更新角色）")
    class UpdateRoleTests {

        @Test
        @DisplayName("成功应返回 200 + null data")
        void updateRole_validRequest_returns200WithNullData() throws Exception {
            String body = "{\"role\":\"ADMIN\"}";

            mockMvc.perform(put("/api/study-groups/1/members/200")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isEmpty());
        }

        @Test
        @DisplayName("role 为空应返回 400")
        void updateRole_blankRole_returns400() throws Exception {
            String body = "{\"role\":\"\"}";

            mockMvc.perform(put("/api/study-groups/1/members/200")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("role 非法值应返回 400")
        void updateRole_invalidRole_returns400() throws Exception {
            String body = "{\"role\":\"OWNER\"}";

            mockMvc.perform(put("/api/study-groups/1/members/200")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("非 OWNER 操作应返回 NOT_GROUP_OWNER")
        void updateRole_nonOwner_returnsNotGroupOwner() throws Exception {
            doThrow(new BusinessException(ResultCode.NOT_GROUP_OWNER))
                    .when(appService).updateRole(1L, 200L, "ADMIN");

            String body = "{\"role\":\"ADMIN\"}";

            mockMvc.perform(put("/api/study-groups/1/members/200")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(403))
                    .andExpect(jsonPath("$.message").value("仅群主可操作"));
        }
    }

    @Nested
    @DisplayName("POST /{id}/transfer-ownership（转让群主）")
    class TransferOwnershipTests {

        @Test
        @DisplayName("成功应返回 200 + null data")
        void transferOwnership_validRequest_returns200WithNullData() throws Exception {
            String body = "{\"toUserId\":200}";

            mockMvc.perform(post("/api/study-groups/1/transfer-ownership")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isEmpty());
        }

        @Test
        @DisplayName("toUserId 为空应返回 400")
        void transferOwnership_nullToUserId_returns400() throws Exception {
            String body = "{}";

            mockMvc.perform(post("/api/study-groups/1/transfer-ownership")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("非 OWNER 操作应返回 NOT_GROUP_OWNER")
        void transferOwnership_nonOwner_returnsNotGroupOwner() throws Exception {
            doThrow(new BusinessException(ResultCode.NOT_GROUP_OWNER))
                    .when(appService).transferOwnership(1L, 200L);

            String body = "{\"toUserId\":200}";

            mockMvc.perform(post("/api/study-groups/1/transfer-ownership")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(403))
                    .andExpect(jsonPath("$.message").value("仅群主可操作"));
        }
    }
}
