package com.knowledgegame.core.domain.model.entity;

import com.knowledgegame.core.domain.model.domainenum.JoinPolicy;
import com.knowledgegame.core.domain.model.vo.FileRef;
import com.knowledgegame.core.domain.model.vo.InviteCode;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 学习群组聚合根（无框架注解）
 */
@Getter
public class StudyGroup {

    private Long id;
    private String name;
    private String description;
    private FileRef avatar;
    private Long ownerId;
    private JoinPolicy joinPolicy;
    private InviteCode inviteCode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 创建新群组（工厂方法）
     */
    public static StudyGroup create(String name, String description,
                                    FileRef avatar, Long ownerId,
                                    JoinPolicy joinPolicy) {
        StudyGroup group = new StudyGroup();
        group.name = name;
        group.description = description;
        group.avatar = avatar;
        group.ownerId = ownerId;
        group.joinPolicy = joinPolicy;
        group.inviteCode = InviteCode.generate();
        group.createdAt = LocalDateTime.now();
        group.updatedAt = LocalDateTime.now();
        return group;
    }

    /**
     * 从持久化重建（用于 Repository 加载）
     */
    public static StudyGroup reconstruct(Long id, String name, String description,
                                         FileRef avatar, Long ownerId,
                                         JoinPolicy joinPolicy, InviteCode inviteCode,
                                         LocalDateTime createdAt, LocalDateTime updatedAt) {
        StudyGroup group = new StudyGroup();
        group.id = id;
        group.name = name;
        group.description = description;
        group.avatar = avatar;
        group.ownerId = ownerId;
        group.joinPolicy = joinPolicy;
        group.inviteCode = inviteCode;
        group.createdAt = createdAt;
        group.updatedAt = updatedAt;
        return group;
    }

    /**
     * 更新群主 ID（转让时调用）。
     */
    public void updateOwner(Long newOwnerId) {
        this.ownerId = newOwnerId;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 重新生成邀请码（ISSUE-4 实现）
     */
    public void regenerateInviteCode() {
        this.inviteCode = InviteCode.generate();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 判断是否为开放加入策略
     */
    public boolean isJoinPolicyOpen() {
        return this.joinPolicy == JoinPolicy.OPEN;
    }

    /**
     * 判断是否为仅邀请码加入策略
     */
    public boolean isJoinPolicyInviteOnly() {
        return this.joinPolicy == JoinPolicy.INVITE_ONLY;
    }

    /**
     * 获取邀请码字符串值
     */
    public String getInviteCodeValue() {
        return this.inviteCode != null ? this.inviteCode.getValue() : null;
    }
}
