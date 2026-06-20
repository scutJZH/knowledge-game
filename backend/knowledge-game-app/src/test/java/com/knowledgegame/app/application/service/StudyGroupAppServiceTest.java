package com.knowledgegame.app.application.service;

import com.knowledgegame.app.api.dto.CreateStudyGroupRequest;
import com.knowledgegame.app.api.dto.StudyGroupResponse;
import com.knowledgegame.components.feign.client.FileServiceClient;
import com.knowledgegame.components.feign.dto.FileInfoResponse;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.common.result.Result;
import com.knowledgegame.core.common.result.ResultCode;
import com.knowledgegame.core.domain.model.domainenum.GroupRole;
import com.knowledgegame.core.domain.model.domainenum.JoinPolicy;
import com.knowledgegame.core.domain.model.entity.GroupMember;
import com.knowledgegame.core.domain.model.entity.StudyGroup;
import com.knowledgegame.core.domain.model.vo.FileRef;
import com.knowledgegame.core.domain.model.vo.InviteCode;
import com.knowledgegame.core.domain.port.outbound.GroupMemberRepository;
import com.knowledgegame.core.domain.port.outbound.StudyGroupRepository;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudyGroupAppServiceTest {

    @Mock
    private StudyGroupRepository studyGroupRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private FileServiceClient fileServiceClient;

    private StudyGroupAppService appService;

    private MockedStatic<com.knowledgegame.auth.security.SecurityUtils> securityUtilsMock;

    @BeforeEach
    void setUp() {
        appService = new StudyGroupAppService(studyGroupRepository, groupMemberRepository, fileServiceClient);
        securityUtilsMock = mockStatic(com.knowledgegame.auth.security.SecurityUtils.class);
        securityUtilsMock.when(com.knowledgegame.auth.security.SecurityUtils::getCurrentUserId).thenReturn(100L);
    }

    @AfterEach
    void tearDown() {
        securityUtilsMock.close();
    }

    @Test
    @DisplayName("正常创建群组（含头像校验）")
    void create_shouldCreateGroupWithAvatar() {
        CreateStudyGroupRequest request = buildRequest("测试群组", "描述", 10L, null);

        FileInfoResponse fileInfo = FileInfoResponse.builder()
                .fileId(10L)
                .url("https://example.com/avatar.png")
                .metadata(Map.of("bizType", "STUDY_GROUP_AVATAR", "userId", 100L))
                .build();
        when(fileServiceClient.getFileInfo(10L)).thenReturn(Result.success(fileInfo));

        StudyGroup mockSaved = StudyGroup.reconstruct(1L, "测试群组", "描述",
                FileRef.of(10L, "https://example.com/avatar.png"),
                100L, JoinPolicy.OPEN, InviteCode.of("ABC12345"),
                java.time.LocalDateTime.now(), java.time.LocalDateTime.now());
        when(studyGroupRepository.save(any())).thenReturn(mockSaved);

        StudyGroupResponse response = appService.create(request);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("测试群组", response.getName());
        assertEquals(10L, response.getAvatarFileId());
        assertEquals("https://example.com/avatar.png", response.getAvatarUrl());

        ArgumentCaptor<StudyGroup> groupCaptor = ArgumentCaptor.forClass(StudyGroup.class);
        verify(studyGroupRepository).save(groupCaptor.capture());
        assertEquals("测试群组", groupCaptor.getValue().getName());
        assertEquals(100L, groupCaptor.getValue().getOwnerId());
        assertNotNull(groupCaptor.getValue().getAvatar());
        assertEquals("https://example.com/avatar.png", groupCaptor.getValue().getAvatar().url());

        ArgumentCaptor<GroupMember> memberCaptor = ArgumentCaptor.forClass(GroupMember.class);
        verify(groupMemberRepository).save(memberCaptor.capture());
        assertEquals(1L, memberCaptor.getValue().getGroupId());
        assertEquals(100L, memberCaptor.getValue().getUserId());
        assertEquals(GroupRole.OWNER, memberCaptor.getValue().getRole());
        assertEquals(0, memberCaptor.getValue().getPoints());
    }

    @Test
    @DisplayName("avatarFileId 为 null 时跳过 FileRef 校验")
    void create_shouldSkipFileRefWhenAvatarFileIdNull() {
        CreateStudyGroupRequest request = buildRequest("群组", null, null, null);

        StudyGroup mockSaved = StudyGroup.reconstruct(1L, "群组", null, null,
                100L, JoinPolicy.OPEN, InviteCode.of("ABC12345"),
                java.time.LocalDateTime.now(), java.time.LocalDateTime.now());
        when(studyGroupRepository.save(any())).thenReturn(mockSaved);

        StudyGroupResponse response = appService.create(request);

        assertNull(response.getAvatarFileId());
        assertNull(response.getAvatarUrl());
    }

    @Test
    @DisplayName("文件不存在时抛 FILE_NOT_FOUND")
    void create_shouldThrowWhenFileNotFound() {
        CreateStudyGroupRequest request = buildRequest("群组", null, 10L, null);
        when(fileServiceClient.getFileInfo(10L)).thenReturn(Result.success(null));

        BusinessException ex = assertThrows(BusinessException.class, () -> appService.create(request));
        assertEquals(ResultCode.FILE_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("bizType 不匹配时抛 FILE_BIZ_TYPE_MISMATCH")
    void create_shouldThrowWhenBizTypeMismatch() {
        CreateStudyGroupRequest request = buildRequest("群组", null, 10L, null);
        FileInfoResponse fileInfo = FileInfoResponse.builder()
                .fileId(10L)
                .url("https://example.com/avatar.png")
                .metadata(Map.of("bizType", "WRONG_TYPE", "userId", 100L))
                .build();
        when(fileServiceClient.getFileInfo(10L)).thenReturn(Result.success(fileInfo));

        BusinessException ex = assertThrows(BusinessException.class, () -> appService.create(request));
        assertEquals(ResultCode.FILE_BIZ_TYPE_MISMATCH.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("userId 不匹配时抛 FILE_OWNER_MISMATCH")
    void create_shouldThrowWhenOwnerMismatch() {
        CreateStudyGroupRequest request = buildRequest("群组", null, 10L, null);
        FileInfoResponse fileInfo = FileInfoResponse.builder()
                .fileId(10L)
                .url("https://example.com/avatar.png")
                .metadata(Map.of("bizType", "STUDY_GROUP_AVATAR", "userId", 999L))
                .build();
        when(fileServiceClient.getFileInfo(10L)).thenReturn(Result.success(fileInfo));

        BusinessException ex = assertThrows(BusinessException.class, () -> appService.create(request));
        assertEquals(ResultCode.FILE_OWNER_MISMATCH.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("result.isSuccess()=false 时抛 INTERNAL_ERROR")
    void create_shouldThrowInternalErrorWhenResultNotSuccess() {
        CreateStudyGroupRequest request = buildRequest("群组", null, 10L, null);
        when(fileServiceClient.getFileInfo(10L)).thenReturn(Result.fail(500, "服务内部错误"));

        BusinessException ex = assertThrows(BusinessException.class, () -> appService.create(request));
        assertEquals(ResultCode.INTERNAL_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("Feign 调用异常应透传")
    void create_shouldPropagateFeignException() {
        CreateStudyGroupRequest request = buildRequest("群组", null, 10L, null);
        when(fileServiceClient.getFileInfo(10L)).thenThrow(new RuntimeException("network error"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> appService.create(request));
        assertEquals("network error", ex.getMessage());
    }

    @Test
    @DisplayName("joinPolicy 为 null 时应默认为 OPEN")
    void create_withNullJoinPolicy_defaultsToOpen() {
        CreateStudyGroupRequest request = buildRequest("群组", null, null, null);

        StudyGroup mockSaved = StudyGroup.reconstruct(1L, "群组", null, null,
                100L, JoinPolicy.OPEN, InviteCode.of("ABC12345"),
                java.time.LocalDateTime.now(), java.time.LocalDateTime.now());
        when(studyGroupRepository.save(any())).thenReturn(mockSaved);

        StudyGroupResponse response = appService.create(request);

        assertEquals("OPEN", response.getJoinPolicy());
    }

    @Test
    @DisplayName("joinPolicy 为 INVITE_ONLY 时应透传")
    void create_withExplicitInviteOnly_passesThrough() {
        CreateStudyGroupRequest request = buildRequest("群组", null, null, JoinPolicy.INVITE_ONLY);

        StudyGroup mockSaved = StudyGroup.reconstruct(1L, "群组", null, null,
                100L, JoinPolicy.INVITE_ONLY, InviteCode.of("ABC12345"),
                java.time.LocalDateTime.now(), java.time.LocalDateTime.now());
        when(studyGroupRepository.save(any())).thenReturn(mockSaved);

        StudyGroupResponse response = appService.create(request);

        assertEquals("INVITE_ONLY", response.getJoinPolicy());
    }

    @Test
    @DisplayName("regenerateInviteCode: 群组不存在应抛 GROUP_NOT_FOUND")
    void regenerateInviteCode_groupNotFound_throwsGroupNotFound() {
        when(studyGroupRepository.findById(1L)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> appService.regenerateInviteCode(1L));
        assertEquals(ResultCode.GROUP_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("regenerateInviteCode: 非成员应抛 NOT_GROUP_MEMBER")
    void regenerateInviteCode_nonMember_throwsNotGroupMember() {
        StudyGroup group = StudyGroup.create("群组", null, null, 200L, JoinPolicy.OPEN);
        when(studyGroupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroupIdAndUserId(1L, 100L))
                .thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> appService.regenerateInviteCode(1L));
        assertEquals(ResultCode.NOT_GROUP_MEMBER.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("regenerateInviteCode: 非 OWNER 应抛 NOT_GROUP_OWNER")
    void regenerateInviteCode_nonOwner_throwsNotGroupOwner() {
        StudyGroup group = StudyGroup.create("群组", null, null, 200L, JoinPolicy.OPEN);
        when(studyGroupRepository.findById(1L)).thenReturn(Optional.of(group));
        GroupMember member = GroupMember.reconstruct(99L, 1L, 100L,
                GroupRole.MEMBER, 0, java.time.LocalDateTime.now());
        when(groupMemberRepository.findByGroupIdAndUserId(1L, 100L))
                .thenReturn(Optional.of(member));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> appService.regenerateInviteCode(1L));
        assertEquals(ResultCode.NOT_GROUP_OWNER.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("regenerateInviteCode: OWNER 操作成功应返回新邀请码（不同于原码）")
    void regenerateInviteCode_owner_successReturnsNewInviteCode() {
        // 使用真实 StudyGroup（不是 mock），让 regenerateInviteCode() 真实执行
        StudyGroup group = StudyGroup.create("群组", null, null, 200L, JoinPolicy.OPEN);
        String oldCode = group.getInviteCodeValue();
        when(studyGroupRepository.findById(1L)).thenReturn(Optional.of(group));
        GroupMember owner = GroupMember.reconstruct(99L, 1L, 100L,
                GroupRole.OWNER, 0, java.time.LocalDateTime.now());
        when(groupMemberRepository.findByGroupIdAndUserId(1L, 100L))
                .thenReturn(Optional.of(owner));
        // mock save 返回传入的 group（real 对象，inviteCode 已变更）
        when(studyGroupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StudyGroupResponse response = appService.regenerateInviteCode(1L);

        assertNotNull(response.getInviteCode());
        assertNotEquals(oldCode, response.getInviteCode());
    }

    @Test
    @DisplayName("regenerateInviteCode: 首次碰撞重试一次成功")
    void regenerateInviteCode_firstCollision_retriesAndSucceeds() {
        StudyGroup group = StudyGroup.create("群组", null, null, 200L, JoinPolicy.OPEN);
        String oldCode = group.getInviteCodeValue();
        when(studyGroupRepository.findById(1L)).thenReturn(Optional.of(group));
        GroupMember owner = GroupMember.reconstruct(99L, 1L, 100L,
                GroupRole.OWNER, 0, java.time.LocalDateTime.now());
        when(groupMemberRepository.findByGroupIdAndUserId(1L, 100L))
                .thenReturn(Optional.of(owner));
        // 第一次 save 抛 DIVI，第二次成功
        when(studyGroupRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("dup"))
                .thenAnswer(inv -> inv.getArgument(0));

        StudyGroupResponse response = appService.regenerateInviteCode(1L);

        verify(studyGroupRepository, times(2)).save(any());
        assertNotEquals(oldCode, response.getInviteCode());
    }

    @Test
    @DisplayName("regenerateInviteCode: 连续碰撞两次应抛 INVITE_CODE_GENERATION_FAILED")
    void regenerateInviteCode_persistentCollision_throwsGenerationFailed() {
        StudyGroup group = StudyGroup.create("群组", null, null, 200L, JoinPolicy.OPEN);
        when(studyGroupRepository.findById(1L)).thenReturn(Optional.of(group));
        GroupMember owner = GroupMember.reconstruct(99L, 1L, 100L,
                GroupRole.OWNER, 0, java.time.LocalDateTime.now());
        when(groupMemberRepository.findByGroupIdAndUserId(1L, 100L))
                .thenReturn(Optional.of(owner));
        when(studyGroupRepository.save(any())).thenThrow(new DataIntegrityViolationException("dup"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> appService.regenerateInviteCode(1L));
        assertEquals(ResultCode.INVITE_CODE_GENERATION_FAILED.getCode(), ex.getCode());
    }

    private CreateStudyGroupRequest buildRequest(String name, String description, Long avatarFileId,
                                                  JoinPolicy joinPolicy) {
        CreateStudyGroupRequest request = new CreateStudyGroupRequest();
        request.setName(name);
        request.setDescription(description);
        request.setAvatarFileId(avatarFileId);
        request.setJoinPolicy(joinPolicy);
        return request;
    }
}
