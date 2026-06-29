package com.knowledgegame.core.domain.model.entity;

import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.common.result.ResultCode;
import com.knowledgegame.core.domain.model.domainenum.GroupRole;
import com.knowledgegame.core.domain.model.domainenum.ReferenceType;
import com.knowledgegame.core.domain.model.domainenum.TxType;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 群组成员聚合根（无框架注解）
 */
@Getter
public class GroupMember {

    private Long id;
    private Long groupId;
    private Long userId;
    private GroupRole role;
    private int points;
    private LocalDateTime joinedAt;

    /**
     * 创建群主成员记录（工厂方法）
     */
    public static GroupMember createOwner(Long groupId, Long userId) {
        GroupMember member = new GroupMember();
        member.groupId = groupId;
        member.userId = userId;
        member.role = GroupRole.OWNER;
        member.points = 0;
        member.joinedAt = LocalDateTime.now();
        return member;
    }

    /**
     * 作为成员加入群组（工厂方法）
     */
    public static GroupMember joinAsMember(Long groupId, Long userId) {
        GroupMember member = new GroupMember();
        member.groupId = groupId;
        member.userId = userId;
        member.role = GroupRole.MEMBER;
        member.points = 0;
        member.joinedAt = LocalDateTime.now();
        return member;
    }

    /**
     * 从持久化重建（用于 Repository 加载）
     */
    public static GroupMember reconstruct(Long id, Long groupId, Long userId,
                                          GroupRole role, int points,
                                          LocalDateTime joinedAt) {
        GroupMember member = new GroupMember();
        member.id = id;
        member.groupId = groupId;
        member.userId = userId;
        member.role = role;
        member.points = points;
        member.joinedAt = joinedAt;
        return member;
    }

    /**
     * 提升为管理员。幂等。OWNER 调用抛 IllegalStateException。
     */
    public void promoteToAdmin() {
        if (this.role == GroupRole.OWNER) {
            throw new IllegalStateException("群主不能降级为管理员，请使用转让功能");
        }
        this.role = GroupRole.ADMIN;
    }

    /**
     * 降级为成员。幂等。OWNER 调用抛 IllegalStateException。
     */
    public void demoteToMember() {
        if (this.role == GroupRole.OWNER) {
            throw new IllegalStateException("群主不能降级为成员，请使用转让功能");
        }
        this.role = GroupRole.MEMBER;
    }

    /**
     * 转让群主。this 为原 OWNER，转让后变为 ADMIN。
     */
    public void transferOwnershipTo(GroupMember target) {
        if (this.role != GroupRole.OWNER) {
            throw new IllegalStateException("仅群主可以转让");
        }
        if (!this.groupId.equals(target.groupId)) {
            throw new IllegalStateException("只能转让给同群组成员");
        }
        this.role = GroupRole.ADMIN;
        target.role = GroupRole.OWNER;
    }

    /**
     * 增加积分（游戏/签到/分解/翻牌等场景调用）。
     * 返回待持久化的 PointTransaction 对象（由调用方在同一事务里 save）。
     * amount 必须为正数，否则抛 IllegalArgumentException
     */
    public PointTransaction earnPoints(int amount, ReferenceType refType, Long referenceId) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount 必须为正数");
        }
        this.points += amount;
        return PointTransaction.record(this.groupId, this.userId, TxType.EARN,
                amount, refType, referenceId, this.points);
    }

    /**
     * 扣减积分（抽卡/直购/兑换等场景调用）。
     * 余额不足抛 BusinessException(POINT_TRANSACTION_INSUFFICIENT_BALANCE)
     */
    public PointTransaction spendPoints(int amount, ReferenceType refType, Long referenceId) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount 必须为正数");
        }
        if (this.points < amount) {
            throw new BusinessException(ResultCode.POINT_TRANSACTION_INSUFFICIENT_BALANCE);
        }
        this.points -= amount;
        return PointTransaction.record(this.groupId, this.userId, TxType.SPEND,
                amount, refType, referenceId, this.points);
    }
}
