package com.knowledgegame.auth.security;

import java.time.Instant;

/**
 * Token 黑名单接口（支持登出时使 Token 主动失效）
 */
public interface TokenBlacklist {

    /**
     * 将 Token 加入黑名单
     *
     * @param jti      Token 唯一标识
     * @param expireAt Token 过期时间，用于惰性清理
     */
    void blacklist(String jti, Instant expireAt);

    /**
     * 检查 Token 是否在黑名单中
     *
     * @param jti Token 唯一标识
     * @return true 表示已被拉黑
     */
    boolean isBlacklisted(String jti);
}
