package com.knowledgegame.components.m2mauth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 机机鉴权配置属性
 * <p>
 * 校验模型：调用方持有 {@code apiKey}，被调用方也持有 {@code apiKey}，
 * 被调用方只校验 {@code X-Service-Key == apiKey}，不关心调用方是谁。
 * 新增调用方只需在调用方配置文件中填被调用方的 key，被调用方无需改动。
 */
@ConfigurationProperties(prefix = "m2m.auth")
public class M2mAuthProperties {

    /**
     * 是否启动机机鉴权（默认关闭）
     */
    private boolean enabled = false;

    /**
     * 需要机机鉴权的路径模式列表（Ant 风格，如 /internal/**）
     */
    private List<String> protectedPaths = new ArrayList<>();

    /**
     * 当前服务名（可选，用于 Feign 拦截器标识调用方身份，便于日志追踪）
     */
    private String serviceName;

    /**
     * API Key（客户端和服务端共用）
     * <ul>
     *   <li>客户端：用于 Feign 拦截器注入 X-Service-Key 请求头</li>
     *   <li>服务端：用于 Filter 校验 X-Service-Key 是否与自身配置一致</li>
     * </ul>
     */
    private String apiKey;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getProtectedPaths() {
        return protectedPaths;
    }

    public void setProtectedPaths(List<String> protectedPaths) {
        this.protectedPaths = protectedPaths;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}
