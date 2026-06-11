package com.knowledgegame.app;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Nacos 配置上下文启动测试
 * <p>
 * 验收标准：
 * 1. Spring 上下文在 test profile 下绕过 Nacos 连接能正常启动
 * 2. 核心组件（KnowledgeGameApplication）成功加载
 * 3. @EnableFeignClients 注解正确配置，Feign 相关基础设施可用
 */
@SpringBootTest
@ActiveProfiles("test")
class NacosConfigContextTest {

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * 验证 Spring 上下文在 test profile 下能正常启动（绕过 Nacos）
     */
    @Test
    void 上下文应该正常启动() {
        // 上下文成功注入即证明启动正常
        assertThat(applicationContext).isNotNull();
    }

    /**
     * 验证 KnowledgeGameApplication 作为主配置类被正确加载
     */
    @Test
    void 主启动类应该被加载() {
        // 验证主启动类的 Bean 定义存在
        assertThat(applicationContext.getBean(KnowledgeGameApplication.class)).isNotNull();
    }

    /**
     * 验证环境配置为 test profile
     */
    @Test
    void 应该使用testProfile() {
        // 验证当前激活的 profile 包含 "test"
        String[] activeProfiles = applicationContext.getEnvironment().getActiveProfiles();
        assertThat(activeProfiles).contains("test");
    }

    /**
     * 验证 Nacos 相关自动配置已被禁用
     */
    @Test
    void nacos相关配置应该被禁用() {
        // 验证 Nacos Discovery 自动配置未注册
        String discoveryAutoConfig = "com.alibaba.cloud.nacos.discovery.NacosDiscoveryAutoConfiguration";
        assertThat(applicationContext.containsBeanDefinition(discoveryAutoConfig)).isFalse();

        // 验证 Nacos Config 自动配置未注册
        String configAutoConfig = "com.alibaba.cloud.nacos.config.NacosConfigAutoConfiguration";
        assertThat(applicationContext.containsBeanDefinition(configAutoConfig)).isFalse();
    }
}
