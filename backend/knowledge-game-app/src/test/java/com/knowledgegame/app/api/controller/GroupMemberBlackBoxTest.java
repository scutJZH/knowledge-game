package com.knowledgegame.app.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegame.app.api.dto.GroupMemberResponse;
import com.knowledgegame.app.api.dto.JoinByInviteRequest;
import com.knowledgegame.app.api.dto.StudyGroupResponse;
import com.knowledgegame.app.application.service.GroupMemberAppService;
import com.knowledgegame.app.application.service.StudyGroupAppService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * REQ-49 黑盒测试（GroupMemberController + StudyGroupController）
 * <p>
 * 仅凭 PRD 行为描述 + 接口签名编写，不依赖实现代码。
 * 侧重 HTTP 层端到端行为：状态码、响应体结构、错误码透传。
 */
@WebMvcTest(controllers = {GroupMemberController.class, StudyGroupController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, JacksonConfig.class})
class GroupMemberBlackBoxTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GroupMemberAppService groupMemberAppService;

    @MockitoBean
    private StudyGroupAppService studyGroupAppService;

    @Nested
    @DisplayName("POST /{id}/members — 直接加入")
    class JoinDirectlyTests {

        @Test
        @DisplayName("成功加入 OPEN 群组应返回 200 + GroupMemberResponse")
        void joinDirectly_openGroup_returns200WithMemberResponse() throws Exception {
            GroupMemberResponse response = buildMemberResponse(99L, 1L, 200L, "MEMBER", 0);
            when(groupMemberAppService.joinDirectly(anyLong())).thenReturn(response);

            mockMvc.perform(post("/api/study-groups/1/members"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(99))
                    .andExpect(jsonPath("$.data.groupId").value(1))
                    .andExpect(jsonPath("$.data.userId").value(200))
                    .andExpect(jsonPath("$.data.role").value("MEMBER"))
                    .andExpect(jsonPath("$.data.points").value(0))
                    .andExpect(jsonPath("$.data.joinedAt").isNumber());

            verify(groupMemberAppService, times(1)).joinDirectly(1L);
        }

        @Test
        @DisplayName("仅 INVITE_ONLY 群组加入应返回 GROUP_JOIN_POLICY_MISMATCH")
        void joinDirectly_inviteOnlyGroup_returnsJoinPolicyMismatch() throws Exception {
            when(groupMemberAppService.joinDirectly(anyLong()))
                    .thenThrow(new BusinessException(ResultCode.GROUP_JOIN_POLICY_MISMATCH));

            mockMvc.perform(post("/api/study-groups/1/members"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("该群组需要邀请码加入"));
        }

        @Test
        @DisplayName("已是成员再加入应返回 ALREADY_GROUP_MEMBER")
        void joinDirectly_alreadyMember_returnsAlreadyGroupMember() throws Exception {
            when(groupMemberAppService.joinDirectly(anyLong()))
                    .thenThrow(new BusinessException(ResultCode.ALREADY_GROUP_MEMBER));

            mockMvc.perform(post("/api/study-groups/1/members"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("已是群组成员"));
        }

        @Test
        @DisplayName("群组不存在应返回 GROUP_NOT_FOUND")
        void joinDirectly_groupNotFound_returnsGroupNotFound() throws Exception {
            when(groupMemberAppService.joinDirectly(anyLong()))
                    .thenThrow(new BusinessException(ResultCode.GROUP_NOT_FOUND));

            mockMvc.perform(post("/api/study-groups/999/members"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value("群组不存在"));
        }
    }

    @Nested
    @DisplayName("POST /join-by-invite — 凭邀请码加入")
    class JoinByInviteTests {

        @Test
        @DisplayName("空邀请码应返回 400 FAIL（@NotBlank 校验）")
        void joinByInvite_blankCode_returns400() throws Exception {
            JoinByInviteRequest request = new JoinByInviteRequest();
            request.setInviteCode("");

            mockMvc.perform(post("/api/study-groups/join-by-invite")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));

            verify(groupMemberAppService, never()).joinByInvite(anyString());
        }

        @Test
        @DisplayName("7 位邀请码应返回 400 FAIL（@Pattern 校验，Crockford 必须 8 位）")
        void joinByInvite_tooShort_returns400() throws Exception {
            JoinByInviteRequest request = new JoinByInviteRequest();
            request.setInviteCode("ABC1234");

            mockMvc.perform(post("/api/study-groups/join-by-invite")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));

            verify(groupMemberAppService, never()).joinByInvite(anyString());
        }

        @Test
        @DisplayName("含 L 的邀请码应返回 400 FAIL（@Pattern 校验，Crockford 排除字符）")
        void joinByInvite_containsExcludedCharL_returns400() throws Exception {
            JoinByInviteRequest request = new JoinByInviteRequest();
            request.setInviteCode("ABCDEFGL");

            mockMvc.perform(post("/api/study-groups/join-by-invite")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));

            verify(groupMemberAppService, never()).joinByInvite(anyString());
        }

        @Test
        @DisplayName("格式正确但未匹配应返回 INVITE_CODE_INVALID")
        void joinByInvite_unknownCode_returnsInviteCodeInvalid() throws Exception {
            when(groupMemberAppService.joinByInvite(anyString()))
                    .thenThrow(new BusinessException(ResultCode.INVITE_CODE_INVALID));

            JoinByInviteRequest request = new JoinByInviteRequest();
            request.setInviteCode("ABC12345");

            mockMvc.perform(post("/api/study-groups/join-by-invite")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("邀请码无效"));

            verify(groupMemberAppService, times(1)).joinByInvite("ABC12345");
        }

        @Test
        @DisplayName("已是成员凭邀请码加入应返回 ALREADY_GROUP_MEMBER")
        void joinByInvite_alreadyMember_returnsAlreadyGroupMember() throws Exception {
            when(groupMemberAppService.joinByInvite(anyString()))
                    .thenThrow(new BusinessException(ResultCode.ALREADY_GROUP_MEMBER));

            JoinByInviteRequest request = new JoinByInviteRequest();
            request.setInviteCode("ABC12345");

            mockMvc.perform(post("/api/study-groups/join-by-invite")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("已是群组成员"));
        }

        @Test
        @DisplayName("合法邀请码成功应返回 200 + GroupMemberResponse")
        void joinByInvite_valid_returns200() throws Exception {
            GroupMemberResponse response = buildMemberResponse(88L, 2L, 200L, "MEMBER", 0);
            when(groupMemberAppService.joinByInvite(anyString())).thenReturn(response);

            JoinByInviteRequest request = new JoinByInviteRequest();
            request.setInviteCode("ABC12345");

            mockMvc.perform(post("/api/study-groups/join-by-invite")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(88))
                    .andExpect(jsonPath("$.data.groupId").value(2))
                    .andExpect(jsonPath("$.data.role").value("MEMBER"))
                    .andExpect(jsonPath("$.data.joinedAt").isNumber());

            verify(groupMemberAppService, times(1)).joinByInvite("ABC12345");
        }
    }

    @Nested
    @DisplayName("DELETE /{id}/members/me — 退出群组")
    class LeaveTests {

        @Test
        @DisplayName("非成员退出应返回 NOT_GROUP_MEMBER (403)")
        void leave_nonMember_returnsNotGroupMember() throws Exception {
            doThrow(new BusinessException(ResultCode.NOT_GROUP_MEMBER))
                    .when(groupMemberAppService).leave(anyLong());

            mockMvc.perform(delete("/api/study-groups/1/members/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(403))
                    .andExpect(jsonPath("$.message").value("非群组成员"));

            verify(groupMemberAppService, times(1)).leave(1L);
        }

        @Test
        @DisplayName("OWNER 退出应返回 OWNER_CANNOT_LEAVE (400)")
        void leave_owner_returnsOwnerCannotLeave() throws Exception {
            doThrow(new BusinessException(ResultCode.OWNER_CANNOT_LEAVE))
                    .when(groupMemberAppService).leave(anyLong());

            mockMvc.perform(delete("/api/study-groups/1/members/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("群主不能退出，请先转让群组"));
        }

        @Test
        @DisplayName("成员成功退出应返回 200 + null data")
        void leave_member_returns200WithNullData() throws Exception {
            mockMvc.perform(delete("/api/study-groups/1/members/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isEmpty());

            verify(groupMemberAppService, times(1)).leave(1L);
        }
    }

    @Nested
    @DisplayName("POST /{id}/invite-code/regenerate — 重新生成邀请码")
    class RegenerateInviteCodeTests {

        @Test
        @DisplayName("非 OWNER 应返回 NOT_GROUP_OWNER (403)")
        void regenerateInviteCode_nonOwner_returnsNotGroupOwner() throws Exception {
            when(studyGroupAppService.regenerateInviteCode(anyLong()))
                    .thenThrow(new BusinessException(ResultCode.NOT_GROUP_OWNER));

            mockMvc.perform(post("/api/study-groups/1/invite-code/regenerate"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(403))
                    .andExpect(jsonPath("$.message").value("仅群主可操作"));

            verify(studyGroupAppService, times(1)).regenerateInviteCode(1L);
        }

        @Test
        @DisplayName("OWNER 成功应返回 200 + StudyGroupResponse（含新邀请码）")
        void regenerateInviteCode_owner_returns200WithNewCode() throws Exception {
            StudyGroupResponse response = new StudyGroupResponse();
            response.setId(1L);
            response.setName("测试群组");
            response.setDescription("描述");
            response.setJoinPolicy("INVITE_ONLY");
            response.setInviteCode("NEWCODE9");
            response.setOwnerId(100L);
            response.setCreatedAt(1718800000000L);
            response.setUpdatedAt(1719900000000L);
            when(studyGroupAppService.regenerateInviteCode(anyLong())).thenReturn(response);

            mockMvc.perform(post("/api/study-groups/1/invite-code/regenerate"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.inviteCode").value("NEWCODE9"))
                    .andExpect(jsonPath("$.data.joinPolicy").value("INVITE_ONLY"))
                    .andExpect(jsonPath("$.data.updatedAt").isNumber());
        }

        @Test
        @DisplayName("群组不存在应返回 GROUP_NOT_FOUND")
        void regenerateInviteCode_groupNotFound_returns404() throws Exception {
            when(studyGroupAppService.regenerateInviteCode(anyLong()))
                    .thenThrow(new BusinessException(ResultCode.GROUP_NOT_FOUND));

            mockMvc.perform(post("/api/study-groups/999/invite-code/regenerate"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404));
        }
    }

    @Nested
    @DisplayName("GET /{id}/members/me — 查询当前身份")
    class GetCurrentMemberTests {

        @Test
        @DisplayName("未加入群组应返回 NOT_GROUP_MEMBER (403)")
        void getCurrentMember_nonMember_returnsNotGroupMember() throws Exception {
            when(groupMemberAppService.getCurrentMember(anyLong()))
                    .thenThrow(new BusinessException(ResultCode.NOT_GROUP_MEMBER));

            mockMvc.perform(get("/api/study-groups/1/members/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(403))
                    .andExpect(jsonPath("$.message").value("非群组成员"));

            verify(groupMemberAppService, times(1)).getCurrentMember(1L);
        }

        @Test
        @DisplayName("群组不存在应返回 GROUP_NOT_FOUND")
        void getCurrentMember_groupNotFound_returnsGroupNotFound() throws Exception {
            when(groupMemberAppService.getCurrentMember(anyLong()))
                    .thenThrow(new BusinessException(ResultCode.GROUP_NOT_FOUND));

            mockMvc.perform(get("/api/study-groups/999/members/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value("群组不存在"));
        }

        @Test
        @DisplayName("MEMBER 角色查询应返回正确字段")
        void getCurrentMember_member_returns200WithCorrectFields() throws Exception {
            GroupMemberResponse response = buildMemberResponse(77L, 3L, 300L, "MEMBER", 15);
            when(groupMemberAppService.getCurrentMember(anyLong())).thenReturn(response);

            mockMvc.perform(get("/api/study-groups/3/members/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(77))
                    .andExpect(jsonPath("$.data.groupId").value(3))
                    .andExpect(jsonPath("$.data.userId").value(300))
                    .andExpect(jsonPath("$.data.role").value("MEMBER"))
                    .andExpect(jsonPath("$.data.points").value(15))
                    .andExpect(jsonPath("$.data.joinedAt").isNumber());

            verify(groupMemberAppService, times(1)).getCurrentMember(3L);
        }

        @Test
        @DisplayName("OWNER 角色查询应返回 role=OWNER")
        void getCurrentMember_owner_returnsOwnerRole() throws Exception {
            GroupMemberResponse response = buildMemberResponse(1L, 4L, 400L, "OWNER", 0);
            when(groupMemberAppService.getCurrentMember(anyLong())).thenReturn(response);

            mockMvc.perform(get("/api/study-groups/4/members/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.role").value("OWNER"))
                    .andExpect(jsonPath("$.data.points").value(0));
        }

        @Test
        @DisplayName("ADMIN 角色查询应返回 role=ADMIN")
        void getCurrentMember_admin_returnsAdminRole() throws Exception {
            GroupMemberResponse response = buildMemberResponse(2L, 5L, 500L, "ADMIN", 100);
            when(groupMemberAppService.getCurrentMember(anyLong())).thenReturn(response);

            mockMvc.perform(get("/api/study-groups/5/members/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.role").value("ADMIN"))
                    .andExpect(jsonPath("$.data.points").value(100));
        }
    }

    @Nested
    @DisplayName("跨 API 端到端场景")
    class EndToEndTests {

        @Test
        @DisplayName("直接加入 → 查询身份 → 退出 完整链路")
        void joinQueryLeave_fullCycle() throws Exception {
            // 加入
            GroupMemberResponse joined = buildMemberResponse(10L, 10L, 100L, "MEMBER", 0);
            when(groupMemberAppService.joinDirectly(anyLong())).thenReturn(joined);

            mockMvc.perform(post("/api/study-groups/10/members"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.role").value("MEMBER"));

            // 查询
            GroupMemberResponse current = buildMemberResponse(10L, 10L, 100L, "MEMBER", 5);
            when(groupMemberAppService.getCurrentMember(anyLong())).thenReturn(current);

            mockMvc.perform(get("/api/study-groups/10/members/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            // 退出
            mockMvc.perform(delete("/api/study-groups/10/members/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            // 退出后再查询应返回非成员
            doThrow(new BusinessException(ResultCode.NOT_GROUP_MEMBER))
                    .when(groupMemberAppService).getCurrentMember(anyLong());

            mockMvc.perform(get("/api/study-groups/10/members/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(403));
        }
    }

    private GroupMemberResponse buildMemberResponse(Long id, Long groupId, Long userId,
                                                     String role, int points) {
        GroupMemberResponse response = new GroupMemberResponse();
        response.setId(id);
        response.setGroupId(groupId);
        response.setUserId(userId);
        response.setRole(role);
        response.setPoints(points);
        response.setJoinedAt(1718800000000L);
        return response;
    }
}
