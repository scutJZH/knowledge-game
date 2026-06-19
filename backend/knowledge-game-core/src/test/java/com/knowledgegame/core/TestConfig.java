package com.knowledgegame.core;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * 测试专用 Spring Boot 配置类
 * <p>
 * core 模块是库模块（非 Spring Boot 应用），无 @SpringBootApplication 类。
 * @DataJpaTest 需要 @SpringBootConfiguration 来确定根包路径，此空配置满足该需求。
 */
@SpringBootConfiguration
@EnableAutoConfiguration
public class TestConfig {
}
