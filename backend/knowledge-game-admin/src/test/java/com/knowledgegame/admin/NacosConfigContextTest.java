package com.knowledgegame.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Nacos 配置上下文启动测试
 * <p>
 * 验证在 test profile 下绕过 Nacos 连接，Spring 上下文能正常启动。
 * 后续新增 @SpringBootTest 级别的测试时，均需使用 @ActiveProfiles("test")。
 */
@SpringBootTest
@ActiveProfiles("test")
class NacosConfigContextTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextShouldStart() {
        assertThat(applicationContext).isNotNull();
    }

    @Test
    void mainApplicationShouldBeLoaded() {
        assertThat(applicationContext.getBean(KnowledgeGameAdminApplication.class)).isNotNull();
    }

    @Test
    void testProfileShouldBeActive() {
        assertThat(applicationContext.getEnvironment().getActiveProfiles()).contains("test");
    }
}
