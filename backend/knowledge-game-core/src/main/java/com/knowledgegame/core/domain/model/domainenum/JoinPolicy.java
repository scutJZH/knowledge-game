package com.knowledgegame.core.domain.model.domainenum;

/**
 * 群组加入策略枚举
 */
public enum JoinPolicy {

    /**
     * 开放加入（任何人可直接加入）
     */
    OPEN,

    /**
     * 仅凭邀请码加入
     */
    INVITE_ONLY
}
