package com.knowledgegame.auth.security;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存 Token 黑名单实现（惰性清理过期条目）
 */
public class InMemoryTokenBlacklist implements TokenBlacklist {

    /** jti → 过期时间戳 */
    private final ConcurrentHashMap<String, Instant> blacklist = new ConcurrentHashMap<>();

    @Override
    public void blacklist(String jti, Instant expireAt) {
        if (jti == null || jti.isEmpty()) {
            return;
        }
        blacklist.put(jti, expireAt);
    }

    @Override
    public boolean isBlacklisted(String jti) {
        if (jti == null || jti.isEmpty()) {
            return false;
        }
        Instant expireAt = blacklist.get(jti);
        if (expireAt == null) {
            return false;
        }
        // 惰性清理：已过期则移除
        if (expireAt.isBefore(Instant.now())) {
            blacklist.remove(jti);
            return false;
        }
        return true;
    }
}
