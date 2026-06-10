package com.knowledgegame.components.log.config;

import com.knowledgegame.components.log.aspect.LogParamAspect;
import com.knowledgegame.components.log.filter.TraceIdFilter;
import com.knowledgegame.components.log.masking.SensitiveDataConverter;
import com.knowledgegame.components.log.properties.LogProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * 日志组件自动配置
 */
@AutoConfiguration
@EnableConfigurationProperties(LogProperties.class)
public class LogAutoConfiguration {

    /**
     * 注册 TraceId 过滤器，优先级最高（在 JWT Filter 之前）
     */
    @Bean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilter() {
        FilterRegistrationBean<TraceIdFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TraceIdFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("traceIdFilter");
        return registration;
    }

    /**
     * 将配置注入 SensitiveDataConverter 的静态持有者
     * Logback 通过 conversionRule 自建 Converter 实例，无法通过 @Bean 管理，
     * 因此使用静态持有者模式让 Logback 实例获取配置
     */
    @Bean
    public LogPropertiesHolder logPropertiesHolder(LogProperties logProperties) {
        SensitiveDataConverter.setLogProperties(logProperties);
        return new LogPropertiesHolder();
    }

    /**
     * 注册 @LogParam AOP 切面
     */
    @Bean
    public LogParamAspect logParamAspect() {
        return new LogParamAspect();
    }

    /**
     * 占位 Bean，确保配置注入时机正确
     */
    static class LogPropertiesHolder {
    }
}
