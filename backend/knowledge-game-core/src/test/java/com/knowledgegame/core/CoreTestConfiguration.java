package com.knowledgegame.core;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * core 模块测试用 Spring Boot 配置类
 * <p>
 * core 模块是 Starter（无 @SpringBootApplication），@DataJpaTest 需要
 * 在包层级中找到 @SpringBootConfiguration 以加载 Spring Test 上下文。
 */
@SpringBootConfiguration
@EnableAutoConfiguration
public class CoreTestConfiguration {
}
