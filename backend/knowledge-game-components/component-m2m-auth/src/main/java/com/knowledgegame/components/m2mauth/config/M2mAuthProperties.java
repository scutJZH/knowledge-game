package com.knowledgegame.components.m2mauth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 机机鉴权配置属性
 */
@ConfigurationProperties(prefix = "m2m.auth")
public class M2mAuthProperties {

    /**
     * 是否启动机机鉴权（默认关闭）
     */
    private boolean enabled = false;

    /**
     * 服务名 → API Key 映射（服务端使用，校验调用方身份）
     */
    private Map<String, String> keys = new HashMap<>();

    /**
     * 需要机机鉴权的路径模式列表（Ant 风格，如 /internal/**）
     */
    private List<String> protectedPaths = new ArrayList<>();

    /**
     * 当前服务名（客户端使用，标识自己）
     */
    private String serviceName;

    /**
     * 当前服务的 API Key（客户端使用）
     */
    private String apiKey;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, String> getKeys() {
        return keys;
    }

    public void setKeys(Map<String, String> keys) {
        this.keys = keys;
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
