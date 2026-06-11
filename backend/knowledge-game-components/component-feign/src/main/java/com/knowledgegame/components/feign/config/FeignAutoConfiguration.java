package com.knowledgegame.components.feign.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Feign 模块自动配置类
 * <p>
 * 自动扫描 Feign Client 接口，各服务引入 component-feign 后无需手动添加 @EnableFeignClients。
 */
@AutoConfiguration
@EnableFeignClients(basePackages = "com.knowledgegame.components.feign.client")
public class FeignAutoConfiguration {

}
