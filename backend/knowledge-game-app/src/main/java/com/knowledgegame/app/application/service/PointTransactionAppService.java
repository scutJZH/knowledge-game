package com.knowledgegame.app.application.service;

import com.knowledgegame.app.api.assembler.PointTransactionAssembler;
import com.knowledgegame.app.api.dto.response.BalanceResponse;
import com.knowledgegame.app.api.dto.response.PointTransactionCrossGroupResponse;
import com.knowledgegame.app.api.dto.response.PointTransactionPageResponse;
import com.knowledgegame.app.api.dto.response.PointTransactionResponse;
import com.knowledgegame.app.application.command.PointTransactionQuery;
import com.knowledgegame.auth.security.SecurityUtils;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.common.result.ResultCode;
import com.knowledgegame.core.domain.model.domainenum.GroupRole;
import com.knowledgegame.core.domain.model.entity.GroupMember;
import com.knowledgegame.core.domain.model.entity.PointTransaction;
import com.knowledgegame.core.domain.model.entity.StudyGroup;
import com.knowledgegame.core.domain.model.entity.User;
import com.knowledgegame.core.domain.model.vo.PageResult;
import com.knowledgegame.core.domain.port.outbound.GroupMemberRepository;
import com.knowledgegame.core.domain.port.outbound.PointTransactionRepository;
import com.knowledgegame.core.domain.port.outbound.StudyGroupRepository;
import com.knowledgegame.core.domain.port.outbound.UserRepositoryPort;
import com.knowledgegame.core.domain.service.PointTransactionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 积分流水应用服务
 */
@Service
public class PointTransactionAppService {

    private final PointTransactionService txService;
    private final PointTransactionRepository txRepo;
    private final GroupMemberRepository memberRepo;
    private final UserRepositoryPort userRepo;
    private final StudyGroupRepository groupRepo;

    public PointTransactionAppService(PointTransactionService txService,
                                       PointTransactionRepository txRepo,
                                       GroupMemberRepository memberRepo,
                                       UserRepositoryPort userRepo,
                                       StudyGroupRepository groupRepo) {
        this.txService = txService;
        this.txRepo = txRepo;
        this.memberRepo = memberRepo;
        this.userRepo = userRepo;
        this.groupRepo = groupRepo;
    }

    /** 群组视角查询 — 权限驱动可见性 */
    @Transactional(readOnly = true)
    public PointTransactionPageResponse<PointTransactionResponse> listByGroup(
            Long groupId, PointTransactionQuery query) {
        Long callerUserId = SecurityUtils.getCurrentUserId();
        GroupMember caller = memberRepo.findByGroupIdAndUserId(groupId, callerUserId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_GROUP_MEMBER));

        Long effectiveUserId = switch (caller.getRole()) {
            case MEMBER -> callerUserId;
            case ADMIN, OWNER -> query.userId();
        };

        PageResult<PointTransaction> page = txRepo.findByGroup(
                groupId, effectiveUserId, query.type(), query.referenceType(),
                query.startDate(), query.endDate(), query.sortField(),
                query.page() - 1, query.size());

        List<Long> userIds = page.getContent().stream()
                .map(PointTransaction::getUserId).distinct().toList();
        Map<Long, User> userMap = userRepo.findByIdIn(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<PointTransactionResponse> content = page.getContent().stream()
                .map(tx -> PointTransactionAssembler.INSTANCE.toResponse(tx, userMap))
                .toList();

        return new PointTransactionPageResponse<>(content,
                page.getTotalElements(), page.getTotalPages());
    }

    /** 个人视角查询（跨群组） */
    @Transactional(readOnly = true)
    public PointTransactionPageResponse<PointTransactionCrossGroupResponse> listByUser(
            PointTransactionQuery query) {
        Long callerUserId = SecurityUtils.getCurrentUserId();

        PageResult<PointTransaction> page = txRepo.findByUser(
                callerUserId, query.groupId(), query.type(), query.referenceType(),
                query.startDate(), query.endDate(), query.sortField(),
                query.page() - 1, query.size());

        List<Long> userIds = page.getContent().stream()
                .map(PointTransaction::getUserId).distinct().toList();
        List<Long> groupIds = page.getContent().stream()
                .map(PointTransaction::getGroupId).distinct().toList();
        Map<Long, User> userMap = userRepo.findByIdIn(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, StudyGroup> groupMap = groupRepo.findByIdIn(groupIds).stream()
                .collect(Collectors.toMap(StudyGroup::getId, Function.identity()));

        List<PointTransactionCrossGroupResponse> content = page.getContent().stream()
                .map(tx -> PointTransactionAssembler.INSTANCE.toResponse(tx, userMap, groupMap))
                .toList();

        return new PointTransactionPageResponse<>(content,
                page.getTotalElements(), page.getTotalPages());
    }

    /** 当前余额（轻量读，不查流水表） */
    @Transactional(readOnly = true)
    public BalanceResponse getBalance(Long groupId) {
        Long userId = SecurityUtils.getCurrentUserId();
        GroupMember m = memberRepo.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_GROUP_MEMBER));
        return new BalanceResponse(groupId, userId, m.getPoints());
    }
}
