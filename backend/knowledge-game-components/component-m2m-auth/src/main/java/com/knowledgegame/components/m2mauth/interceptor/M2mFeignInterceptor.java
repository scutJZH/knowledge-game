package com.knowledgegame.components.m2mauth.interceptor;

import com.knowledgegame.components.m2mauth.config.M2mAuthProperties;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import feign.Target;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * Feign 请求拦截器（V2）
 * 按目标服务名从 {@code services} Map 查找对应 apiKey 注入 X-Service-Key 头，
 * 未命中抛 IllegalStateException 强制显式配置。
 */
public class M2mFeignInterceptor implements RequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(M2mFeignInterceptor.class);

    private static final String HEADER_SERVICE_NAME = "X-Service-Name";
    private static final String HEADER_SERVICE_KEY = "X-Service-Key";

    private final M2mAuthProperties properties;

    public M2mFeignInterceptor(M2mAuthProperties properties) {
        this.properties = properties;
    }

    @Override
    public void apply(RequestTemplate template) {
        String serviceName = properties.getServiceName();
        if (!StringUtils.hasText(serviceName)) {
            throw new IllegalStateException("m2m.auth.service-name 未配置，无法注入 X-Service-Name");
        }

        String targetName = resolveTargetName(template);
        String apiKey = resolveApiKey(targetName);
        template.header(HEADER_SERVICE_NAME, serviceName);
        template.header(HEADER_SERVICE_KEY, apiKey);

        log.debug("M2M 鉴权注入成功：目标服务={}", targetName);
    }

    /**
     * 从 RequestTemplate 解析目标服务名
     *
     * @throws IllegalStateException feignTarget 为空或目标服务名为空
     */
    private String resolveTargetName(RequestTemplate template) {
        Target<?> target = template.feignTarget();
        if (target == null || !StringUtils.hasText(target.name())) {
            throw new IllegalStateException("Feign RequestTemplate.feignTarget() 为空，无法解析目标服务名");
        }
        return target.name();
    }

    /**
     * 按目标服务名从 services Map 解析 apiKey
     *
     * @throws IllegalStateException services 未配置或目标服务不在 Map 中
     */
    private String resolveApiKey(String targetName) {
        Map<String, String> services = properties.getServices();
        if (services == null) {
            throw new IllegalStateException("m2m.auth.services 未配置");
        }
        String key = services.get(targetName);
        if (!StringUtils.hasText(key)) {
            throw new IllegalStateException(
                    "目标服务 [" + targetName + "] 未在 m2m.auth.services 中配置 apiKey");
        }
        return key;
    }
}
