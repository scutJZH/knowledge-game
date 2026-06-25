package com.knowledgegame.app.application.service;

import com.knowledgegame.app.api.dto.CreateStudyGroupRequest;
import com.knowledgegame.app.api.dto.GroupIpLibraryResponse;
import com.knowledgegame.app.api.dto.StudyGroupDetailResponse;
import com.knowledgegame.app.api.dto.StudyGroupListResponse;
import com.knowledgegame.app.api.dto.StudyGroupResponse;
import com.knowledgegame.app.api.dto.UpdateGroupIpLibraryRequest;
import com.knowledgegame.app.api.dto.UpdateStudyGroupRequest;
import com.knowledgegame.app.application.assembler.GroupIpLibraryAssembler;
import com.knowledgegame.app.application.assembler.StudyGroupAssembler;
import com.knowledgegame.auth.security.SecurityUtils;
import com.knowledgegame.components.feign.client.FileServiceClient;
import com.knowledgegame.components.feign.dto.FileInfoResponse;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.common.result.Result;
import com.knowledgegame.core.common.result.ResultCode;
import com.knowledgegame.core.domain.model.domainenum.GroupRole;
import com.knowledgegame.core.domain.model.domainenum.IpSeriesStatus;
import com.knowledgegame.core.domain.model.domainenum.JoinPolicy;
import com.knowledgegame.core.domain.model.entity.GroupIpLibrary;
import com.knowledgegame.core.domain.model.entity.GroupMember;
import com.knowledgegame.core.domain.model.entity.IpSeries;
import com.knowledgegame.core.domain.model.entity.StudyGroup;
import com.knowledgegame.core.domain.model.vo.FileRef;
import com.knowledgegame.core.domain.port.outbound.GroupIpLibraryRepository;
import com.knowledgegame.core.domain.port.outbound.GroupMemberRepository;
import com.knowledgegame.core.domain.port.outbound.IpSeriesRepositoryPort;
import com.knowledgegame.core.domain.port.outbound.StudyGroupRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private final GroupIpLibraryRepository groupIpLibraryRepository;
    private final IpSeriesRepositoryPort ipSeriesRepositoryPort;

    public StudyGroupAppService(StudyGroupRepository studyGroupRepository,
                                GroupMemberRepository groupMemberRepository,
                                FileServiceClient fileServiceClient,
                                GroupIpLibraryRepository groupIpLibraryRepository,
                                IpSeriesRepositoryPort ipSeriesRepositoryPort) {
        this.studyGroupRepository = studyGroupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.fileServiceClient = fileServiceClient;
        this.groupIpLibraryRepository = groupIpLibraryRepository;
        this.ipSeriesRepositoryPort = ipSeriesRepositoryPort;
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

    /**
     * 查询群组详情
     */
    @Transactional(readOnly = true)
    public StudyGroupDetailResponse getDetail(Long groupId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();

        StudyGroup group = studyGroupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(ResultCode.GROUP_NOT_FOUND));

        GroupMember member = groupMemberRepository.findByGroupIdAndUserId(groupId, currentUserId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_GROUP_MEMBER));

        GroupRole myRole = member.getRole();
        String inviteCode = null;
        if (myRole == GroupRole.OWNER || myRole == GroupRole.ADMIN) {
            inviteCode = group.getInviteCodeValue();
        }

        int memberCount = groupMemberRepository.countByGroupIdIn(List.of(groupId))
                .getOrDefault(groupId, 0);

        return StudyGroupAssembler.INSTANCE.toDetailResponse(
                group, myRole.name(), inviteCode, memberCount);
    }

    /**
     * 编辑群组信息（仅 OWNER）
     */
    @Transactional
    public StudyGroupResponse update(Long groupId, UpdateStudyGroupRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();

        StudyGroup group = studyGroupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(ResultCode.GROUP_NOT_FOUND));

        GroupMember member = groupMemberRepository.findByGroupIdAndUserId(groupId, currentUserId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_GROUP_MEMBER));

        if (member.getRole() != GroupRole.OWNER) {
            throw new BusinessException(ResultCode.NOT_GROUP_OWNER);
        }

        FileRef newAvatar = null;
        if (request.getAvatarFileId() != null) {
            newAvatar = resolveAvatar(request.getAvatarFileId(), currentUserId);
        }

        JoinPolicy joinPolicy = null;
        if (request.getJoinPolicy() != null) {
            joinPolicy = JoinPolicy.valueOf(request.getJoinPolicy());
        }

        group.updateInfo(request.getName(), request.getDescription(), newAvatar, joinPolicy);
        StudyGroup saved = studyGroupRepository.save(group);
        return StudyGroupAssembler.INSTANCE.toResponse(saved);
    }

    /**
     * 解散群组（仅 OWNER）
     */
    @Transactional
    public void disband(Long groupId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();

        StudyGroup group = studyGroupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(ResultCode.GROUP_NOT_FOUND));

        GroupMember member = groupMemberRepository.findByGroupIdAndUserId(groupId, currentUserId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_GROUP_MEMBER));

        if (member.getRole() != GroupRole.OWNER) {
            throw new BusinessException(ResultCode.NOT_GROUP_OWNER);
        }

        studyGroupRepository.deleteById(groupId);
    }

    /**
     * 查询群组已关联的 IP 列表（任意成员）
     */
    @Transactional(readOnly = true)
    public List<GroupIpLibraryResponse> listIpLibrary(Long groupId) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (!studyGroupRepository.existsById(groupId)) {
            throw new BusinessException(ResultCode.GROUP_NOT_FOUND);
        }
        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new BusinessException(ResultCode.NOT_GROUP_MEMBER);
        }
        List<GroupIpLibrary> items = groupIpLibraryRepository.findByGroupId(groupId);
        if (items.isEmpty()) return List.of();
        List<Long> ipIds = items.stream().map(GroupIpLibrary::getIpSeriesId).toList();
        Map<Long, IpSeries> ipMap = ipSeriesRepositoryPort.findAllByIdIn(ipIds).stream()
                .collect(Collectors.toMap(IpSeries::getId, Function.identity()));
        return items.stream()
                .map(item -> GroupIpLibraryAssembler.INSTANCE.toResponse(item, ipMap.get(item.getIpSeriesId())))
                .toList();
    }

    /**
     * 批量设置 IP 库关联（OWNER/ADMIN）
     */
    @Transactional
    public List<GroupIpLibraryResponse> updateIpLibrary(Long groupId, UpdateGroupIpLibraryRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();

        if (!studyGroupRepository.existsById(groupId)) {
            throw new BusinessException(ResultCode.GROUP_NOT_FOUND);
        }
        GroupMember member = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_GROUP_MEMBER));
        if (member.getRole() != GroupRole.OWNER && member.getRole() != GroupRole.ADMIN) {
            throw new BusinessException(ResultCode.NOT_GROUP_ADMIN);
        }

        List<Long> requestIds = request.getIpSeriesIds().stream().distinct().toList();

        if (!requestIds.isEmpty()) {
            List<IpSeries> ipList = ipSeriesRepositoryPort.findAllByIdIn(requestIds);
            if (ipList.size() != requestIds.size()) {
                throw new BusinessException(ResultCode.IP_SERIES_NOT_FOUND);
            }
            boolean hasInactive = ipList.stream().anyMatch(ip -> ip.getStatus() != IpSeriesStatus.ACTIVE);
            if (hasInactive) {
                throw new BusinessException(ResultCode.IP_SERIES_NOT_ACTIVE);
            }
        }

        List<GroupIpLibrary> existing = groupIpLibraryRepository.findByGroupId(groupId);
        Set<Long> currentIds = existing.stream().map(GroupIpLibrary::getIpSeriesId).collect(Collectors.toSet());
        Set<Long> requestIdSet = new HashSet<>(requestIds);

        List<Long> toRemove = currentIds.stream().filter(id -> !requestIdSet.contains(id)).toList();
        List<Long> toAdd = requestIds.stream().filter(id -> !currentIds.contains(id)).toList();

        if (!toRemove.isEmpty()) {
            groupIpLibraryRepository.deleteByGroupIdAndIpSeriesIdIn(groupId, toRemove);
        }
        if (!toAdd.isEmpty()) {
            List<GroupIpLibrary> newItems = toAdd.stream()
                    .map(ipId -> GroupIpLibrary.create(groupId, ipId))
                    .toList();
            groupIpLibraryRepository.saveAll(newItems);
        }

        List<GroupIpLibrary> updated = groupIpLibraryRepository.findByGroupId(groupId);
        if (updated.isEmpty()) return List.of();
        List<Long> ipIds = updated.stream().map(GroupIpLibrary::getIpSeriesId).toList();
        Map<Long, IpSeries> ipMap = ipSeriesRepositoryPort.findAllByIdIn(ipIds).stream()
                .collect(Collectors.toMap(IpSeries::getId, Function.identity()));
        return updated.stream()
                .map(item -> GroupIpLibraryAssembler.INSTANCE.toResponse(item, ipMap.get(item.getIpSeriesId())))
                .toList();
    }
}
