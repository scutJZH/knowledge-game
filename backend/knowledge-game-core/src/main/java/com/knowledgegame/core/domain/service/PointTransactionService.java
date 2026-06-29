package com.knowledgegame.core.domain.service;

import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.common.result.ResultCode;
import com.knowledgegame.core.domain.model.domainenum.ReferenceType;
import com.knowledgegame.core.domain.model.domainenum.TxType;
import com.knowledgegame.core.domain.model.entity.GroupMember;
import com.knowledgegame.core.domain.model.entity.PointTransaction;
import com.knowledgegame.core.domain.port.outbound.GroupMemberRepository;
import com.knowledgegame.core.domain.port.outbound.PointTransactionRepository;

/**
 * 积分流水领域服务（纯 POJO，无框架注解）。
 * 由 KnowledgeGameCoreAutoConfiguration 注册为 Bean。
 */
public class PointTransactionService {

    private final GroupMemberRepository memberRepo;
    private final PointTransactionRepository txRepo;

    public PointTransactionService(GroupMemberRepository memberRepo,
                                    PointTransactionRepository txRepo) {
        this.memberRepo = memberRepo;
        this.txRepo = txRepo;
    }

    /**
     * 统一积分变动写入入口。供 REQ-11/19/22/52/68 等所有写入场景调用。
     * <p>
     * 调用方必须在 @Transactional 方法内调用本方法，确保 memberRepo.save(member) 与
     * txRepo.save(tx) 在同一事务内。不在此加 @Transactional 是因为 domain 层零框架依赖，
     * 且事务边界由 application 层控制（遵循 DDD 分层约定）。
     */
    public PointTransaction record(Long groupId, Long userId, TxType type,
                                    int amount, ReferenceType refType, Long referenceId) {
        GroupMember member = memberRepo.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new BusinessException(ResultCode.POINT_TRANSACTION_USER_NOT_IN_GROUP));
        PointTransaction tx = (type == TxType.EARN)
                ? member.earnPoints(amount, refType, referenceId)
                : member.spendPoints(amount, refType, referenceId);
        memberRepo.save(member);
        return txRepo.save(tx);
    }
}
