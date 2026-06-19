package com.knowledgegame.app.application.service;

import com.knowledgegame.app.api.dto.CreateStudyGroupRequest;
import com.knowledgegame.app.api.dto.StudyGroupResponse;
import com.knowledgegame.components.feign.client.FileServiceClient;
import com.knowledgegame.components.feign.dto.FileInfoResponse;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.common.result.Result;
import com.knowledgegame.core.common.result.ResultCode;
import com.knowledgegame.core.domain.model.domainenum.GroupRole;
import com.knowledgegame.core.domain.model.entity.GroupMember;
import com.knowledgegame.core.domain.model.entity.StudyGroup;
import com.knowledgegame.core.domain.port.outbound.GroupMemberRepository;
import com.knowledgegame.core.domain.port.outbound.StudyGroupRepository;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
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
        CreateStudyGroupRequest request = buildRequest("测试群组", "描述", 10L);

        // mock FileServiceClient 返回有效 FileInfo
        FileInfoResponse fileInfo = FileInfoResponse.builder()
                .fileId(10L)
                .url("https://example.com/avatar.png")
                .metadata(Map.of("bizType", "STUDY_GROUP_AVATAR", "userId", 100L))
                .build();
        when(fileServiceClient.getFileInfo(10L)).thenReturn(Result.success(fileInfo));

        // mock 仓储 save
        StudyGroup mockSaved = StudyGroup.reconstruct(1L, "测试群组", "描述",
                com.knowledgegame.core.domain.model.vo.FileRef.of(10L, "https://example.com/avatar.png"),
                100L, java.time.LocalDateTime.now(), java.time.LocalDateTime.now());
        when(studyGroupRepository.save(any())).thenReturn(mockSaved);

        StudyGroupResponse response = appService.create(request);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("测试群组", response.getName());
        assertEquals(10L, response.getAvatarFileId());
        assertEquals("https://example.com/avatar.png", response.getAvatarUrl());

        // 捕获 StudyGroup save 参数
        ArgumentCaptor<StudyGroup> groupCaptor = ArgumentCaptor.forClass(StudyGroup.class);
        verify(studyGroupRepository).save(groupCaptor.capture());
        assertEquals("测试群组", groupCaptor.getValue().getName());
        assertEquals(100L, groupCaptor.getValue().getOwnerId());
        assertNotNull(groupCaptor.getValue().getAvatar());
        assertEquals("https://example.com/avatar.png", groupCaptor.getValue().getAvatar().url());

        // 捕获 GroupMember save 参数
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
        CreateStudyGroupRequest request = buildRequest("群组", null, null);

        StudyGroup mockSaved = StudyGroup.reconstruct(1L, "群组", null, null,
                100L, java.time.LocalDateTime.now(), java.time.LocalDateTime.now());
        when(studyGroupRepository.save(any())).thenReturn(mockSaved);

        StudyGroupResponse response = appService.create(request);

        assertNull(response.getAvatarFileId());
        assertNull(response.getAvatarUrl());
    }

    @Test
    @DisplayName("文件不存在时抛 FILE_NOT_FOUND")
    void create_shouldThrowWhenFileNotFound() {
        CreateStudyGroupRequest request = buildRequest("群组", null, 10L);
        when(fileServiceClient.getFileInfo(10L)).thenReturn(Result.success(null));

        BusinessException ex = assertThrows(BusinessException.class, () -> appService.create(request));
        assertEquals(ResultCode.FILE_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("bizType 不匹配时抛 FILE_BIZ_TYPE_MISMATCH")
    void create_shouldThrowWhenBizTypeMismatch() {
        CreateStudyGroupRequest request = buildRequest("群组", null, 10L);
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
        CreateStudyGroupRequest request = buildRequest("群组", null, 10L);
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
        CreateStudyGroupRequest request = buildRequest("群组", null, 10L);
        when(fileServiceClient.getFileInfo(10L)).thenReturn(Result.fail(500, "服务内部错误"));

        BusinessException ex = assertThrows(BusinessException.class, () -> appService.create(request));
        assertEquals(ResultCode.INTERNAL_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("Feign 调用异常应透传")
    void create_shouldPropagateFeignException() {
        CreateStudyGroupRequest request = buildRequest("群组", null, 10L);
        when(fileServiceClient.getFileInfo(10L)).thenThrow(new RuntimeException("network error"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> appService.create(request));
        assertEquals("network error", ex.getMessage());
    }

    private CreateStudyGroupRequest buildRequest(String name, String description, Long avatarFileId) {
        CreateStudyGroupRequest request = new CreateStudyGroupRequest();
        request.setName(name);
        request.setDescription(description);
        request.setAvatarFileId(avatarFileId);
        return request;
    }
}
