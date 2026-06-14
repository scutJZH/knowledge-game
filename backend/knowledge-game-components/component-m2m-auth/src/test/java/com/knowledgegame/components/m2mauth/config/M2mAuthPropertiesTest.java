package com.knowledgegame.components.m2mauth.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M2mAuthProperties 配置绑定测试。
 * 覆盖场景 8-10：多服务绑定、空 services 默认值、新旧字段共存。
 */
class M2mAuthPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(EnableConfig.class);

    @EnableConfigurationProperties(M2mAuthProperties.class)
    static class EnableConfig {
    }

    // ==================== 场景 8：多服务绑定 ====================

    @Nested
    @DisplayName("场景8：多服务绑定 - yml 配多个 services 条目")
    class MultiServiceBinding {

        @Test
        @DisplayName("单目标服务绑定到 services Map")
        void shouldBindSingleService() {
            contextRunner
                    .withPropertyValues("m2m.auth.services.knowledge-game-file=abc")
                    .run(context -> {
                        M2mAuthProperties props = context.getBean(M2mAuthProperties.class);
                        assertThat(props.getServices()).isNotNull();
                        assertThat(props.getServices()).containsKey("knowledge-game-file");
                        assertThat(props.getServices().get("knowledge-game-file")).isEqualTo("abc");
                    });
        }

        @Test
        @DisplayName("多目标服务绑定到 services Map")
        void shouldBindMultipleServices() {
            contextRunner
                    .withPropertyValues(
                            "m2m.auth.services.file-svc=file-key-123",
                            "m2m.auth.services.audit-svc=audit-key-456",
                            "m2m.auth.services.notify-svc=notify-key-789"
                    )
                    .run(context -> {
                        M2mAuthProperties props = context.getBean(M2mAuthProperties.class);
                        assertThat(props.getServices())
                                .isNotNull()
                                .hasSize(3)
                                .containsEntry("file-svc", "file-key-123")
                                .containsEntry("audit-svc", "audit-key-456")
                                .containsEntry("notify-svc", "notify-key-789");
                    });
        }

        @Test
        @DisplayName("多目标服务中 key 包含点号和横线等特殊字符")
        void shouldBindServicesWithSpecialCharacters() {
            contextRunner
                    .withPropertyValues(
                            "m2m.auth.services.knowledge-game-file=file-api-key",
                            "m2m.auth.services.knowledge-game-admin=admin-api-key"
                    )
                    .run(context -> {
                        M2mAuthProperties props = context.getBean(M2mAuthProperties.class);
                        assertThat(props.getServices())
                                .hasSize(2)
                                .containsEntry("knowledge-game-file", "file-api-key")
                                .containsEntry("knowledge-game-admin", "admin-api-key");
                    });
        }

        @Test
        @DisplayName("services 中 value 包含特殊字符时正确绑定")
        void shouldBindServiceValueWithSpecialChars() {
            contextRunner
                    .withPropertyValues(
                            "m2m.auth.services.svc-a=key-with-dashes-AND_underscores",
                            "m2m.auth.services.svc-b=!@#$%^&*()"
                    )
                    .run(context -> {
                        M2mAuthProperties props = context.getBean(M2mAuthProperties.class);
                        assertThat(props.getServices())
                                .containsEntry("svc-a", "key-with-dashes-AND_underscores")
                                .containsEntry("svc-b", "!@#$%^&*()");
                    });
        }
    }

    // ==================== 场景 9：空 services 默认值 ====================

    @Nested
    @DisplayName("场景9：空 services 默认值 - 不配 services 时为非 null 空 Map")
    class EmptyServicesDefault {

        @Test
        @DisplayName("不配置 services 时 getServices() 返回非 null 的空 Map")
        void shouldDefaultToEmptyMap() {
            contextRunner.run(context -> {
                M2mAuthProperties props = context.getBean(M2mAuthProperties.class);
                assertThat(props.getServices()).isNotNull();
                assertThat(props.getServices()).isEmpty();
            });
        }

        @Test
        @DisplayName("空 services 不是 null，是空 HashMap 实例")
        void shouldReturnEmptyHashMapNotNulL() {
            contextRunner.run(context -> {
                M2mAuthProperties props = context.getBean(M2mAuthProperties.class);
                assertThat(props.getServices()).isNotNull();
                // 调用 Map 方法不应抛 NPE
                assertThat(props.getServices().get("any-key")).isNull();
                assertThat(props.getServices().size()).isEqualTo(0);
                assertThat(props.getServices().containsKey("any-key")).isFalse();
            });
        }
    }

    // ==================== 场景 10：新旧字段共存 ====================

    @Nested
    @DisplayName("场景10：新旧字段共存 - enabled/protectedPaths/serviceName/apiKey 与 services 同时存在")
    class OldFieldsCoexistence {

        @Test
        @DisplayName("所有新旧字段同时配置时各自正确绑定")
        void shouldBindAllOldAndNewFieldsTogether() {
            contextRunner
                    .withPropertyValues(
                            "m2m.auth.enabled=true",
                            "m2m.auth.protectedPaths[0]=/internal/**",
                            "m2m.auth.protectedPaths[1]=/callback/**",
                            "m2m.auth.service-name=knowledge-game-admin",
                            "m2m.auth.apiKey=shared-api-key",
                            "m2m.auth.services.file-svc=file-key",
                            "m2m.auth.services.audit-svc=audit-key"
                    )
                    .run(context -> {
                        M2mAuthProperties props = context.getBean(M2mAuthProperties.class);

                        assertThat(props.isEnabled()).isTrue();
                        assertThat(props.getProtectedPaths())
                                .containsExactly("/internal/**", "/callback/**");
                        assertThat(props.getServiceName())
                                .isEqualTo("knowledge-game-admin");
                        assertThat(props.getApiKey())
                                .isEqualTo("shared-api-key");
                        assertThat(props.getServices())
                                .hasSize(2)
                                .containsEntry("file-svc", "file-key")
                                .containsEntry("audit-svc", "audit-key");
                    });
        }

        @Test
        @DisplayName("仅配置旧字段（不配 services）时旧字段正确绑定")
        void shouldBindOldFieldsWithoutServices() {
            contextRunner
                    .withPropertyValues(
                            "m2m.auth.enabled=true",
                            "m2m.auth.protectedPaths[0]=/api/internal/**",
                            "m2m.auth.service-name=my-service",
                            "m2m.auth.apiKey=my-api-key"
                    )
                    .run(context -> {
                        M2mAuthProperties props = context.getBean(M2mAuthProperties.class);

                        assertThat(props.isEnabled()).isTrue();
                        assertThat(props.getProtectedPaths()).containsExactly("/api/internal/**");
                        assertThat(props.getServiceName()).isEqualTo("my-service");
                        assertThat(props.getApiKey()).isEqualTo("my-api-key");
                        assertThat(props.getServices()).isNotNull().isEmpty();
                    });
        }

        @Test
        @DisplayName("仅配置 services（不配旧字段）时新字段正确绑定，旧字段保持默认值")
        void shouldBindServicesWithoutOldFields() {
            contextRunner
                    .withPropertyValues(
                            "m2m.auth.services.file-svc=file-key"
                    )
                    .run(context -> {
                        M2mAuthProperties props = context.getBean(M2mAuthProperties.class);

                        assertThat(props.isEnabled()).isFalse(); // 默认值
                        assertThat(props.getProtectedPaths()).isEmpty(); // 默认值
                        assertThat(props.getServiceName()).isNull(); // 默认值
                        assertThat(props.getApiKey()).isNull(); // 默认值
                        assertThat(props.getServices())
                                .hasSize(1)
                                .containsEntry("file-svc", "file-key");
                    });
        }

        @Test
        @DisplayName("enabled 为 false 时不配 services，所有字段保持默认")
        void shouldKeepDefaultsWhenOnlyEnabledIsFalse() {
            contextRunner
                    .withPropertyValues("m2m.auth.enabled=false")
                    .run(context -> {
                        M2mAuthProperties props = context.getBean(M2mAuthProperties.class);

                        assertThat(props.isEnabled()).isFalse();
                        assertThat(props.getProtectedPaths()).isEmpty();
                        assertThat(props.getServiceName()).isNull();
                        assertThat(props.getApiKey()).isNull();
                        assertThat(props.getServices()).isEmpty();
                    });
        }

        @Test
        @DisplayName("protectedPaths 多条目与 services 共存")
        void shouldBindMultipleProtectedPathsAndServicesTogether() {
            contextRunner
                    .withPropertyValues(
                            "m2m.auth.enabled=true",
                            "m2m.auth.protectedPaths[0]=/internal/**",
                            "m2m.auth.protectedPaths[1]=/admin/**",
                            "m2m.auth.protectedPaths[2]=/callback/**",
                            "m2m.auth.services.svc1=key1",
                            "m2m.auth.services.svc2=key2",
                            "m2m.auth.services.svc3=key3"
                    )
                    .run(context -> {
                        M2mAuthProperties props = context.getBean(M2mAuthProperties.class);

                        assertThat(props.isEnabled()).isTrue();
                        assertThat(props.getProtectedPaths())
                                .hasSize(3)
                                .containsExactly("/internal/**", "/admin/**", "/callback/**");
                        assertThat(props.getServices())
                                .hasSize(3)
                                .containsEntry("svc1", "key1")
                                .containsEntry("svc2", "key2")
                                .containsEntry("svc3", "key3");
                    });
        }
    }
}
