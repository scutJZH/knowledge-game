package com.knowledgegame.app.application.service;

import com.knowledgegame.app.api.dto.CreateStudyGroupRequest;
import com.knowledgegame.app.api.dto.StudyGroupListResponse;
import com.knowledgegame.app.api.dto.StudyGroupResponse;
import com.knowledgegame.app.application.assembler.StudyGroupAssembler;
import com.knowledgegame.auth.security.SecurityUtils;
import com.knowledgegame.components.feign.client.FileServiceClient;
import com.knowledgegame.components.feign.dto.FileInfoResponse;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.common.result.Result;
import com.knowledgegame.core.common.result.ResultCode;
import com.knowledgegame.core.domain.model.domainenum.JoinPolicy;
import com.knowledgegame.core.domain.model.domainenum.GroupRole;
import com.knowledgegame.core.domain.model.entity.GroupMember;
import com.knowledgegame.core.domain.model.entity.StudyGroup;
import com.knowledgegame.core.domain.model.vo.FileRef;
import com.knowledgegame.core.domain.port.outbound.GroupMemberRepository;
import com.knowledgegame.core.domain.port.outbound.StudyGroupRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

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
        JoinPolicy joinPolicy = request.getJoinPolicy() != null ? request.getJoinPolicy() : JoinPolicy.OPEN;
        StudyGroup group = StudyGroup.create(request.getName(), request.getDescription(), avatar, ownerId, joinPolicy);
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

    /**
     * 重新生成邀请码（仅群主可操作）
     */
    @Transactional
    public StudyGroupResponse regenerateInviteCode(Long groupId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();

        StudyGroup group = studyGroupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(ResultCode.GROUP_NOT_FOUND));

        GroupMember member = groupMemberRepository.findByGroupIdAndUserId(groupId, currentUserId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_GROUP_MEMBER));

        if (member.getRole() != GroupRole.OWNER) {
            throw new BusinessException(ResultCode.NOT_GROUP_OWNER);
        }

        group.regenerateInviteCode();
        try {
            StudyGroup saved = studyGroupRepository.save(group);
            return StudyGroupAssembler.INSTANCE.toResponse(saved);
        } catch (DataIntegrityViolationException e) {
            // update 场景下唯一可能是 invite_code 唯一约束冲突（其他字段未变）
            group.regenerateInviteCode();
            try {
                StudyGroup saved = studyGroupRepository.save(group);
                return StudyGroupAssembler.INSTANCE.toResponse(saved);
            } catch (DataIntegrityViolationException ex) {
                throw new BusinessException(ResultCode.INVITE_CODE_GENERATION_FAILED);
            }
        }
    }

    /**
     * 查询当前用户已加入的群组列表
     */
    @Transactional(readOnly = true)
    public List<StudyGroupListResponse> listMyGroups() {
        Long userId = SecurityUtils.getCurrentUserId();
        List<GroupMember> members = groupMemberRepository.findByUserIdOrderByJoinedAtDesc(userId);
        if (members.isEmpty()) {
            return List.of();
        }
        List<Long> groupIds = members.stream().map(GroupMember::getGroupId).toList();
        Map<Long, StudyGroup> groupMap = studyGroupRepository.findByIdIn(groupIds).stream()
                .collect(Collectors.toMap(StudyGroup::getId, Function.identity()));
        Map<Long, Integer> memberCounts = groupMemberRepository.countByGroupIdIn(groupIds);
        List<StudyGroupListResponse> result = new ArrayList<>();
        for (GroupMember member : members) {
            StudyGroup group = groupMap.get(member.getGroupId());
            if (group == null) {
                continue;  // 群组已被硬删除但成员记录残留，跳过
            }
            int memberCount = memberCounts.getOrDefault(group.getId(), 0);
            result.add(StudyGroupAssembler.INSTANCE.toListResponse(
                    group, member.getRole().name(), memberCount));
        }
        return result;
    }
}
