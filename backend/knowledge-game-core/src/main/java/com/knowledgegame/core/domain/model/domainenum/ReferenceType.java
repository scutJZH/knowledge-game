package com.knowledgegame.core.domain.model.domainenum;

/**
 * 积分变动来源类型。
 */
public enum ReferenceType {
    /** 盲盒抽卡 */
    GACHA,
    /** 直购卡牌 */
    PURCHASE,
    /** 卡牌分解 */
    DECOMPOSE,
    /** 游戏结算（秒判/Boss/串联） */
    GAME_REWARD,
    /** 翻牌奖励 */
    FLIP_REWARD,
    /** 群组签到 */
    CHECK_IN,
    /** 管理员手动调整（REQ-52） */
    ADMIN_ADJUST,
    /** 卡牌换积分（盲盒抽到不要的卡 / 卡包取出换分） */
    EXCHANGE_POINTS
}
