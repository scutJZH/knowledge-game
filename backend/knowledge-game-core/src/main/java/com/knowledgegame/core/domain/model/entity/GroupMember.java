package com.knowledgegame.core.domain.model.entity;

import com.knowledgegame.core.domain.model.domainenum.GroupRole;
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
}
