package com.knowledgegame.components.m2mauth.interceptor;

import com.knowledgegame.components.m2mauth.config.M2mAuthProperties;
import feign.RequestTemplate;
import feign.Target;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * M2mFeignInterceptor 独立黑盒测试（脱离 Spring 上下文）。
 * 按 PRD 6.1 节行为规格覆盖场景 1-7。
 */
class IndependentM2mFeignInterceptorTest {

    private M2mAuthProperties properties;
    private M2mFeignInterceptor interceptor;

    @BeforeEach
    void setUp() {
        properties = new M2mAuthProperties();
        interceptor = new M2mFeignInterceptor(properties);
    }

    /**
     * 辅助方法：构建一个已设置 feignTarget 的 RequestTemplate。
     *
     * @param targetName 模拟的目标服务名（Target.name() 返回值）
     */
    private RequestTemplate templateWithTarget(String targetName) {
        Target<?> target = mock(Target.class);
        when(target.name()).thenReturn(targetName);
        RequestTemplate template = new RequestTemplate();
        template.feignTarget(target);
        return template;
    }

    // ==================== 场景 1：serviceName 未配置 ====================

    @Nested
    @DisplayName("场景1：serviceName 未配置时抛 IllegalStateException")
    class ServiceNameNotConfigured {

        @Test
        @DisplayName("serviceName 为 null 时抛异常，消息含「service-name 未配置」")
        void shouldThrowWhenServiceNameIsNull() {
            properties.setServices(Map.of("svc", "key"));
            RequestTemplate template = templateWithTarget("svc");

            assertThatThrownBy(() -> interceptor.apply(template))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("service-name 未配置");
        }

        @Test
        @DisplayName("serviceName 为空字符串时抛异常")
        void shouldThrowWhenServiceNameIsEmpty() {
            properties.setServiceName("");
            properties.setServices(Map.of("svc", "key"));
            RequestTemplate template = templateWithTarget("svc");

            assertThatThrownBy(() -> interceptor.apply(template))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("service-name 未配置");
        }

        @Test
        @DisplayName("serviceName 为纯空格时抛异常")
        void shouldThrowWhenServiceNameIsBlank() {
            properties.setServiceName("   ");
            properties.setServices(Map.of("svc", "key"));
            RequestTemplate template = templateWithTarget("svc");

            assertThatThrownBy(() -> interceptor.apply(template))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("service-name 未配置");
        }

        @Test
        @DisplayName("serviceName 未配置时不会尝试读取 feignTarget（短路行为）")
        void shouldShortCircuitBeforeReadingFeignTarget() {
            // serviceName 为 null，即使 feignTarget 也为 null，也应按 serviceName 报错
            RequestTemplate template = new RequestTemplate(); // feignTarget 未设置

            assertThatThrownBy(() -> interceptor.apply(template))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("service-name 未配置");
        }
    }

    // ==================== 场景 2：feignTarget 为空 ====================

    @Nested
    @DisplayName("场景2：feignTarget 为空时抛 IllegalStateException")
    class FeignTargetMissing {

        @Test
        @DisplayName("feignTarget 未设置（为 null）时抛异常，消息含「feignTarget() 为空」")
        void shouldThrowWhenFeignTargetIsNull() {
            properties.setServiceName("caller");
            properties.setServices(Map.of("svc", "key"));
            RequestTemplate template = new RequestTemplate(); // 未调用 feignTarget()

            assertThatThrownBy(() -> interceptor.apply(template))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("feignTarget() 为空");
        }

        @Test
        @DisplayName("target.name() 返回 null 时抛异常")
        void shouldThrowWhenTargetNameIsNull() {
            properties.setServiceName("caller");
            properties.setServices(Map.of("svc", "key"));
            Target<?> target = mock(Target.class);
            when(target.name()).thenReturn(null);
            RequestTemplate template = new RequestTemplate();
            template.feignTarget(target);

            assertThatThrownBy(() -> interceptor.apply(template))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("feignTarget() 为空");
        }

        @Test
        @DisplayName("target.name() 返回空字符串时抛异常")
        void shouldThrowWhenTargetNameIsEmpty() {
            properties.setServiceName("caller");
            properties.setServices(Map.of("svc", "key"));
            RequestTemplate template = templateWithTarget("");

            assertThatThrownBy(() -> interceptor.apply(template))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("feignTarget() 为空");
        }

        @Test
        @DisplayName("target.name() 返回纯空格时抛异常")
        void shouldThrowWhenTargetNameIsBlank() {
            properties.setServiceName("caller");
            properties.setServices(Map.of("svc", "key"));
            RequestTemplate template = templateWithTarget("   ");

            assertThatThrownBy(() -> interceptor.apply(template))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("feignTarget() 为空");
        }
    }

    // ==================== 场景 3：services Map 命中 ====================

    @Nested
    @DisplayName("场景3：services Map 命中时注入请求头")
    class ServiceMapHit {

        @Test
        @DisplayName("目标服务在 services 中时注入 X-Service-Name 和 X-Service-Key")
        void shouldInjectHeadersWhenServiceInMap() {
            properties.setServiceName("knowledge-game-admin");
            properties.setServices(Map.of("knowledge-game-file", "file-api-key"));
            RequestTemplate template = templateWithTarget("knowledge-game-file");

            interceptor.apply(template);

            assertThat(template.headers().get("X-Service-Name"))
                    .containsExactly("knowledge-game-admin");
            assertThat(template.headers().get("X-Service-Key"))
                    .containsExactly("file-api-key");
        }

        @Test
        @DisplayName("X-Service-Name 的值必须等于 properties.serviceName")
        void shouldSetXServiceNameEqualToCallerServiceName() {
            properties.setServiceName("my-caller-service");
            properties.setServices(Map.of("target-svc", "target-key"));
            RequestTemplate template = templateWithTarget("target-svc");

            interceptor.apply(template);

            assertThat(template.headers().get("X-Service-Name"))
                    .containsExactly("my-caller-service");
        }

        @Test
        @DisplayName("服务名包含特殊字符时也能正确注入")
        void shouldHandleSpecialCharactersInServiceName() {
            properties.setServiceName("svc-with-dash_AND.upper");
            properties.setServices(Map.of("target", "key-123!@#"));
            RequestTemplate template = templateWithTarget("target");

            interceptor.apply(template);

            assertThat(template.headers().get("X-Service-Name"))
                    .containsExactly("svc-with-dash_AND.upper");
            assertThat(template.headers().get("X-Service-Key"))
                    .containsExactly("key-123!@#");
        }
    }

    // ==================== 场景 4：services Map 未命中 ====================

    @Nested
    @DisplayName("场景4：services Map 未命中时抛异常")
    class ServiceMapMiss {

        @Test
        @DisplayName("目标服务不在 services 中时抛异常，消息含目标服务名")
        void shouldThrowWhenTargetNotInServices() {
            properties.setServiceName("caller");
            properties.setServices(Map.of("other-svc", "other-key"));
            RequestTemplate template = templateWithTarget("unknown-svc");

            assertThatThrownBy(() -> interceptor.apply(template))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("unknown-svc")
                    .hasMessageContaining("未在 m2m.auth.services 中配置 apiKey");
        }

        @Test
        @DisplayName("目标服务在 services 中但值为空字符串时抛异常")
        void shouldThrowWhenServiceKeyIsEmpty() {
            properties.setServiceName("caller");
            properties.setServices(new HashMap<>(Map.of("svc", "")));
            RequestTemplate template = templateWithTarget("svc");

            assertThatThrownBy(() -> interceptor.apply(template))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("svc")
                    .hasMessageContaining("未在 m2m.auth.services 中配置 apiKey");
        }

        @Test
        @DisplayName("目标服务在 services 中但值为纯空格时抛异常")
        void shouldThrowWhenServiceKeyIsBlank() {
            properties.setServiceName("caller");
            properties.setServices(new HashMap<>(Map.of("svc", "   ")));
            RequestTemplate template = templateWithTarget("svc");

            assertThatThrownBy(() -> interceptor.apply(template))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("svc")
                    .hasMessageContaining("未在 m2m.auth.services 中配置 apiKey");
        }

        @Test
        @DisplayName("目标服务在 services 中但值为 null 时抛异常（key 存在但 value=null）")
        void shouldThrowWhenServiceKeyIsNull() {
            properties.setServiceName("caller");
            HashMap<String, String> map = new HashMap<>();
            map.put("svc", null);
            properties.setServices(map);
            RequestTemplate template = templateWithTarget("svc");

            assertThatThrownBy(() -> interceptor.apply(template))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("svc")
                    .hasMessageContaining("未在 m2m.auth.services 中配置 apiKey");
        }

        @Test
        @DisplayName("services 为 null 时抛 IllegalStateException（防御性校验）")
        void shouldThrowWhenServicesIsNull() {
            properties.setServiceName("caller");
            properties.setServices(null);
            RequestTemplate template = templateWithTarget("svc");

            assertThatThrownBy(() -> interceptor.apply(template))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("services 未配置");
        }
    }

    // ==================== 场景 5：services 为空 + apiKey 有旧值 ====================

    @Nested
    @DisplayName("场景5：services 为空时即使 apiKey 有旧值也不回落")
    class NoFallbackToOldApiKey {

        @Test
        @DisplayName("services 为空 Map + apiKey 有值 → 抛异常，不回落旧字段")
        void shouldNotFallbackWhenServicesIsEmpty() {
            properties.setServiceName("caller");
            properties.setApiKey("old-api-key-value"); // 旧配置字段
            // services 保持默认空 HashMap
            RequestTemplate template = templateWithTarget("any-svc");

            assertThatThrownBy(() -> interceptor.apply(template))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("未在 m2m.auth.services 中配置 apiKey");
        }

        @Test
        @DisplayName("services 为空 + apiKey 为 null → 同样抛异常")
        void shouldThrowWhenServicesIsEmptyAndApiKeyIsNull() {
            properties.setServiceName("caller");
            // services 默认空，apiKey 默认 null
            RequestTemplate template = templateWithTarget("any-svc");

            assertThatThrownBy(() -> interceptor.apply(template))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("未在 m2m.auth.services 中配置 apiKey");
        }
    }

    // ==================== 场景 6：多目标服务映射 ====================

    @Nested
    @DisplayName("场景6：多目标服务映射 - 不同 target 注入不同 apiKey")
    class MultiTargetMapping {

        @Test
        @DisplayName("两个不同目标服务各自注入对应的 apiKey")
        void shouldInjectDifferentKeysForDifferentTargets() {
            properties.setServiceName("caller");
            properties.setServices(Map.of(
                    "service-a", "key-for-a",
                    "service-b", "key-for-b"
            ));

            RequestTemplate templateA = templateWithTarget("service-a");
            RequestTemplate templateB = templateWithTarget("service-b");

            interceptor.apply(templateA);
            interceptor.apply(templateB);

            assertThat(templateA.headers().get("X-Service-Name")).containsExactly("caller");
            assertThat(templateA.headers().get("X-Service-Key")).containsExactly("key-for-a");
            assertThat(templateB.headers().get("X-Service-Name")).containsExactly("caller");
            assertThat(templateB.headers().get("X-Service-Key")).containsExactly("key-for-b");
        }

        @Test
        @DisplayName("三个目标服务各自获取正确 apiKey")
        void shouldInjectCorrectKeysForThreeTargets() {
            properties.setServiceName("caller");
            properties.setServices(Map.of(
                    "svc-1", "key-1",
                    "svc-2", "key-2",
                    "svc-3", "key-3"
            ));

            RequestTemplate t1 = templateWithTarget("svc-1");
            RequestTemplate t2 = templateWithTarget("svc-2");
            RequestTemplate t3 = templateWithTarget("svc-3");

            interceptor.apply(t1);
            interceptor.apply(t2);
            interceptor.apply(t3);

            assertThat(t1.headers().get("X-Service-Key")).containsExactly("key-1");
            assertThat(t2.headers().get("X-Service-Key")).containsExactly("key-2");
            assertThat(t3.headers().get("X-Service-Key")).containsExactly("key-3");
        }

        @Test
        @DisplayName("多个目标但其中一个未配置时抛异常")
        void shouldThrowWhenOneTargetNotConfigured() {
            properties.setServiceName("caller");
            properties.setServices(Map.of("svc-a", "key-a"));
            RequestTemplate template = templateWithTarget("svc-b"); // 未配置

            assertThatThrownBy(() -> interceptor.apply(template))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("svc-b");
        }
    }

    // ==================== 场景 7：多线程并发 ====================

    @Nested
    @DisplayName("场景7：多线程并发安全 - 无共享可变状态")
    class ThreadSafety {

        @Test
        @DisplayName("16 线程并发调用 apply 不会互相干扰，各自获得正确头部")
        void shouldBeThreadSafe() throws Exception {
            properties.setServiceName("caller");
            properties.setServices(Map.of(
                    "svc-a", "key-a",
                    "svc-b", "key-b"
            ));

            int threadCount = 16;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger errorCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                final String svc = i % 2 == 0 ? "svc-a" : "svc-b";
                final String expectedKey = i % 2 == 0 ? "key-a" : "key-b";
                executor.submit(() -> {
                    try {
                        Target<?> target = mock(Target.class);
                        when(target.name()).thenReturn(svc);
                        RequestTemplate template = new RequestTemplate();
                        template.feignTarget(target);

                        interceptor.apply(template);

                        assertThat(template.headers().get("X-Service-Name"))
                                .containsExactly("caller");
                        assertThat(template.headers().get("X-Service-Key"))
                                .containsExactly(expectedKey);
                    } catch (AssertionError e) {
                        errorCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean finished = latch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            assertThat(finished).isTrue();
            assertThat(errorCount.get()).isZero();
        }
    }
}
