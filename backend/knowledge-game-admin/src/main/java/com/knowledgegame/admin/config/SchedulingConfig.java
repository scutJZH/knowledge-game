package com.knowledgegame.admin.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务调度配置
 * <p>
 * 将 @EnableScheduling 从主启动类分离到独立配置类，
 * 避免 @WebMvcTest 切片测试加载调度上下文。
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
