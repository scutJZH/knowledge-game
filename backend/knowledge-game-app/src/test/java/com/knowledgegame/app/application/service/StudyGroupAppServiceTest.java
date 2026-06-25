package com.knowledgegame.app.application.service;

import com.knowledgegame.app.api.dto.CreateStudyGroupRequest;
import com.knowledgegame.app.api.dto.GroupIpLibraryResponse;
import com.knowledgegame.app.api.dto.StudyGroupListResponse;
import com.knowledgegame.app.api.dto.StudyGroupResponse;
import com.knowledgegame.app.api.dto.UpdateGroupIpLibraryRequest;
import com.knowledgegame.components.feign.client.FileServiceClient;
import com.knowledgegame.components.feign.dto.FileInfoResponse;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.common.result.Result;
import com.knowledgegame.core.common.result.ResultCode;
import com.knowledgegame.core.domain.model.domainenum.GroupRole;
import com.knowledgegame.core.domain.model.domainenum.JoinPolicy;
import com.knowledgegame.core.domain.model.domainenum.StudyGroupStatus;
import com.knowledgegame.core.domain.model.entity.GroupMember;
import com.knowledgegame.core.domain.model.entity.StudyGroup;
import com.knowledgegame.core.domain.model.vo.FileRef;
import com.knowledgegame.core.domain.model.vo.InviteCode;
import com.knowledgegame.core.domain.model.domainenum.IpSeriesStatus;
import com.knowledgegame.core.domain.model.entity.IpSeries;
import com.knowledgegame.core.domain.port.outbound.GroupIpLibraryRepository;
import com.knowledgegame.core.domain.port.outbound.GroupMemberRepository;
import com.knowledgegame.core.domain.port.outbound.IpSeriesRepositoryPort;
import com.knowledgegame.core.domain.port.outbound.StudyGroupRepository;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Collections;
import java.util.List;
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

    @Mock
    private GroupIpLibraryRepository groupIpLibraryRepository;

    @Mock
    private IpSeriesRepositoryPort ipSeriesRepositoryPort;

    private StudyGroupAppService appService;

    private MockedStatic<com.knowledgegame.auth.security.SecurityUtils> securityUtilsMock;

    @BeforeEach
    void setUp() {
        appService = new StudyGroupAppService(studyGroupRepository, groupMemberRepository, fileServiceClient,
                groupIpLibraryRepository, ipSeriesRepositoryPort);
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
                100L, StudyGroupStatus.ACTIVE, JoinPolicy.OPEN, InviteCode.of("ABC12345"),
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
                100L, StudyGroupStatus.ACTIVE, JoinPolicy.OPEN, InviteCode.of("ABC12345"),
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
                100L, StudyGroupStatus.ACTIVE, JoinPolicy.OPEN, InviteCode.of("ABC12345"),
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
                100L, StudyGroupStatus.ACTIVE, JoinPolicy.INVITE_ONLY, InviteCode.of("ABC12345"),
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

    // ---- listMyGroups 测试 ----

    @Test
    @DisplayName("listMyGroups: 用户无任何群组时应返回空列表")
    void listMyGroups_noMembership_returnsEmptyList() {
        when(groupMemberRepository.findByUserIdOrderByJoinedAtDesc(100L))
                .thenReturn(List.of());

        List<StudyGroupListResponse> result = appService.listMyGroups();

        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    @DisplayName("listMyGroups: 有群组时应返回正确列表（含 myRole + memberCount）")
    void listMyGroups_withMemberships_returnsListWithRoleAndCount() {
        GroupMember member1 = GroupMember.reconstruct(1L, 10L, 100L,
                GroupRole.OWNER, 50, java.time.LocalDateTime.now().minusDays(1));
        GroupMember member2 = GroupMember.reconstruct(2L, 20L, 100L,
                GroupRole.MEMBER, 10, java.time.LocalDateTime.now());
        when(groupMemberRepository.findByUserIdOrderByJoinedAtDesc(100L))
                .thenReturn(List.of(member2, member1));

        StudyGroup group10 = StudyGroup.reconstruct(10L, "群组A", "描述A", null,
                100L, StudyGroupStatus.ACTIVE, JoinPolicy.OPEN, InviteCode.of("ABC12345"),
                java.time.LocalDateTime.now(), java.time.LocalDateTime.now());
        StudyGroup group20 = StudyGroup.reconstruct(20L, "群组B", null,
                FileRef.of(5L, "https://example.com/avatar.png"),
                200L, StudyGroupStatus.ACTIVE, JoinPolicy.INVITE_ONLY, InviteCode.of("DEF67890"),
                java.time.LocalDateTime.now(), java.time.LocalDateTime.now());
        when(studyGroupRepository.findByIdIn(List.of(20L, 10L)))
                .thenReturn(List.of(group10, group20));

        when(groupMemberRepository.countByGroupIdIn(List.of(20L, 10L)))
                .thenReturn(java.util.Map.of(10L, 12, 20L, 8));

        List<StudyGroupListResponse> result = appService.listMyGroups();

        assertNotNull(result);
        assertEquals(2, result.size());
        // 成员按 joinedAt DESC 排序：member2(group20) 在前
        assertEquals(20L, result.get(0).getId());
        assertEquals("MEMBER", result.get(0).getMyRole());
        assertEquals(8, result.get(0).getMemberCount());
        assertEquals("INVITE_ONLY", result.get(0).getJoinPolicy());
        assertEquals("https://example.com/avatar.png", result.get(0).getAvatarUrl());
        assertNotNull(result.get(0).getCreatedAt());
        assertNotNull(result.get(0).getUpdatedAt());

        assertEquals(10L, result.get(1).getId());
        assertEquals("OWNER", result.get(1).getMyRole());
        assertEquals(12, result.get(1).getMemberCount());
        assertEquals("OPEN", result.get(1).getJoinPolicy());
        assertNull(result.get(1).getAvatarUrl());
    }

    @Test
    @DisplayName("listMyGroups: 群组已被删除但成员记录残留时跳过")
    void listMyGroups_staleMembership_skipped() {
        GroupMember member = GroupMember.reconstruct(1L, 999L, 100L,
                GroupRole.MEMBER, 0, java.time.LocalDateTime.now());
        when(groupMemberRepository.findByUserIdOrderByJoinedAtDesc(100L))
                .thenReturn(List.of(member));
        // findByIdIn 不返回已删除的群组
        when(studyGroupRepository.findByIdIn(List.of(999L)))
                .thenReturn(List.of());
        when(groupMemberRepository.countByGroupIdIn(List.of(999L)))
                .thenReturn(java.util.Map.of());

        List<StudyGroupListResponse> result = appService.listMyGroups();

        assertNotNull(result);
        assertEquals(0, result.size());
    }

    // ---- listIpLibrary 测试 ----

    @Test
    @DisplayName("listIpLibrary: 群组不存在应抛 GROUP_NOT_FOUND")
    void listIpLibrary_groupNotFound_throwsGroupNotFound() {
        when(studyGroupRepository.existsById(1L)).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> appService.listIpLibrary(1L));
        assertEquals(ResultCode.GROUP_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("listIpLibrary: 非成员应抛 NOT_GROUP_MEMBER")
    void listIpLibrary_nonMember_throwsNotGroupMember() {
        when(studyGroupRepository.existsById(1L)).thenReturn(true);
        when(groupMemberRepository.existsByGroupIdAndUserId(1L, 100L)).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> appService.listIpLibrary(1L));
        assertEquals(ResultCode.NOT_GROUP_MEMBER.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("listIpLibrary: 空列表返回空")
    void listIpLibrary_empty_returnsEmptyList() {
        when(studyGroupRepository.existsById(1L)).thenReturn(true);
        when(groupMemberRepository.existsByGroupIdAndUserId(1L, 100L)).thenReturn(true);
        when(groupIpLibraryRepository.findByGroupId(1L)).thenReturn(List.of());

        List<GroupIpLibraryResponse> result = appService.listIpLibrary(1L);

        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    @DisplayName("listIpLibrary: 正常返回含 IP 名称")
    void listIpLibrary_normal_returnsWithIpNames() {
        when(studyGroupRepository.existsById(1L)).thenReturn(true);
        when(groupMemberRepository.existsByGroupIdAndUserId(1L, 100L)).thenReturn(true);

        com.knowledgegame.core.domain.model.entity.GroupIpLibrary item =
                com.knowledgegame.core.domain.model.entity.GroupIpLibrary.reconstruct(
                        1L, 1L, 10L, java.time.LocalDateTime.of(2025, 6, 1, 12, 0, 0));
        when(groupIpLibraryRepository.findByGroupId(1L)).thenReturn(List.of(item));

        IpSeries ip = IpSeries.reconstruct(10L, "PKM", "宝可梦", "desc", null,
                IpSeriesStatus.ACTIVE, java.time.LocalDateTime.now(), java.time.LocalDateTime.now());
        when(ipSeriesRepositoryPort.findAllByIdIn(List.of(10L))).thenReturn(List.of(ip));

        List<GroupIpLibraryResponse> result = appService.listIpLibrary(1L);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals(10L, result.get(0).getIpSeriesId());
        assertEquals("宝可梦", result.get(0).getIpSeriesName());
        assertEquals("PKM", result.get(0).getIpSeriesCode());
        assertNotNull(result.get(0).getAddedAt());
    }

    // ---- updateIpLibrary 测试 ----

    @Test
    @DisplayName("updateIpLibrary: 群组不存在应抛 GROUP_NOT_FOUND")
    void updateIpLibrary_groupNotFound_throwsGroupNotFound() {
        UpdateGroupIpLibraryRequest request = buildIpLibraryRequest(List.of(10L));
        when(studyGroupRepository.existsById(1L)).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> appService.updateIpLibrary(1L, request));
        assertEquals(ResultCode.GROUP_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("updateIpLibrary: 非成员应抛 NOT_GROUP_MEMBER")
    void updateIpLibrary_nonMember_throwsNotGroupMember() {
        UpdateGroupIpLibraryRequest request = buildIpLibraryRequest(List.of(10L));
        when(studyGroupRepository.existsById(1L)).thenReturn(true);
        when(groupMemberRepository.findByGroupIdAndUserId(1L, 100L)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> appService.updateIpLibrary(1L, request));
        assertEquals(ResultCode.NOT_GROUP_MEMBER.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("updateIpLibrary: MEMBER 应抛 NOT_GROUP_ADMIN")
    void updateIpLibrary_member_throwsNotGroupAdmin() {
        UpdateGroupIpLibraryRequest request = buildIpLibraryRequest(List.of(10L));
        when(studyGroupRepository.existsById(1L)).thenReturn(true);
        GroupMember member = GroupMember.reconstruct(1L, 1L, 100L,
                GroupRole.MEMBER, 0, java.time.LocalDateTime.now());
        when(groupMemberRepository.findByGroupIdAndUserId(1L, 100L)).thenReturn(Optional.of(member));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> appService.updateIpLibrary(1L, request));
        assertEquals(ResultCode.NOT_GROUP_ADMIN.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("updateIpLibrary: ADMIN 操作成功")
    void updateIpLibrary_admin_success() {
        UpdateGroupIpLibraryRequest request = buildIpLibraryRequest(List.of(10L));
        when(studyGroupRepository.existsById(1L)).thenReturn(true);
        GroupMember admin = GroupMember.reconstruct(1L, 1L, 100L,
                GroupRole.ADMIN, 0, java.time.LocalDateTime.now());
        when(groupMemberRepository.findByGroupIdAndUserId(1L, 100L)).thenReturn(Optional.of(admin));

        // 当前无关联 → 需新增
        when(groupIpLibraryRepository.findByGroupId(1L))
                .thenReturn(List.of())   // 第一次：diff 计算
                .thenReturn(List.of(     // 第二次：响应组装（saveAll 后）
                        com.knowledgegame.core.domain.model.entity.GroupIpLibrary.reconstruct(
                                1L, 1L, 10L, java.time.LocalDateTime.now())));

        IpSeries ip = IpSeries.reconstruct(10L, "PKM", "宝可梦", "desc", null,
                IpSeriesStatus.ACTIVE, java.time.LocalDateTime.now(), java.time.LocalDateTime.now());
        when(ipSeriesRepositoryPort.findAllByIdIn(List.of(10L)))
                .thenReturn(List.of(ip))   // IP 校验
                .thenReturn(List.of(ip));  // 响应填充

        when(groupIpLibraryRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        List<GroupIpLibraryResponse> result = appService.updateIpLibrary(1L, request);

        assertEquals(1, result.size());
        assertEquals(10L, result.get(0).getIpSeriesId());
        assertEquals("宝可梦", result.get(0).getIpSeriesName());

        verify(groupIpLibraryRepository).saveAll(any());
    }

    @Test
    @DisplayName("updateIpLibrary: IP 不存在应抛 IP_SERIES_NOT_FOUND")
    void updateIpLibrary_ipNotFound_throwsIpSeriesNotFound() {
        UpdateGroupIpLibraryRequest request = buildIpLibraryRequest(List.of(10L, 20L));
        when(studyGroupRepository.existsById(1L)).thenReturn(true);
        GroupMember owner = GroupMember.reconstruct(1L, 1L, 100L,
                GroupRole.OWNER, 0, java.time.LocalDateTime.now());
        when(groupMemberRepository.findByGroupIdAndUserId(1L, 100L)).thenReturn(Optional.of(owner));

        // 只返回 1 个，10 存在 20 不存在
        IpSeries ip10 = IpSeries.reconstruct(10L, "PKM", "P", null, null,
                IpSeriesStatus.ACTIVE, java.time.LocalDateTime.now(), java.time.LocalDateTime.now());
        when(ipSeriesRepositoryPort.findAllByIdIn(List.of(10L, 20L))).thenReturn(List.of(ip10));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> appService.updateIpLibrary(1L, request));
        assertEquals(ResultCode.IP_SERIES_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("updateIpLibrary: IP INACTIVE 应抛 IP_SERIES_NOT_ACTIVE")
    void updateIpLibrary_ipInactive_throwsIpSeriesNotActive() {
        UpdateGroupIpLibraryRequest request = buildIpLibraryRequest(List.of(10L));
        when(studyGroupRepository.existsById(1L)).thenReturn(true);
        GroupMember owner = GroupMember.reconstruct(1L, 1L, 100L,
                GroupRole.OWNER, 0, java.time.LocalDateTime.now());
        when(groupMemberRepository.findByGroupIdAndUserId(1L, 100L)).thenReturn(Optional.of(owner));

        IpSeries ip = IpSeries.reconstruct(10L, "PKM", "P", null, null,
                IpSeriesStatus.INACTIVE, java.time.LocalDateTime.now(), java.time.LocalDateTime.now());
        when(ipSeriesRepositoryPort.findAllByIdIn(List.of(10L))).thenReturn(List.of(ip));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> appService.updateIpLibrary(1L, request));
        assertEquals(ResultCode.IP_SERIES_NOT_ACTIVE.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("updateIpLibrary: 空数组清空全部关联")
    void updateIpLibrary_emptyArray_clearsAll() {
        UpdateGroupIpLibraryRequest request = buildIpLibraryRequest(List.of());
        when(studyGroupRepository.existsById(1L)).thenReturn(true);
        GroupMember owner = GroupMember.reconstruct(1L, 1L, 100L,
                GroupRole.OWNER, 0, java.time.LocalDateTime.now());
        when(groupMemberRepository.findByGroupIdAndUserId(1L, 100L)).thenReturn(Optional.of(owner));

        com.knowledgegame.core.domain.model.entity.GroupIpLibrary existing =
                com.knowledgegame.core.domain.model.entity.GroupIpLibrary.reconstruct(
                        1L, 1L, 10L, java.time.LocalDateTime.now());
        when(groupIpLibraryRepository.findByGroupId(1L))
                .thenReturn(List.of(existing))  // 当前有 1 条
                .thenReturn(List.of());          // 清空后空

        List<GroupIpLibraryResponse> result = appService.updateIpLibrary(1L, request);

        assertEquals(0, result.size());
        verify(groupIpLibraryRepository).deleteByGroupIdAndIpSeriesIdIn(1L, List.of(10L));
    }

    @Test
    @DisplayName("updateIpLibrary: 幂等 — requestIds 与 currentIds 相同时无操作")
    void updateIpLibrary_idempotent_noChange() {
        UpdateGroupIpLibraryRequest request = buildIpLibraryRequest(List.of(10L));
        when(studyGroupRepository.existsById(1L)).thenReturn(true);
        GroupMember owner = GroupMember.reconstruct(1L, 1L, 100L,
                GroupRole.OWNER, 0, java.time.LocalDateTime.now());
        when(groupMemberRepository.findByGroupIdAndUserId(1L, 100L)).thenReturn(Optional.of(owner));

        com.knowledgegame.core.domain.model.entity.GroupIpLibrary existing =
                com.knowledgegame.core.domain.model.entity.GroupIpLibrary.reconstruct(
                        1L, 1L, 10L, java.time.LocalDateTime.now());
        when(groupIpLibraryRepository.findByGroupId(1L)).thenReturn(List.of(existing));

        IpSeries ip = IpSeries.reconstruct(10L, "PKM", "P", null, null,
                IpSeriesStatus.ACTIVE, java.time.LocalDateTime.now(), java.time.LocalDateTime.now());
        when(ipSeriesRepositoryPort.findAllByIdIn(List.of(10L)))
                .thenReturn(List.of(ip));

        List<GroupIpLibraryResponse> result = appService.updateIpLibrary(1L, request);

        assertEquals(1, result.size());
        // 不应调用 saveAll（无新增）或 delete（无删除）
        verify(groupIpLibraryRepository, times(0)).saveAll(any());
    }

    private UpdateGroupIpLibraryRequest buildIpLibraryRequest(List<Long> ipSeriesIds) {
        UpdateGroupIpLibraryRequest request = new UpdateGroupIpLibraryRequest();
        request.setIpSeriesIds(ipSeriesIds);
        return request;
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
