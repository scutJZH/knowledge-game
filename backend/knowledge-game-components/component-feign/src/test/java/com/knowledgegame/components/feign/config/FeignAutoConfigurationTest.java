package com.knowledgegame.components.feign.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FeignAutoConfiguration 自动配置加载测试
 * <p>
 * 验收标准：
 * 1. FeignAutoConfiguration 能被 Spring Boot 自动加载
 * 2. 自动配置类在上下文中注册成功
 */
class FeignAutoConfigurationTest {

    /**
     * ApplicationContextRunner 用于在隔离环境中测试自动配置
     */
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FeignAutoConfiguration.class));

    /**
     * 验证 FeignAutoConfiguration 能被 Spring 自动加载
     */
    @Test
    void 应该能自动加载FeignAutoConfiguration() {
        contextRunner.run(context -> {
            // 验证上下文中包含 FeignAutoConfiguration 的 Bean 定义
            assertThat(context).hasSingleBean(FeignAutoConfiguration.class);
        });
    }

    /**
     * 验证自动配置类在上下文中成功注册且上下文正常启动
     */
    @Test
    void 上下文应该正常启动() {
        contextRunner.run(context -> {
            // 验证上下文启动成功
            assertThat(context).hasNotFailed();
        });
    }
}
