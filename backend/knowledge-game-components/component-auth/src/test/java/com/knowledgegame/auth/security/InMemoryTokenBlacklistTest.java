package com.knowledgegame.auth.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * InMemoryTokenBlacklist 单元测试
 */
class InMemoryTokenBlacklistTest {

    private InMemoryTokenBlacklist blacklist;

    @BeforeEach
    void setUp() {
        blacklist = new InMemoryTokenBlacklist();
    }

    @Nested
    @DisplayName("blacklist + isBlacklisted")
    class BlacklistAndCheck {

        @Test
        @DisplayName("加入黑名单后 isBlacklisted 返回 true")
        void blacklisted_token_returnsTrue() {
            Instant expireAt = Instant.now().plusSeconds(3600);

            blacklist.blacklist("jti-123", expireAt);

            assertThat(blacklist.isBlacklisted("jti-123")).isTrue();
        }

        @Test
        @DisplayName("未加入黑名单的 jti 返回 false")
        void not_blacklisted_token_returnsFalse() {
            assertThat(blacklist.isBlacklisted("jti-not-exist")).isFalse();
        }

        @Test
        @DisplayName("不同 jti 互不影响")
        void different_jti_independent() {
            Instant expireAt = Instant.now().plusSeconds(3600);

            blacklist.blacklist("jti-a", expireAt);

            assertThat(blacklist.isBlacklisted("jti-a")).isTrue();
            assertThat(blacklist.isBlacklisted("jti-b")).isFalse();
        }
    }

    @Nested
    @DisplayName("惰性清理")
    class LazyCleanup {

        @Test
        @DisplayName("已过期的黑名单条目在查询时被清理，返回 false")
        void expired_entry_cleaned_on_check() {
            Instant pastExpireAt = Instant.now().minusSeconds(1);

            blacklist.blacklist("jti-expired", pastExpireAt);

            // 过期条目应被惰性清理，返回 false
            assertThat(blacklist.isBlacklisted("jti-expired")).isFalse();
        }

        @Test
        @DisplayName("未过期的条目不受惰性清理影响")
        void not_expired_entry_kept() {
            Instant futureExpireAt = Instant.now().plusSeconds(3600);

            blacklist.blacklist("jti-valid", futureExpireAt);

            assertThat(blacklist.isBlacklisted("jti-valid")).isTrue();
        }
    }

    @Nested
    @DisplayName("边界条件")
    class EdgeCases {

        @Test
        @DisplayName("null jti 返回 false（旧 Token 兼容）")
        void null_jti_returnsFalse() {
            assertThat(blacklist.isBlacklisted(null)).isFalse();
        }

        @Test
        @DisplayName("空字符串 jti 返回 false")
        void empty_jti_returnsFalse() {
            assertThat(blacklist.isBlacklisted("")).isFalse();
        }

        @Test
        @DisplayName("大量条目性能可接受")
        void large_number_of_entries() {
            Instant expireAt = Instant.now().plusSeconds(3600);

            for (int i = 0; i < 10000; i++) {
                blacklist.blacklist("jti-" + i, expireAt);
            }

            assertThat(blacklist.isBlacklisted("jti-9999")).isTrue();
            assertThat(blacklist.isBlacklisted("jti-10000")).isFalse();
        }
    }
}
