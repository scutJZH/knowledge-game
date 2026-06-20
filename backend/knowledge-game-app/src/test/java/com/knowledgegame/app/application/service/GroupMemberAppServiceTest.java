package com.knowledgegame.app.application.service;

import com.knowledgegame.app.api.dto.GroupMemberResponse;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.common.result.ResultCode;
import com.knowledgegame.core.domain.model.domainenum.GroupRole;
import com.knowledgegame.core.domain.model.domainenum.JoinPolicy;
import com.knowledgegame.core.domain.model.entity.GroupMember;
import com.knowledgegame.core.domain.model.entity.StudyGroup;
import com.knowledgegame.core.domain.model.vo.InviteCode;
import com.knowledgegame.core.domain.port.outbound.GroupMemberRepository;
import com.knowledgegame.core.domain.port.outbound.StudyGroupRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupMemberAppServiceTest {

    @Mock
    private StudyGroupRepository studyGroupRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    private GroupMemberAppService appService;

    private MockedStatic<com.knowledgegame.auth.security.SecurityUtils> securityUtilsMock;

    @BeforeEach
    void setUp() {
        appService = new GroupMemberAppService(studyGroupRepository, groupMemberRepository);
        securityUtilsMock = mockStatic(com.knowledgegame.auth.security.SecurityUtils.class);
        securityUtilsMock.when(com.knowledgegame.auth.security.SecurityUtils::getCurrentUserId).thenReturn(100L);
    }

    @AfterEach
    void tearDown() {
        securityUtilsMock.close();
    }

    @Nested
    @DisplayName("joinDirectly")
    class JoinDirectlyTests {

        @Test
        @DisplayName("群组不存在应抛 GROUP_NOT_FOUND")
        void joinDirectly_groupNotFound_throwsGroupNotFound() {
            when(studyGroupRepository.findById(1L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> appService.joinDirectly(1L));
            assertEquals(ResultCode.GROUP_NOT_FOUND.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("INVITE_ONLY 群组应抛 GROUP_JOIN_POLICY_MISMATCH")
        void joinDirectly_inviteOnlyGroup_throwsJoinPolicyMismatch() {
            StudyGroup group = StudyGroup.reconstruct(1L, "私密群组", null, null,
                    200L, JoinPolicy.INVITE_ONLY, InviteCode.of("ABC12345"),
                    java.time.LocalDateTime.now(), java.time.LocalDateTime.now());
            when(studyGroupRepository.findById(1L)).thenReturn(Optional.of(group));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> appService.joinDirectly(1L));
            assertEquals(ResultCode.GROUP_JOIN_POLICY_MISMATCH.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("已是成员应抛 ALREADY_GROUP_MEMBER")
        void joinDirectly_alreadyMember_throwsAlreadyGroupMember() {
            StudyGroup group = StudyGroup.reconstruct(1L, "开放群组", null, null,
                    200L, JoinPolicy.OPEN, InviteCode.of("ABC12345"),
                    java.time.LocalDateTime.now(), java.time.LocalDateTime.now());
            when(studyGroupRepository.findById(1L)).thenReturn(Optional.of(group));
            when(groupMemberRepository.existsByGroupIdAndUserId(1L, 100L)).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> appService.joinDirectly(1L));
            assertEquals(ResultCode.ALREADY_GROUP_MEMBER.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("OPEN 群组新用户应保存 role=MEMBER 的成员记录")
        void joinDirectly_openGroupNewUser_savesMemberWithMemberRole() {
            StudyGroup group = StudyGroup.reconstruct(1L, "开放群组", null, null,
                    200L, JoinPolicy.OPEN, InviteCode.of("ABC12345"),
                    java.time.LocalDateTime.now(), java.time.LocalDateTime.now());
            when(studyGroupRepository.findById(1L)).thenReturn(Optional.of(group));
            when(groupMemberRepository.existsByGroupIdAndUserId(1L, 100L)).thenReturn(false);

            GroupMember mockSaved = GroupMember.reconstruct(99L, 1L, 100L,
                    GroupRole.MEMBER, 0, java.time.LocalDateTime.now());
            when(groupMemberRepository.save(any())).thenReturn(mockSaved);

            GroupMemberResponse response = appService.joinDirectly(1L);

            assertNotNull(response);
            assertEquals(99L, response.getId());
            assertEquals(1L, response.getGroupId());
            assertEquals(100L, response.getUserId());
            assertEquals("MEMBER", response.getRole());
            assertEquals(0, response.getPoints());
            assertNotNull(response.getJoinedAt());

            ArgumentCaptor<GroupMember> memberCaptor = ArgumentCaptor.forClass(GroupMember.class);
            verify(groupMemberRepository).save(memberCaptor.capture());
            assertEquals(GroupRole.MEMBER, memberCaptor.getValue().getRole());
            assertEquals(0, memberCaptor.getValue().getPoints());
            assertEquals(1L, memberCaptor.getValue().getGroupId());
            assertEquals(100L, memberCaptor.getValue().getUserId());
        }

        @Test
        @DisplayName("save 抛 DataIntegrityViolationException 应转 ALREADY_GROUP_MEMBER")
        void joinDirectly_concurrentSaveThrowsDIVI_throwsAlreadyGroupMember() {
            StudyGroup group = StudyGroup.reconstruct(1L, "开放群组", null, null,
                    200L, JoinPolicy.OPEN, InviteCode.of("ABC12345"),
                    java.time.LocalDateTime.now(), java.time.LocalDateTime.now());
            when(studyGroupRepository.findById(1L)).thenReturn(Optional.of(group));
            when(groupMemberRepository.existsByGroupIdAndUserId(1L, 100L)).thenReturn(false);
            when(groupMemberRepository.save(any())).thenThrow(new DataIntegrityViolationException("dup"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> appService.joinDirectly(1L));
            assertEquals(ResultCode.ALREADY_GROUP_MEMBER.getCode(), ex.getCode());
        }
    }

    @Nested
    @DisplayName("joinByInvite")
    class JoinByInviteTests {

        @Test
        @DisplayName("格式非法（含 I）应抛 INVITE_CODE_INVALID")
        void joinByInvite_invalidFormat_throwsInviteCodeInvalid() {
            assertThrows(BusinessException.class, () -> appService.joinByInvite("ABCI1234"));
        }

        @Test
        @DisplayName("格式合法但不存在的邀请码应抛 INVITE_CODE_INVALID")
        void joinByInvite_unknownCode_throwsInviteCodeInvalid() {
            when(studyGroupRepository.findByInviteCode("ABC12345")).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> appService.joinByInvite("ABC12345"));
            assertEquals(ResultCode.INVITE_CODE_INVALID.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("已是成员再凭邀请码加入应抛 ALREADY_GROUP_MEMBER")
        void joinByInvite_alreadyMember_throwsAlreadyGroupMember() {
            StudyGroup group = StudyGroup.reconstruct(1L, "群组", null, null,
                    200L, JoinPolicy.INVITE_ONLY, InviteCode.of("ABC12345"),
                    java.time.LocalDateTime.now(), java.time.LocalDateTime.now());
            when(studyGroupRepository.findByInviteCode("ABC12345")).thenReturn(Optional.of(group));
            when(groupMemberRepository.existsByGroupIdAndUserId(1L, 100L)).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> appService.joinByInvite("ABC12345"));
            assertEquals(ResultCode.ALREADY_GROUP_MEMBER.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("合法邀请码新用户应保存 member 记录（groupId 来自查询结果）")
        void joinByInvite_validCodeNewUser_savesMember() {
            StudyGroup group = StudyGroup.reconstruct(2L, "邀请码群组", null, null,
                    200L, JoinPolicy.INVITE_ONLY, InviteCode.of("ABC12345"),
                    java.time.LocalDateTime.now(), java.time.LocalDateTime.now());
            when(studyGroupRepository.findByInviteCode("ABC12345")).thenReturn(Optional.of(group));
            when(groupMemberRepository.existsByGroupIdAndUserId(2L, 100L)).thenReturn(false);

            GroupMember mockSaved = GroupMember.reconstruct(99L, 2L, 100L,
                    GroupRole.MEMBER, 0, java.time.LocalDateTime.now());
            when(groupMemberRepository.save(any())).thenReturn(mockSaved);

            GroupMemberResponse response = appService.joinByInvite("ABC12345");

            assertNotNull(response);
            assertEquals(2L, response.getGroupId());

            ArgumentCaptor<GroupMember> memberCaptor = ArgumentCaptor.forClass(GroupMember.class);
            verify(groupMemberRepository).save(memberCaptor.capture());
            assertEquals(2L, memberCaptor.getValue().getGroupId());
            assertEquals(100L, memberCaptor.getValue().getUserId());
            assertEquals(GroupRole.MEMBER, memberCaptor.getValue().getRole());
        }

        @Test
        @DisplayName("save 抛 DIVI 应转 ALREADY_GROUP_MEMBER")
        void joinByInvite_concurrentSaveThrowsDIVI_throwsAlreadyGroupMember() {
            StudyGroup group = StudyGroup.reconstruct(1L, "群组", null, null,
                    200L, JoinPolicy.INVITE_ONLY, InviteCode.of("ABC12345"),
                    java.time.LocalDateTime.now(), java.time.LocalDateTime.now());
            when(studyGroupRepository.findByInviteCode("ABC12345")).thenReturn(Optional.of(group));
            when(groupMemberRepository.existsByGroupIdAndUserId(1L, 100L)).thenReturn(false);
            when(groupMemberRepository.save(any())).thenThrow(new DataIntegrityViolationException("dup"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> appService.joinByInvite("ABC12345"));
            assertEquals(ResultCode.ALREADY_GROUP_MEMBER.getCode(), ex.getCode());
        }
    }

    @Nested
    @DisplayName("leave")
    class LeaveTests {

        @Test
        @DisplayName("非成员退出应抛 NOT_GROUP_MEMBER")
        void leave_nonMember_throwsNotGroupMember() {
            when(groupMemberRepository.findByGroupIdAndUserId(1L, 100L))
                    .thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> appService.leave(1L));
            assertEquals(ResultCode.NOT_GROUP_MEMBER.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("OWNER 退出应抛 OWNER_CANNOT_LEAVE")
        void leave_owner_throwsOwnerCannotLeave() {
            GroupMember owner = GroupMember.reconstruct(99L, 1L, 100L,
                    GroupRole.OWNER, 0, java.time.LocalDateTime.now());
            when(groupMemberRepository.findByGroupIdAndUserId(1L, 100L))
                    .thenReturn(Optional.of(owner));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> appService.leave(1L));
            assertEquals(ResultCode.OWNER_CANNOT_LEAVE.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("ADMIN 退出应调用 deleteByGroupIdAndUserId")
        void leave_admin_callsDeleteByGroupIdAndUserId() {
            GroupMember admin = GroupMember.reconstruct(99L, 1L, 100L,
                    GroupRole.ADMIN, 50, java.time.LocalDateTime.now());
            when(groupMemberRepository.findByGroupIdAndUserId(1L, 100L))
                    .thenReturn(Optional.of(admin));

            appService.leave(1L);

            verify(groupMemberRepository).deleteByGroupIdAndUserId(1L, 100L);
        }

        @Test
        @DisplayName("MEMBER 退出应调用 deleteByGroupIdAndUserId")
        void leave_member_callsDeleteByGroupIdAndUserId() {
            GroupMember member = GroupMember.reconstruct(99L, 1L, 100L,
                    GroupRole.MEMBER, 10, java.time.LocalDateTime.now());
            when(groupMemberRepository.findByGroupIdAndUserId(1L, 100L))
                    .thenReturn(Optional.of(member));

            appService.leave(1L);

            verify(groupMemberRepository).deleteByGroupIdAndUserId(1L, 100L);
        }
    }

    @Nested
    @DisplayName("getCurrentMember")
    class GetCurrentMemberTests {

        @Test
        @DisplayName("群组不存在应抛 GROUP_NOT_FOUND")
        void getCurrentMember_groupNotFound_throwsGroupNotFound() {
            when(studyGroupRepository.findById(1L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> appService.getCurrentMember(1L));
            assertEquals(ResultCode.GROUP_NOT_FOUND.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("非成员应抛 NOT_GROUP_MEMBER")
        void getCurrentMember_nonMember_throwsNotGroupMember() {
            StudyGroup group = StudyGroup.create("群组", null, null, 200L, JoinPolicy.OPEN);
            when(studyGroupRepository.findById(1L)).thenReturn(Optional.of(group));
            when(groupMemberRepository.findByGroupIdAndUserId(1L, 100L))
                    .thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> appService.getCurrentMember(1L));
            assertEquals(ResultCode.NOT_GROUP_MEMBER.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("MEMBER 身份查询应返回正确字段")
        void getCurrentMember_member_returnsMemberResponse() {
            StudyGroup group = StudyGroup.create("群组", null, null, 200L, JoinPolicy.OPEN);
            when(studyGroupRepository.findById(1L)).thenReturn(Optional.of(group));
            GroupMember member = GroupMember.reconstruct(99L, 1L, 100L,
                    GroupRole.MEMBER, 10, java.time.LocalDateTime.now());
            when(groupMemberRepository.findByGroupIdAndUserId(1L, 100L))
                    .thenReturn(Optional.of(member));

            GroupMemberResponse response = appService.getCurrentMember(1L);

            assertEquals(99L, response.getId());
            assertEquals(1L, response.getGroupId());
            assertEquals(100L, response.getUserId());
            assertEquals("MEMBER", response.getRole());
            assertEquals(10, response.getPoints());
            assertNotNull(response.getJoinedAt());
        }

        @Test
        @DisplayName("OWNER 身份查询应返回 OWNER 角色")
        void getCurrentMember_owner_returnsOwnerRole() {
            StudyGroup group = StudyGroup.create("群组", null, null, 200L, JoinPolicy.OPEN);
            when(studyGroupRepository.findById(1L)).thenReturn(Optional.of(group));
            GroupMember owner = GroupMember.reconstruct(99L, 1L, 100L,
                    GroupRole.OWNER, 0, java.time.LocalDateTime.now());
            when(groupMemberRepository.findByGroupIdAndUserId(1L, 100L))
                    .thenReturn(Optional.of(owner));

            GroupMemberResponse response = appService.getCurrentMember(1L);

            assertEquals("OWNER", response.getRole());
        }
    }
}
