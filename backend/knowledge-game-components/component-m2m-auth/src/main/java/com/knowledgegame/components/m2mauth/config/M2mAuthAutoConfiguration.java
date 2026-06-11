package com.knowledgegame.components.m2mauth.config;

import com.knowledgegame.components.m2mauth.filter.M2mAuthFilter;
import com.knowledgegame.components.m2mauth.interceptor.M2mFeignInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * 机机鉴权组件自动配置
 */
@AutoConfiguration
@EnableConfigurationProperties(M2mAuthProperties.class)
public class M2mAuthAutoConfiguration {

    /**
     * 注册 M2M 鉴权过滤器（仅在启用时注册）
     */
    @Bean
    @ConditionalOnProperty(name = "m2m.auth.enabled", havingValue = "true")
    public FilterRegistrationBean<M2mAuthFilter> m2mAuthFilter(M2mAuthProperties properties) {
        FilterRegistrationBean<M2mAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new M2mAuthFilter(properties));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 100);
        registration.setName("m2mAuthFilter");
        return registration;
    }

    /**
     * Feign 拦截器配置（嵌套静态内部类，避免 feign-core 不在 classpath 时 NoClassDefFoundError）
     * Spring Boot 通过 ASM 读取内部类字节码评估条件，不会触发类加载
     */
    @ConditionalOnClass(name = "feign.RequestInterceptor")
    static class FeignInterceptorConfiguration {

        /**
         * 注册 Feign 拦截器
         */
        @Bean
        public M2mFeignInterceptor m2mFeignInterceptor(M2mAuthProperties properties) {
            return new M2mFeignInterceptor(properties);
        }
    }
}
