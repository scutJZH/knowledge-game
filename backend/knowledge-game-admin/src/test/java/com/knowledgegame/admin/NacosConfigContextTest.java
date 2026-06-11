package com.knowledgegame.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nacos 配置上下文测试
 * <p>
 * 验证在禁用 Nacos 的 test profile 下，Spring 上下文能正常启动。
 * 后续新增 @SpringBootTest 级别的测试时，均需使用 @ActiveProfiles("test")。
 */
@SpringBootTest
@ActiveProfiles("test")
class NacosConfigContextTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void 上下文应该正常启动() {
        assertNotNull(applicationContext);
    }

    @Test
    void 主启动类应该被加载() {
        assertNotNull(applicationContext.getBean(KnowledgeGameAdminApplication.class));
    }

    @Test
    void 应该使用testProfile() {
        assertTrue(applicationContext.getEnvironment().getActiveProfiles().length > 0);
        assertTrue(
                java.util.List.of(applicationContext.getEnvironment().getActiveProfiles())
                        .contains("test")
        );
    }
}
