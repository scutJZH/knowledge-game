package com.knowledgegame.file.domain.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 上传凭证领域服务测试
 */
class UploadCredentialServiceTest {

    private UploadCredentialService service;

    @BeforeEach
    void setUp() {
        service = new UploadCredentialService(5);
    }

    @Nested
    @DisplayName("生成凭证")
    class GenerateTests {

        @Test
        @DisplayName("应生成非空 token")
        void shouldGenerateNonNullToken() {
            String token = service.generateCredential(1L, 1);
            assertNotNull(token);
            assertFalse(token.isEmpty());
        }
    }

    @Nested
    @DisplayName("验证凭证")
    class ValidateTests {

        @Test
        @DisplayName("有效凭证应通过验证")
        void shouldValidateValidCredential() {
            String token = service.generateCredential(1L, 1);
            assertTrue(service.validate(1L, token));
        }

        @Test
        @DisplayName("错误的 userId 应验证失败")
        void shouldRejectWrongUserId() {
            String token = service.generateCredential(1L, 1);
            assertFalse(service.validate(2L, token));
        }

        @Test
        @DisplayName("错误的 token 应验证失败")
        void shouldRejectWrongToken() {
            service.generateCredential(1L, 1);
            assertFalse(service.validate(1L, "wrong-token"));
        }

        @Test
        @DisplayName("过期凭证应验证失败")
        void shouldRejectExpiredCredential() {
            // 使用 -1 分钟过期，确保证券立即过期
            UploadCredentialService shortLived = new UploadCredentialService(-1);
            String token = shortLived.generateCredential(1L, 1);
            assertFalse(shortLived.validate(1L, token));
        }
    }

    @Nested
    @DisplayName("消费凭证")
    class ConsumeTests {

        @Test
        @DisplayName("tryConsume 消费后凭证应失效")
        void shouldInvalidateAfterTryConsume() {
            String token = service.generateCredential(1L, 1);
            assertTrue(service.tryConsume(1L, token));
            assertFalse(service.validate(1L, token));
        }

        @Test
        @DisplayName("tryConsume 重复消费返回 false")
        void shouldReturnFalseOnDoubleTryConsume() {
            String token = service.generateCredential(1L, 1);
            assertTrue(service.tryConsume(1L, token));
            assertFalse(service.tryConsume(1L, token));
        }
    }

    @Nested
    @DisplayName("清理过期凭证")
    class CleanupTests {

        @Test
        @DisplayName("应清理过期凭证")
        void shouldCleanExpiredCredentials() {
            UploadCredentialService shortLived = new UploadCredentialService(-1);
            shortLived.generateCredential(1L, 1);
            int cleaned = shortLived.cleanupExpired();
            assertTrue(cleaned > 0);
        }

        @Test
        @DisplayName("未过期凭证不应被清理")
        void shouldNotCleanValidCredentials() {
            service.generateCredential(1L, 1);
            int cleaned = service.cleanupExpired();
            assertTrue(cleaned == 0);
        }
    }
}
