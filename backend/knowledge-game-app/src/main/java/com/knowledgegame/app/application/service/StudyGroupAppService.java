package com.knowledgegame.app.application.service;

import com.knowledgegame.app.api.dto.CreateStudyGroupRequest;
import com.knowledgegame.app.api.dto.StudyGroupResponse;
import com.knowledgegame.app.application.assembler.StudyGroupAssembler;
import com.knowledgegame.auth.security.SecurityUtils;
import com.knowledgegame.components.feign.client.FileServiceClient;
import com.knowledgegame.components.feign.dto.FileInfoResponse;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.common.result.Result;
import com.knowledgegame.core.common.result.ResultCode;
import com.knowledgegame.core.domain.model.entity.GroupMember;
import com.knowledgegame.core.domain.model.entity.StudyGroup;
import com.knowledgegame.core.domain.model.vo.FileRef;
import com.knowledgegame.core.domain.port.outbound.GroupMemberRepository;
import com.knowledgegame.core.domain.port.outbound.StudyGroupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;

/**
 * 学习群组应用服务
 */
@Service
public class StudyGroupAppService {

    private final StudyGroupRepository studyGroupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final FileServiceClient fileServiceClient;

    public StudyGroupAppService(StudyGroupRepository studyGroupRepository,
                                GroupMemberRepository groupMemberRepository,
                                FileServiceClient fileServiceClient) {
        this.studyGroupRepository = studyGroupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.fileServiceClient = fileServiceClient;
    }

    @Transactional
    public StudyGroupResponse create(CreateStudyGroupRequest request) {
        Long ownerId = SecurityUtils.getCurrentUserId();
        FileRef avatar = resolveAvatar(request.getAvatarFileId(), ownerId);
        StudyGroup group = StudyGroup.create(request.getName(), request.getDescription(), avatar, ownerId);
        StudyGroup saved = studyGroupRepository.save(group);
        GroupMember owner = GroupMember.createOwner(saved.getId(), ownerId);
        groupMemberRepository.save(owner);
        return StudyGroupAssembler.INSTANCE.toResponse(saved);
    }

    private FileRef resolveAvatar(Long fileId, Long ownerId) {
        if (fileId == null) return null;
        Result<FileInfoResponse> result = fileServiceClient.getFileInfo(fileId);
        FileInfoResponse info = result.getData();
        if (info == null) {
            if (result.getCode() != ResultCode.SUCCESS.getCode()) {
                throw new BusinessException(ResultCode.INTERNAL_ERROR);
            }
            throw new BusinessException(ResultCode.FILE_NOT_FOUND);
        }
        Map<String, Object> metadata = info.getMetadata();
        if (metadata == null || !"STUDY_GROUP_AVATAR".equals(metadata.get("bizType")))
            throw new BusinessException(ResultCode.FILE_BIZ_TYPE_MISMATCH);
        Object metaUserId = metadata.get("userId");
        Long metaUserIdLong = metaUserId instanceof Number ? ((Number) metaUserId).longValue() : null;
        if (!Objects.equals(ownerId, metaUserIdLong))
            throw new BusinessException(ResultCode.FILE_OWNER_MISMATCH);
        return FileRef.of(fileId, info.getUrl());
    }
}
