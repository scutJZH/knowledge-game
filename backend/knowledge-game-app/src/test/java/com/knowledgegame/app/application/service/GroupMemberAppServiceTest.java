package com.knowledgegame.app.application.service;

import com.knowledgegame.app.api.dto.GroupMemberResponse;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.common.result.ResultCode;
import com.knowledgegame.core.domain.model.domainenum.GroupRole;
import com.knowledgegame.core.domain.model.domainenum.JoinPolicy;
import com.knowledgegame.core.domain.model.domainenum.StudyGroupStatus;
import com.knowledgegame.core.domain.model.entity.GroupMember;
import com.knowledgegame.core.domain.model.entity.StudyGroup;
import com.knowledgegame.core.domain.model.vo.InviteCode;
import com.knowledgegame.core.domain.port.outbound.GroupMemberRepository;
import com.knowledgegame.core.domain.port.outbound.StudyGroupRepository;
import com.knowledgegame.core.domain.port.outbound.UserRepositoryPort;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupMemberAppServiceTest {

    @Mock
    private StudyGroupRepository studyGroupRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private UserRepositoryPort userRepositoryPort;

    private GroupMemberAppService appService;

    private MockedStatic<com.knowledgegame.auth.security.SecurityUtils> securityUtilsMock;

    @BeforeEach
    void setUp() {
        appService = new GroupMemberAppService(studyGroupRepository, groupMemberRepository, userRepositoryPort);
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
                    200L, StudyGroupStatus.ACTIVE, JoinPolicy.INVITE_ONLY, InviteCode.of("ABC12345"),
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
                    200L, StudyGroupStatus.ACTIVE, JoinPolicy.OPEN, InviteCode.of("ABC12345"),
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
                    200L, StudyGroupStatus.ACTIVE, JoinPolicy.OPEN, InviteCode.of("ABC12345"),
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
                    200L, StudyGroupStatus.ACTIVE, JoinPolicy.OPEN, InviteCode.of("ABC12345"),
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
                    200L, StudyGroupStatus.ACTIVE, JoinPolicy.INVITE_ONLY, InviteCode.of("ABC12345"),
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
                    200L, StudyGroupStatus.ACTIVE, JoinPolicy.INVITE_ONLY, InviteCode.of("ABC12345"),
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
                    200L, StudyGroupStatus.ACTIVE, JoinPolicy.INVITE_ONLY, InviteCode.of("ABC12345"),
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

    @Nested
    @DisplayName("updateRole")
    class UpdateRoleTests {

        @Test
        @DisplayName("OWNER 提升 MEMBER 为 ADMIN 应保存 role=ADMIN")
        void updateRole_promoteMemberToAdmin_savesAdminRole() {
            GroupMember caller = GroupMember.reconstruct(1L, 10L, 100L,
                    GroupRole.OWNER, 0, LocalDateTime.now());
            GroupMember target = GroupMember.reconstruct(2L, 10L, 200L,
                    GroupRole.MEMBER, 0, LocalDateTime.now());
            when(groupMemberRepository.findByGroupIdAndUserId(10L, 100L)).thenReturn(Optional.of(caller));
            when(groupMemberRepository.findByGroupIdAndUserId(10L, 200L)).thenReturn(Optional.of(target));

            appService.updateRole(10L, 200L, "ADMIN");

            ArgumentCaptor<GroupMember> captor = ArgumentCaptor.forClass(GroupMember.class);
            verify(groupMemberRepository).save(captor.capture());
            assertEquals(GroupRole.ADMIN, captor.getValue().getRole());
        }

        @Test
        @DisplayName("OWNER 降级 ADMIN 为 MEMBER 应保存 role=MEMBER")
        void updateRole_demoteAdminToMember_savesMemberRole() {
            GroupMember caller = GroupMember.reconstruct(1L, 10L, 100L,
                    GroupRole.OWNER, 0, LocalDateTime.now());
            GroupMember target = GroupMember.reconstruct(2L, 10L, 200L,
                    GroupRole.ADMIN, 0, LocalDateTime.now());
            when(groupMemberRepository.findByGroupIdAndUserId(10L, 100L)).thenReturn(Optional.of(caller));
            when(groupMemberRepository.findByGroupIdAndUserId(10L, 200L)).thenReturn(Optional.of(target));

            appService.updateRole(10L, 200L, "MEMBER");

            ArgumentCaptor<GroupMember> captor = ArgumentCaptor.forClass(GroupMember.class);
            verify(groupMemberRepository).save(captor.capture());
            assertEquals(GroupRole.MEMBER, captor.getValue().getRole());
        }

        @Test
        @DisplayName("ADMIN 已是目标角色应幂等（不抛异常）")
        void updateRole_adminToAdmin_isIdempotent() {
            GroupMember caller = GroupMember.reconstruct(1L, 10L, 100L,
                    GroupRole.OWNER, 0, LocalDateTime.now());
            GroupMember target = GroupMember.reconstruct(2L, 10L, 200L,
                    GroupRole.ADMIN, 0, LocalDateTime.now());
            when(groupMemberRepository.findByGroupIdAndUserId(10L, 100L)).thenReturn(Optional.of(caller));
            when(groupMemberRepository.findByGroupIdAndUserId(10L, 200L)).thenReturn(Optional.of(target));

            appService.updateRole(10L, 200L, "ADMIN");

            verify(groupMemberRepository).save(target);
        }

        @Test
        @DisplayName("非 OWNER 操作应抛 NOT_GROUP_OWNER")
        void updateRole_nonOwner_throwsNotGroupOwner() {
            GroupMember caller = GroupMember.reconstruct(1L, 10L, 100L,
                    GroupRole.ADMIN, 0, LocalDateTime.now());
            when(groupMemberRepository.findByGroupIdAndUserId(10L, 100L)).thenReturn(Optional.of(caller));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> appService.updateRole(10L, 200L, "ADMIN"));
            assertEquals(ResultCode.NOT_GROUP_OWNER.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("目标不存在应抛 NOT_GROUP_MEMBER")
        void updateRole_targetNotFound_throwsNotGroupMember() {
            GroupMember caller = GroupMember.reconstruct(1L, 10L, 100L,
                    GroupRole.OWNER, 0, LocalDateTime.now());
            when(groupMemberRepository.findByGroupIdAndUserId(10L, 100L)).thenReturn(Optional.of(caller));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> appService.updateRole(10L, 999L, "ADMIN"));
            assertEquals(ResultCode.NOT_GROUP_MEMBER.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("目标不在该群组应抛 NOT_GROUP_MEMBER")
        void updateRole_targetDifferentGroup_throwsNotGroupMember() {
            GroupMember caller = GroupMember.reconstruct(1L, 10L, 100L,
                    GroupRole.OWNER, 0, LocalDateTime.now());
            GroupMember target = GroupMember.reconstruct(2L, 20L, 200L,
                    GroupRole.MEMBER, 0, LocalDateTime.now());
            when(groupMemberRepository.findByGroupIdAndUserId(10L, 100L)).thenReturn(Optional.of(caller));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> appService.updateRole(10L, 200L, "ADMIN"));
            assertEquals(ResultCode.NOT_GROUP_MEMBER.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("目标为 OWNER 应抛 CANNOT_CHANGE_OWNER_ROLE")
        void updateRole_targetIsOwner_throwsCannotChangeOwnerRole() {
            GroupMember caller = GroupMember.reconstruct(1L, 10L, 100L,
                    GroupRole.OWNER, 0, LocalDateTime.now());
            GroupMember target = GroupMember.reconstruct(2L, 10L, 200L,
                    GroupRole.OWNER, 0, LocalDateTime.now());
            when(groupMemberRepository.findByGroupIdAndUserId(10L, 100L)).thenReturn(Optional.of(caller));
            when(groupMemberRepository.findByGroupIdAndUserId(10L, 200L)).thenReturn(Optional.of(target));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> appService.updateRole(10L, 200L, "ADMIN"));
            assertEquals(ResultCode.CANNOT_CHANGE_OWNER_ROLE.getCode(), ex.getCode());
        }
    }

    @Nested
    @DisplayName("transferOwnership")
    class TransferOwnershipTests {

        @Test
        @DisplayName("OWNER 转让成功应保存原 OWNER(ADMIN) 和目标(OWNER) + 更新 StudyGroup.ownerId")
        void transferOwnership_success_savesBothAndUpdatesStudyGroup() {
            GroupMember owner = GroupMember.reconstruct(1L, 10L, 100L,
                    GroupRole.OWNER, 0, LocalDateTime.now());
            GroupMember target = GroupMember.reconstruct(2L, 10L, 200L,
                    GroupRole.MEMBER, 0, LocalDateTime.now());
            StudyGroup group = StudyGroup.reconstruct(10L, "群组", null, null, 100L, StudyGroupStatus.ACTIVE,
                    JoinPolicy.OPEN, InviteCode.of("ABC12345"),
                    LocalDateTime.now(), LocalDateTime.now());

            when(groupMemberRepository.findByGroupIdAndUserId(10L, 100L)).thenReturn(Optional.of(owner));
            when(groupMemberRepository.findByGroupIdAndUserId(10L, 200L)).thenReturn(Optional.of(target));
            when(studyGroupRepository.findById(10L)).thenReturn(Optional.of(group));

            appService.transferOwnership(10L, 200L);

            verify(studyGroupRepository).save(group);
            assertEquals(200L, group.getOwnerId());

            ArgumentCaptor<GroupMember> captor = ArgumentCaptor.forClass(GroupMember.class);
            verify(groupMemberRepository, times(2)).save(captor.capture());
            List<GroupMember> saved = captor.getAllValues();
            assertEquals(2, saved.size());
            assertEquals(GroupRole.ADMIN, saved.get(0).getRole());
            assertEquals(GroupRole.OWNER, saved.get(1).getRole());
        }

        @Test
        @DisplayName("非 OWNER 转让应抛 NOT_GROUP_OWNER")
        void transferOwnership_nonOwner_throwsNotGroupOwner() {
            GroupMember caller = GroupMember.reconstruct(1L, 10L, 100L,
                    GroupRole.ADMIN, 0, LocalDateTime.now());
            when(groupMemberRepository.findByGroupIdAndUserId(10L, 100L)).thenReturn(Optional.of(caller));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> appService.transferOwnership(10L, 200L));
            assertEquals(ResultCode.NOT_GROUP_OWNER.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("目标不存在应抛 NOT_GROUP_MEMBER")
        void transferOwnership_targetNotFound_throwsNotGroupMember() {
            GroupMember owner = GroupMember.reconstruct(1L, 10L, 100L,
                    GroupRole.OWNER, 0, LocalDateTime.now());
            when(groupMemberRepository.findByGroupIdAndUserId(10L, 100L)).thenReturn(Optional.of(owner));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> appService.transferOwnership(10L, 999L));
            assertEquals(ResultCode.NOT_GROUP_MEMBER.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("目标不在同群组应抛 NOT_GROUP_MEMBER")
        void transferOwnership_targetDifferentGroup_throwsNotGroupMember() {
            GroupMember owner = GroupMember.reconstruct(1L, 10L, 100L,
                    GroupRole.OWNER, 0, LocalDateTime.now());
            GroupMember target = GroupMember.reconstruct(2L, 20L, 200L,
                    GroupRole.MEMBER, 0, LocalDateTime.now());
            when(groupMemberRepository.findByGroupIdAndUserId(10L, 100L)).thenReturn(Optional.of(owner));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> appService.transferOwnership(10L, 200L));
            assertEquals(ResultCode.NOT_GROUP_MEMBER.getCode(), ex.getCode());
        }
    }
}
