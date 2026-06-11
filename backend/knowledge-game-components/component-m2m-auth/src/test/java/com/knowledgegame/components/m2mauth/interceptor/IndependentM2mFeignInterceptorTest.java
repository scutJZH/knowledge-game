package com.knowledgegame.components.m2mauth.interceptor;

import com.knowledgegame.components.m2mauth.config.M2mAuthProperties;
import feign.RequestTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M2mFeignInterceptor 独立单元测试
 * 以独立测试工程师视角，全面覆盖 PRD 验收标准中的所有行为规格
 */
@ExtendWith(MockitoExtension.class)
class IndependentM2mFeignInterceptorTest {

    private M2mAuthProperties properties;

    private M2mFeignInterceptor interceptor;

    @BeforeEach
    void setUp() {
        properties = new M2mAuthProperties();
        interceptor = new M2mFeignInterceptor(properties);
    }

    // ==================== 正常注入 ====================

    @Nested
    @DisplayName("规格1-2：配置完整时注入 X-Service-Name 和 X-Service-Key")
    class NormalInjectionTests {

        @Test
        @DisplayName("serviceName 和 apiKey 都有值时注入两个请求头")
        void shouldInjectBothHeadersWhenFullyConfigured() {
            properties.setServiceName("my-app");
            properties.setApiKey("my-secret-key");

            RequestTemplate template = new RequestTemplate();
            interceptor.apply(template);

            assertThat(template.headers().get("X-Service-Name")).containsExactly("my-app");
            assertThat(template.headers().get("X-Service-Key")).containsExactly("my-secret-key");
        }

        @Test
        @DisplayName("注入的请求头名称精确为 X-Service-Name 和 X-Service-Key")
        void shouldUseExactHeaderNames() {
            properties.setServiceName("svc");
            properties.setApiKey("key");

            RequestTemplate template = new RequestTemplate();
            interceptor.apply(template);

            // 确认头名称存在（区分大小写）
            assertThat(template.headers()).containsKey("X-Service-Name");
            assertThat(template.headers()).containsKey("X-Service-Key");
        }

        @Test
        @DisplayName("多次调用 apply 每次都注入头（幂等行为）")
        void shouldInjectHeadersOnEveryApply() {
            properties.setServiceName("svc");
            properties.setApiKey("key");

            RequestTemplate template1 = new RequestTemplate();
            RequestTemplate template2 = new RequestTemplate();
            interceptor.apply(template1);
            interceptor.apply(template2);

            assertThat(template1.headers().get("X-Service-Name")).containsExactly("svc");
            assertThat(template2.headers().get("X-Service-Name")).containsExactly("svc");
        }

        @Test
        @DisplayName("注入值与配置值完全一致，无额外处理")
        void shouldInjectExactConfigValues() {
            String serviceName = "order-service-v2";
            String apiKey = "sk-prod-abc123xyz!@#";
            properties.setServiceName(serviceName);
            properties.setApiKey(apiKey);

            RequestTemplate template = new RequestTemplate();
            interceptor.apply(template);

            Collection<String> names = template.headers().get("X-Service-Name");
            Collection<String> keys = template.headers().get("X-Service-Key");
            assertThat(names).hasSize(1);
            assertThat(names.iterator().next()).isEqualTo(serviceName);
            assertThat(keys).hasSize(1);
            assertThat(keys.iterator().next()).isEqualTo(apiKey);
        }
    }

    // ==================== 空配置保护 ====================

    @Nested
    @DisplayName("规格3：任一为空/null 时跳过注入")
    class EmptyConfigProtectionTests {

        @Test
        @DisplayName("serviceName 为 null 时跳过注入")
        void shouldSkipWhenServiceNameNull() {
            properties.setServiceName(null);
            properties.setApiKey("some-key");

            RequestTemplate template = new RequestTemplate();
            interceptor.apply(template);

            assertThat(template.headers()).isEmpty();
        }

        @Test
        @DisplayName("serviceName 为空字符串时跳过注入")
        void shouldSkipWhenServiceNameEmpty() {
            properties.setServiceName("");
            properties.setApiKey("some-key");

            RequestTemplate template = new RequestTemplate();
            interceptor.apply(template);

            assertThat(template.headers()).isEmpty();
        }

        @Test
        @DisplayName("serviceName 为纯空格时跳过注入")
        void shouldSkipWhenServiceNameBlank() {
            properties.setServiceName("   ");
            properties.setApiKey("some-key");

            RequestTemplate template = new RequestTemplate();
            interceptor.apply(template);

            assertThat(template.headers()).isEmpty();
        }

        @Test
        @DisplayName("apiKey 为 null 时跳过注入")
        void shouldSkipWhenApiKeyNull() {
            properties.setServiceName("my-service");
            properties.setApiKey(null);

            RequestTemplate template = new RequestTemplate();
            interceptor.apply(template);

            assertThat(template.headers()).isEmpty();
        }

        @Test
        @DisplayName("apiKey 为空字符串时跳过注入")
        void shouldSkipWhenApiKeyEmpty() {
            properties.setServiceName("my-service");
            properties.setApiKey("");

            RequestTemplate template = new RequestTemplate();
            interceptor.apply(template);

            assertThat(template.headers()).isEmpty();
        }

        @Test
        @DisplayName("apiKey 为纯空格时跳过注入")
        void shouldSkipWhenApiKeyBlank() {
            properties.setServiceName("my-service");
            properties.setApiKey("   ");

            RequestTemplate template = new RequestTemplate();
            interceptor.apply(template);

            assertThat(template.headers()).isEmpty();
        }

        @Test
        @DisplayName("两者均为 null 时跳过注入")
        void shouldSkipWhenBothNull() {
            properties.setServiceName(null);
            properties.setApiKey(null);

            RequestTemplate template = new RequestTemplate();
            interceptor.apply(template);

            assertThat(template.headers()).isEmpty();
        }

        @Test
        @DisplayName("两者均为空字符串时跳过注入")
        void shouldSkipWhenBothEmpty() {
            properties.setServiceName("");
            properties.setApiKey("");

            RequestTemplate template = new RequestTemplate();
            interceptor.apply(template);

            assertThat(template.headers()).isEmpty();
        }

        /**
         * 参数化测试：覆盖所有空/null 组合
         */
        @ParameterizedTest(name = "serviceName={0}, apiKey={1} → 不注入头")
        @DisplayName("各种空值组合均不注入头")
        @CsvSource({
                "null, some-key",
                "'', some-key",
                "'   ', some-key",
                "my-service, null",
                "my-service, ''",
                "my-service, '   '",
                "null, null",
                "'', ''",
                "null, ''",
                "'', null"
        })
        void shouldSkipInjectionForVariousEmptyCombinations(String serviceName, String apiKey) {
            // CSVSource 会把 "null" 字符串传进来，需要转换为真正的 null
            properties.setServiceName("null".equals(serviceName) ? null : serviceName);
            properties.setApiKey("null".equals(apiKey) ? null : apiKey);

            RequestTemplate template = new RequestTemplate();
            interceptor.apply(template);

            assertThat(template.headers()).isEmpty();
        }
    }

    // ==================== 配置动态变更 ====================

    @Nested
    @DisplayName("配置动态变更")
    class DynamicConfigChangeTests {

        @Test
        @DisplayName("修改 properties 后 apply 反映最新值")
        void shouldReflectLatestConfigAfterChange() {
            // 第一次：未配置，不注入
            properties.setServiceName(null);
            properties.setApiKey(null);

            RequestTemplate template1 = new RequestTemplate();
            interceptor.apply(template1);
            assertThat(template1.headers()).isEmpty();

            // 修改配置
            properties.setServiceName("new-service");
            properties.setApiKey("new-key");

            RequestTemplate template2 = new RequestTemplate();
            interceptor.apply(template2);
            assertThat(template2.headers().get("X-Service-Name")).containsExactly("new-service");
            assertThat(template2.headers().get("X-Service-Key")).containsExactly("new-key");
        }

        @Test
        @DisplayName("从有值改为空值后不再注入")
        void shouldStopInjectionAfterConfigCleared() {
            // 先配置有效值
            properties.setServiceName("svc");
            properties.setApiKey("key");

            RequestTemplate template1 = new RequestTemplate();
            interceptor.apply(template1);
            assertThat(template1.headers()).hasSize(2);

            // 清除 apiKey
            properties.setApiKey(null);

            RequestTemplate template2 = new RequestTemplate();
            interceptor.apply(template2);
            assertThat(template2.headers()).isEmpty();
        }
    }

    // ==================== 不修改已有请求头 ====================

    @Nested
    @DisplayName("不覆盖已有请求头")
    class ExistingHeadersTests {

        @Test
        @DisplayName("apply 不影响 RequestTemplate 中已存在的其他头")
        void shouldNotRemoveExistingHeaders() {
            properties.setServiceName("svc");
            properties.setApiKey("key");

            RequestTemplate template = new RequestTemplate();
            template.header("Content-Type", "application/json");
            template.header("Authorization", "Bearer token");

            interceptor.apply(template);

            // 验证原有头仍然存在
            assertThat(template.headers().get("Content-Type")).containsExactly("application/json");
            assertThat(template.headers().get("Authorization")).containsExactly("Bearer token");
            // 验证新注入的头也存在
            assertThat(template.headers().get("X-Service-Name")).containsExactly("svc");
            assertThat(template.headers().get("X-Service-Key")).containsExactly("key");
        }
    }
}
