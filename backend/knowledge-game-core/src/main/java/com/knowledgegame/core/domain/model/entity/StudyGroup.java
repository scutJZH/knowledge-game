package com.knowledgegame.core.domain.model.entity;

import com.knowledgegame.core.domain.model.domainenum.JoinPolicy;
import com.knowledgegame.core.domain.model.domainenum.StudyGroupStatus;
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
    private StudyGroupStatus status;
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
        group.status = StudyGroupStatus.ACTIVE;
        group.createdAt = LocalDateTime.now();
        group.updatedAt = LocalDateTime.now();
        return group;
    }

    /**
     * 从持久化重建（用于 Repository 加载）
     */
    public static StudyGroup reconstruct(Long id, String name, String description,
                                         FileRef avatar, Long ownerId,
                                         StudyGroupStatus status,
                                         JoinPolicy joinPolicy, InviteCode inviteCode,
                                         LocalDateTime createdAt, LocalDateTime updatedAt) {
        StudyGroup group = new StudyGroup();
        group.id = id;
        group.name = name;
        group.description = description;
        group.avatar = avatar;
        group.ownerId = ownerId;
        group.status = status;
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

    public StudyGroupStatus getStatus() {
        return status;
    }

    /**
     * 更新群组信息（null 字段表示不更新）
     */
    public void updateInfo(String name, String description, FileRef avatar, JoinPolicy joinPolicy) {
        if (name != null) this.name = name;
        if (description != null) this.description = description;
        if (avatar != null) this.avatar = avatar;
        if (joinPolicy != null) this.joinPolicy = joinPolicy;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 激活群组
     */
    public void activate() {
        if (this.status == StudyGroupStatus.ACTIVE) {
            throw new IllegalStateException("群组已是启用状态");
        }
        this.status = StudyGroupStatus.ACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 停用群组
     */
    public void deactivate() {
        if (this.status == StudyGroupStatus.INACTIVE) {
            throw new IllegalStateException("群组已是停用状态");
        }
        this.status = StudyGroupStatus.INACTIVE;
        this.updatedAt = LocalDateTime.now();
    }
}
