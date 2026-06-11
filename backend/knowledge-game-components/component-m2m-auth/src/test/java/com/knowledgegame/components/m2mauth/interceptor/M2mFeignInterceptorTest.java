package com.knowledgegame.components.m2mauth.interceptor;

import com.knowledgegame.components.m2mauth.config.M2mAuthProperties;
import feign.RequestTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M2mFeignInterceptor 单元测试
 */
class M2mFeignInterceptorTest {

    private M2mAuthProperties properties;
    private M2mFeignInterceptor interceptor;

    @BeforeEach
    void setUp() {
        properties = new M2mAuthProperties();
        interceptor = new M2mFeignInterceptor(properties);
    }

    @Nested
    @DisplayName("正常注入")
    class NormalInjectionTests {

        @Test
        @DisplayName("配置完整时注入 X-Service-Name 和 X-Service-Key")
        void shouldInjectHeadersWhenConfigured() {
            properties.setServiceName("app");
            properties.setApiKey("app-secret-key");

            RequestTemplate template = new RequestTemplate();
            interceptor.apply(template);

            Collection<String> names = template.headers().get("X-Service-Name");
            Collection<String> keys = template.headers().get("X-Service-Key");

            assertThat(names).containsExactly("app");
            assertThat(keys).containsExactly("app-secret-key");
        }

        @Test
        @DisplayName("头值与配置完全一致")
        void shouldMatchExactConfigValues() {
            properties.setServiceName("admin-service");
            properties.setApiKey("complex-key-with-special-chars-!@#$");

            RequestTemplate template = new RequestTemplate();
            interceptor.apply(template);

            assertThat(template.headers().get("X-Service-Name")).containsExactly("admin-service");
            assertThat(template.headers().get("X-Service-Key")).containsExactly("complex-key-with-special-chars-!@#$");
        }
    }

    @Nested
    @DisplayName("空配置保护")
    class EmptyConfigTests {

        @Test
        @DisplayName("serviceName 为空时不注入任何头")
        void shouldNotInjectWhenServiceNameEmpty() {
            properties.setServiceName("");
            properties.setApiKey("app-secret-key");

            RequestTemplate template = new RequestTemplate();
            interceptor.apply(template);

            assertThat(template.headers()).isEmpty();
        }

        @Test
        @DisplayName("serviceName 为 null 时不注入任何头")
        void shouldNotInjectWhenServiceNameNull() {
            properties.setServiceName(null);
            properties.setApiKey("app-secret-key");

            RequestTemplate template = new RequestTemplate();
            interceptor.apply(template);

            assertThat(template.headers()).isEmpty();
        }

        @Test
        @DisplayName("apiKey 为空时不注入任何头")
        void shouldNotInjectWhenApiKeyEmpty() {
            properties.setServiceName("app");
            properties.setApiKey("");

            RequestTemplate template = new RequestTemplate();
            interceptor.apply(template);

            assertThat(template.headers()).isEmpty();
        }

        @Test
        @DisplayName("apiKey 为 null 时不注入任何头")
        void shouldNotInjectWhenApiKeyNull() {
            properties.setServiceName("app");
            properties.setApiKey(null);

            RequestTemplate template = new RequestTemplate();
            interceptor.apply(template);

            assertThat(template.headers()).isEmpty();
        }

        @Test
        @DisplayName("两者均为空时不注入任何头")
        void shouldNotInjectWhenBothEmpty() {
            properties.setServiceName(null);
            properties.setApiKey(null);

            RequestTemplate template = new RequestTemplate();
            interceptor.apply(template);

            assertThat(template.headers()).isEmpty();
        }
    }
}
