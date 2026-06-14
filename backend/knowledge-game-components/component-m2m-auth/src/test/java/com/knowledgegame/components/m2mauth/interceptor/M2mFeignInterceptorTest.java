package com.knowledgegame.components.m2mauth.interceptor;

import com.knowledgegame.components.m2mauth.config.M2mAuthAutoConfiguration;
import com.knowledgegame.components.m2mauth.config.M2mAuthProperties;
import feign.RequestTemplate;
import feign.Target;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * M2mFeignInterceptor Spring 上下文集成测试。
 * 验证在 Spring Boot 自动配置下拦截器的行为（多服务场景）。
 */
class M2mFeignInterceptorTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(M2mAuthAutoConfiguration.class);

    /**
     * 辅助方法：构建带 Target 的 RequestTemplate。
     */
    private RequestTemplate templateWithTarget(String targetName) {
        Target<?> target = mock(Target.class);
        when(target.name()).thenReturn(targetName);
        RequestTemplate template = new RequestTemplate();
        template.feignTarget(target);
        return template;
    }

    @Nested
    @DisplayName("Spring 上下文：多服务映射")
    class MultiServiceInSpring {

        @Test
        @DisplayName("配置两个 services 条目后，不同目标服务注入不同 apiKey")
        void shouldInjectCorrectKeysForDifferentTargets() {
            contextRunner
                    .withPropertyValues(
                            "m2m.auth.service-name=my-admin",
                            "m2m.auth.services.file-svc=file-key-abc",
                            "m2m.auth.services.audit-svc=audit-key-xyz"
                    )
                    .run(context -> {
                        M2mFeignInterceptor interceptor = context.getBean(M2mFeignInterceptor.class);

                        RequestTemplate tFile = templateWithTarget("file-svc");
                        RequestTemplate tAudit = templateWithTarget("audit-svc");

                        interceptor.apply(tFile);
                        interceptor.apply(tAudit);

                        assertThat(tFile.headers().get("X-Service-Name"))
                                .containsExactly("my-admin");
                        assertThat(tFile.headers().get("X-Service-Key"))
                                .containsExactly("file-key-abc");
                        assertThat(tAudit.headers().get("X-Service-Name"))
                                .containsExactly("my-admin");
                        assertThat(tAudit.headers().get("X-Service-Key"))
                                .containsExactly("audit-key-xyz");
                    });
        }

        @Test
        @DisplayName("Spring 上下文中未配置目标服务时抛异常")
        void shouldThrowWhenTargetNotConfiguredInServices() {
            contextRunner
                    .withPropertyValues(
                            "m2m.auth.service-name=my-admin",
                            "m2m.auth.services.known-svc=known-key"
                    )
                    .run(context -> {
                        M2mFeignInterceptor interceptor = context.getBean(M2mFeignInterceptor.class);
                        RequestTemplate template = templateWithTarget("unknown-svc");

                        assertThatThrownBy(() -> interceptor.apply(template))
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("unknown-svc");
                    });
        }
    }

    @Nested
    @DisplayName("Spring 上下文：属性绑定验证")
    class PropertyBinding {

        @Test
        @DisplayName("services 和其他字段通过 Spring 正确绑定到 M2mAuthProperties")
        void shouldBindAllFieldsCorrectly() {
            contextRunner
                    .withPropertyValues(
                            "m2m.auth.enabled=true",
                            "m2m.auth.protectedPaths[0]=/internal/**",
                            "m2m.auth.protectedPaths[1]=/callback/**",
                            "m2m.auth.service-name=admin-service",
                            "m2m.auth.apiKey=legacy-api-key",
                            "m2m.auth.services.user-svc=user-key",
                            "m2m.auth.services.file-svc=file-key"
                    )
                    .run(context -> {
                        M2mAuthProperties props = context.getBean(M2mAuthProperties.class);

                        assertThat(props.isEnabled()).isTrue();
                        assertThat(props.getProtectedPaths())
                                .containsExactly("/internal/**", "/callback/**");
                        assertThat(props.getServiceName()).isEqualTo("admin-service");
                        assertThat(props.getApiKey()).isEqualTo("legacy-api-key");
                        assertThat(props.getServices())
                                .containsEntry("user-svc", "user-key")
                                .containsEntry("file-svc", "file-key")
                                .hasSize(2);
                    });
        }

        @Test
        @DisplayName("Spring 上下文创建的拦截器可获取 properties 引用")
        void shouldAccessPropertiesFromInterceptor() {
            contextRunner
                    .withPropertyValues(
                            "m2m.auth.service-name=test-caller",
                            "m2m.auth.services.test-target=test-key"
                    )
                    .run(context -> {
                        M2mFeignInterceptor interceptor = context.getBean(M2mFeignInterceptor.class);
                        M2mAuthProperties props = context.getBean(M2mAuthProperties.class);

                        // 验证 properties 和 interceptor 都是有效的 Spring Bean
                        assertThat(interceptor).isNotNull();
                        assertThat(props.getServiceName()).isEqualTo("test-caller");
                        assertThat(props.getServices()).containsEntry("test-target", "test-key");
                    });
        }
    }

    @Nested
    @DisplayName("Spring 上下文：services 为空场景")
    class EmptyServicesInSpring {

        @Test
        @DisplayName("未配置 services 时默认为空 Map，拦截器调用 fail-fast")
        void shouldFailWithEmptyServicesByDefault() {
            contextRunner
                    .withPropertyValues("m2m.auth.service-name=caller")
                    .run(context -> {
                        M2mFeignInterceptor interceptor = context.getBean(M2mFeignInterceptor.class);
                        M2mAuthProperties props = context.getBean(M2mAuthProperties.class);

                        // 默认 services 应为空 Map
                        assertThat(props.getServices()).isNotNull().isEmpty();

                        RequestTemplate template = templateWithTarget("any-svc");

                        // 空 services 意味着任何目标都未配置 apiKey → 抛异常
                        assertThatThrownBy(() -> interceptor.apply(template))
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("未在 m2m.auth.services 中配置 apiKey");
                    });
        }
    }
}
