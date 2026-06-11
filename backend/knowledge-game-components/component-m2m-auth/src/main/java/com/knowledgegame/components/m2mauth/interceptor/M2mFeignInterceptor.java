package com.knowledgegame.components.m2mauth.interceptor;

import com.knowledgegame.components.m2mauth.config.M2mAuthProperties;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * Feign 请求拦截器
 * 自动在 Feign 调用中注入 X-Service-Name 和 X-Service-Key 请求头
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
        String apiKey = properties.getApiKey();

        if (!StringUtils.hasText(serviceName) || !StringUtils.hasText(apiKey)) {
            log.warn("M2M Feign 拦截器：serviceName 或 apiKey 未配置，跳过注入");
            return;
        }

        template.header(HEADER_SERVICE_NAME, serviceName);
        template.header(HEADER_SERVICE_KEY, apiKey);
    }
}
