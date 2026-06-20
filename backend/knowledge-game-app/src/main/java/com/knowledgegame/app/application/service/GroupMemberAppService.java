package com.knowledgegame.app.application.service;

import com.knowledgegame.app.api.dto.GroupMemberResponse;
import com.knowledgegame.app.application.assembler.GroupMemberAssembler;
import com.knowledgegame.auth.security.SecurityUtils;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.common.result.ResultCode;
import com.knowledgegame.core.domain.model.domainenum.GroupRole;
import com.knowledgegame.core.domain.model.entity.GroupMember;
import com.knowledgegame.core.domain.model.entity.StudyGroup;
import com.knowledgegame.core.domain.model.vo.InviteCode;
import com.knowledgegame.core.domain.port.outbound.GroupMemberRepository;
import com.knowledgegame.core.domain.port.outbound.StudyGroupRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 群组成员应用服务
 */
@Service
public class GroupMemberAppService {

    private final StudyGroupRepository studyGroupRepository;
    private final GroupMemberRepository groupMemberRepository;

    public GroupMemberAppService(StudyGroupRepository studyGroupRepository,
                                 GroupMemberRepository groupMemberRepository) {
        this.studyGroupRepository = studyGroupRepository;
        this.groupMemberRepository = groupMemberRepository;
    }

    /**
     * 直接加入群组（仅 OPEN 策略）
     */
    @Transactional
    public GroupMemberResponse joinDirectly(Long groupId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();

        StudyGroup group = studyGroupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(ResultCode.GROUP_NOT_FOUND));

        if (!group.isJoinPolicyOpen()) {
            throw new BusinessException(ResultCode.GROUP_JOIN_POLICY_MISMATCH);
        }

        if (groupMemberRepository.existsByGroupIdAndUserId(groupId, currentUserId)) {
            throw new BusinessException(ResultCode.ALREADY_GROUP_MEMBER);
        }

        GroupMember member = GroupMember.joinAsMember(groupId, currentUserId);
        try {
            GroupMember saved = groupMemberRepository.save(member);
            return GroupMemberAssembler.INSTANCE.toResponse(saved);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ResultCode.ALREADY_GROUP_MEMBER);
        }
    }

    /**
     * 凭邀请码加入群组（任何 joinPolicy 均可用）
     */
    @Transactional
    public GroupMemberResponse joinByInvite(String inviteCodeInput) {
        Long currentUserId = SecurityUtils.getCurrentUserId();

        InviteCode inviteCode;
        try {
            inviteCode = InviteCode.of(inviteCodeInput);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.INVITE_CODE_INVALID);
        }

        StudyGroup group = studyGroupRepository.findByInviteCode(inviteCode.getValue())
                .orElseThrow(() -> new BusinessException(ResultCode.INVITE_CODE_INVALID));

        if (groupMemberRepository.existsByGroupIdAndUserId(group.getId(), currentUserId)) {
            throw new BusinessException(ResultCode.ALREADY_GROUP_MEMBER);
        }

        GroupMember member = GroupMember.joinAsMember(group.getId(), currentUserId);
        try {
            GroupMember saved = groupMemberRepository.save(member);
            return GroupMemberAssembler.INSTANCE.toResponse(saved);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ResultCode.ALREADY_GROUP_MEMBER);
        }
    }

    /**
     * 退出群组
     */
    @Transactional
    public void leave(Long groupId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        GroupMember member = groupMemberRepository.findByGroupIdAndUserId(groupId, currentUserId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_GROUP_MEMBER));

        if (member.getRole() == GroupRole.OWNER) {
            throw new BusinessException(ResultCode.OWNER_CANNOT_LEAVE);
        }

        groupMemberRepository.deleteByGroupIdAndUserId(groupId, currentUserId);
    }

    /**
     * 查询当前用户在指定群组的成员身份
     */
    @Transactional(readOnly = true)
    public GroupMemberResponse getCurrentMember(Long groupId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();

        studyGroupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(ResultCode.GROUP_NOT_FOUND));

        GroupMember member = groupMemberRepository.findByGroupIdAndUserId(groupId, currentUserId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_GROUP_MEMBER));

        return GroupMemberAssembler.INSTANCE.toResponse(member);
    }

    /**
     * 更新成员角色（仅 OWNER，ADMIN / MEMBER 互转）
     */
    @Transactional
    public void updateRole(Long groupId, Long targetUserId, String role) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        enforceOwner(groupId, currentUserId);

        GroupMember target = groupMemberRepository.findById(targetUserId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_GROUP_MEMBER));

        if (!target.getGroupId().equals(groupId)) {
            throw new BusinessException(ResultCode.NOT_GROUP_MEMBER);
        }

        if (target.getRole() == GroupRole.OWNER) {
            throw new BusinessException(ResultCode.CANNOT_CHANGE_OWNER_ROLE);
        }

        if ("ADMIN".equals(role)) {
            target.promoteToAdmin();
        } else if ("MEMBER".equals(role)) {
            target.demoteToMember();
        } else {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }
        groupMemberRepository.save(target);
    }

    /**
     * 转让群主（仅 OWNER，转让后原 OWNER 变为 ADMIN）
     */
    @Transactional
    public void transferOwnership(Long groupId, Long toUserId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        GroupMember owner = enforceOwner(groupId, currentUserId);

        GroupMember target = groupMemberRepository.findById(toUserId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_GROUP_MEMBER));

        if (!target.getGroupId().equals(groupId)) {
            throw new BusinessException(ResultCode.NOT_GROUP_MEMBER);
        }

        StudyGroup group = studyGroupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(ResultCode.GROUP_NOT_FOUND));
        group.updateOwner(toUserId);
        studyGroupRepository.save(group);

        owner.transferOwnershipTo(target);
        groupMemberRepository.save(owner);
        groupMemberRepository.save(target);
    }

    private GroupMember enforceOwner(Long groupId, Long currentUserId) {
        GroupMember current = groupMemberRepository.findByGroupIdAndUserId(groupId, currentUserId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_GROUP_MEMBER));
        if (current.getRole() != GroupRole.OWNER) {
            throw new BusinessException(ResultCode.NOT_GROUP_OWNER);
        }
        return current;
    }
}
