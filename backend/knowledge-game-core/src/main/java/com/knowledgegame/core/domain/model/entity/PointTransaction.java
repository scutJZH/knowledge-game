package com.knowledgegame.core.domain.model.entity;

import com.knowledgegame.core.domain.model.domainenum.ReferenceType;
import com.knowledgegame.core.domain.model.domainenum.TxType;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 积分流水（只读历史记录，非聚合根边界）
 */
@Getter
public class PointTransaction {

    private Long id;
    private Long groupId;
    private Long userId;
    private TxType type;
    private int amount;
    private ReferenceType referenceType;
    private Long referenceId;
    private int balanceAfter;
    private LocalDateTime createdAt;

    /**
     * 工厂方法（仅由 GroupMember.earnPoints / spendPoints 调用）
     */
    public static PointTransaction record(Long groupId, Long userId, TxType type,
                                          int amount, ReferenceType referenceType,
                                          Long referenceId, int balanceAfter) {
        PointTransaction tx = new PointTransaction();
        tx.groupId = groupId;
        tx.userId = userId;
        tx.type = type;
        tx.amount = amount;
        tx.referenceType = referenceType;
        tx.referenceId = referenceId;
        tx.balanceAfter = balanceAfter;
        tx.createdAt = LocalDateTime.now();
        return tx;
    }

    /**
     * 从持久化重建（用于 Repository 加载）
     */
    public static PointTransaction reconstruct(Long id, Long groupId, Long userId,
                                                TxType type, int amount,
                                                ReferenceType referenceType, Long referenceId,
                                                int balanceAfter, LocalDateTime createdAt) {
        PointTransaction tx = new PointTransaction();
        tx.id = id;
        tx.groupId = groupId;
        tx.userId = userId;
        tx.type = type;
        tx.amount = amount;
        tx.referenceType = referenceType;
        tx.referenceId = referenceId;
        tx.balanceAfter = balanceAfter;
        tx.createdAt = createdAt;
        return tx;
    }
}
