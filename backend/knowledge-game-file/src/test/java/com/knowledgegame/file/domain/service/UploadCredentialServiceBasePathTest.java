package com.knowledgegame.file.domain.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UploadCredentialService basePath 绑定专项测试
 * <p>
 * 验证凭证生成时记录 basePath、校验时能取出 basePath、不同 basePath 的凭证互不影响。
 */
class UploadCredentialServiceBasePathTest {

    private UploadCredentialService service;

    @BeforeEach
    void setUp() {
        service = new UploadCredentialService(5);
    }

    @Nested
    @DisplayName("凭证生成时记录 basePath")
    class GenerateWithBasePathTests {

        @Test
        @DisplayName("生成凭证后应能取出 basePath")
        void shouldRetrieveBasePath_afterGeneration() {
            String token = service.generateCredential(1L, 1, "ip-series");

            String basePath = service.getBasePath(1L, token);
            assertEquals("ip-series", basePath);
        }

        @Test
        @DisplayName("生成凭证后应验证通过")
        void shouldValidate_afterGeneration() {
            String token = service.generateCredential(1L, 1, "avatar");

            assertTrue(service.validate(1L, token));
        }

        @Test
        @DisplayName("不同 basePath 的凭证均能正确记录")
        void shouldRecordDifferentBasePaths() {
            String token1 = service.generateCredential(1L, 1, "ip-series");
            String token2 = service.generateCredential(1L, 1, "avatar");
            String token3 = service.generateCredential(1L, 1, "card-star-image");

            assertEquals("ip-series", service.getBasePath(1L, token1));
            assertEquals("avatar", service.getBasePath(1L, token2));
            assertEquals("card-star-image", service.getBasePath(1L, token3));
        }
    }

    @Nested
    @DisplayName("凭证校验时取出 basePath")
    class RetrieveBasePathTests {

        @Test
        @DisplayName("无效 token 返回 null basePath")
        void shouldReturnNull_forInvalidToken() {
            assertNull(service.getBasePath(1L, "non-existent-token"));
        }

        @Test
        @DisplayName("错误的 userId 返回 null basePath")
        void shouldReturnNull_forWrongUserId() {
            String token = service.generateCredential(1L, 1, "ip-series");

            assertNull(service.getBasePath(2L, token));
        }

        @Test
        @DisplayName("已消费的凭证返回 null basePath")
        void shouldReturnNull_forConsumedCredential() {
            String token = service.generateCredential(1L, 1, "ip-series");

            service.tryConsume(1L, token);

            assertNull(service.getBasePath(1L, token));
        }

        @Test
        @DisplayName("过期凭证返回 null basePath")
        void shouldReturnNull_forExpiredCredential() {
            // 使用 -1 分钟过期，确保凭证立即过期
            UploadCredentialService shortLived = new UploadCredentialService(-1);
            String token = shortLived.generateCredential(1L, 1, "ip-series");

            assertNull(shortLived.getBasePath(1L, token));
        }
    }

    @Nested
    @DisplayName("不同 basePath 的凭证互不影响")
    class IsolationTests {

        @Test
        @DisplayName("消费一个凭证不影响另一个凭证的 basePath")
        void shouldNotAffectOtherCredential_whenOneConsumed() {
            String token1 = service.generateCredential(1L, 1, "ip-series");
            String token2 = service.generateCredential(1L, 1, "avatar");

            // 消费第一个凭证
            service.tryConsume(1L, token1);

            // 第二个凭证仍有效且 basePath 正确
            assertEquals("avatar", service.getBasePath(1L, token2));
            assertTrue(service.validate(1L, token2));
        }

        @Test
        @DisplayName("不同用户的同名 basePath 凭证互不影响")
        void shouldIsolateAcrossUsers() {
            String token1 = service.generateCredential(1L, 1, "ip-series");
            String token2 = service.generateCredential(2L, 1, "ip-series");

            // 消费用户1的凭证
            service.tryConsume(1L, token1);

            // 用户2的凭证仍有效且 basePath 正确
            assertEquals("ip-series", service.getBasePath(2L, token2));
            assertTrue(service.validate(2L, token2));
        }

        @Test
        @DisplayName("不同 basePath 的凭证各自独立消费")
        void shouldConsumeIndependently() {
            String token1 = service.generateCredential(1L, 1, "ip-series");
            String token2 = service.generateCredential(1L, 1, "avatar");

            // 分别消费
            assertTrue(service.tryConsume(1L, token1));
            assertTrue(service.tryConsume(1L, token2));

            // 两个凭证都已失效
            assertNull(service.getBasePath(1L, token1));
            assertNull(service.getBasePath(1L, token2));
        }

        @Test
        @DisplayName("多次生成不同 basePath 的凭证全部可用")
        void shouldGenerateMultipleCredentials_withDifferentBasePaths() {
            String basePath1 = "ip-series";
            String basePath2 = "avatar";
            String basePath3 = "card-star-image";

            String token1 = service.generateCredential(1L, 1, basePath1);
            String token2 = service.generateCredential(1L, 1, basePath2);
            String token3 = service.generateCredential(1L, 1, basePath3);

            // 所有凭证均可验证
            assertNotNull(token1);
            assertNotNull(token2);
            assertNotNull(token3);

            // 所有 basePath 均可正确取出
            assertEquals(basePath1, service.getBasePath(1L, token1));
            assertEquals(basePath2, service.getBasePath(1L, token2));
            assertEquals(basePath3, service.getBasePath(1L, token3));
        }
    }
}
