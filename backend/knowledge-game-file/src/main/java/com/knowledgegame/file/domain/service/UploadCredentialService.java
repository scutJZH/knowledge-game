package com.knowledgegame.file.domain.service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 上传凭证领域服务（纯 POJO，零框架依赖）
 * <p>
 * 内存态凭证管理，单实例场景。多实例需迁移到 Redis。
 */
public class UploadCredentialService {

    private final int expireMinutes;

    private final Map<String, CredentialState> credentials = new ConcurrentHashMap<>();

    private final Map<String, Long> consumed = new ConcurrentHashMap<>();

    public UploadCredentialService(int expireMinutes) {
        this.expireMinutes = expireMinutes;
    }

    /**
     * 生成上传凭证
     */
    public String generateCredential(long userId, int count) {
        String token = UUID.randomUUID().toString();
        String key = buildKey(userId, token);
        long expireAt = Instant.now().plusSeconds(expireMinutes * 60L).getEpochSecond();
        credentials.put(key, new CredentialState(expireAt, count));
        return token;
    }

    /**
     * 验证凭证是否有效（未过期且仍有剩余次数）
     */
    public boolean validate(long userId, String token) {
        CredentialState state = getState(userId, token);
        return state != null && state.remainingCount.get() > 0;
    }

    /**
     * 获取凭证剩余次数（未过期且未消费完时返回剩余次数，否则返回 -1）
     */
    public int getRemainingCount(long userId, String token) {
        CredentialState state = getState(userId, token);
        if (state == null) {
            return -1;
        }
        return state.remainingCount.get();
    }

    /**
     * 原子化消费一次凭证：检查有效性 + 扣减次数，整体原子操作。
     * <p>
     * 解决并发场景下同一凭证被多个请求同时通过的竞态问题。
     *
     * @return true=消费成功（仍有剩余次数或刚好耗尽），false=凭证无效/已耗尽
     */
    public boolean tryConsume(long userId, String token) {
        String key = buildKey(userId, token);

        // 已在已消费集合中
        if (consumed.containsKey(key)) {
            return false;
        }

        CredentialState state = credentials.get(key);
        if (state == null) {
            return false;
        }

        // 已过期
        if (Instant.now().getEpochSecond() > state.expireAt) {
            credentials.remove(key);
            return false;
        }

        // 原子扣减
        int after = state.remainingCount.decrementAndGet();
        if (after < 0) {
            // 已被其他线程扣到 0 以下，回滚
            state.remainingCount.incrementAndGet();
            return false;
        }

        if (after == 0) {
            credentials.remove(key);
            consumed.put(key, Instant.now().getEpochSecond());
        }
        return true;
    }

    /**
     * 一次性消费全部剩余次数（批量上传成功后调用）
     */
    public void consumeAll(long userId, String token) {
        String key = buildKey(userId, token);
        credentials.remove(key);
        consumed.put(key, Instant.now().getEpochSecond());
    }

    /**
     * 清理过期的凭证和已消费记录
     */
    public int cleanupExpired() {
        long now = Instant.now().getEpochSecond();
        int cleaned = 0;

        var it = credentials.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().expireAt < now) {
                it.remove();
                cleaned++;
            }
        }

        long safetyThreshold = now - (long) expireMinutes * 60 * 2;
        var consumedIt = consumed.entrySet().iterator();
        while (consumedIt.hasNext()) {
            if (consumedIt.next().getValue() < safetyThreshold) {
                consumedIt.remove();
                cleaned++;
            }
        }

        return cleaned;
    }

    private CredentialState getState(long userId, String token) {
        String key = buildKey(userId, token);

        if (consumed.containsKey(key)) {
            return null;
        }

        CredentialState state = credentials.get(key);
        if (state == null) {
            return null;
        }

        if (Instant.now().getEpochSecond() > state.expireAt) {
            credentials.remove(key);
            return null;
        }

        return state;
    }

    private String buildKey(long userId, String token) {
        return userId + ":" + token;
    }

    private static class CredentialState {
        final long expireAt;
        final AtomicInteger remainingCount;

        CredentialState(long expireAt, int count) {
            this.expireAt = expireAt;
            this.remainingCount = new AtomicInteger(count);
        }
    }
}
