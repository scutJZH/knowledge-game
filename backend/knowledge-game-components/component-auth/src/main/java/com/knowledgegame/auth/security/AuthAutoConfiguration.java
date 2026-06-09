package com.knowledgegame.auth.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 认证组件自动配置（BCrypt + JWT Token + Token 黑名单 + JwtAuthenticationFilter）
 */
@AutoConfiguration
@EnableConfigurationProperties(JwtProperties.class)
public class AuthAutoConfiguration {

    /**
     * BCrypt 密码加密器
     */
    @Bean
    public org.springframework.security.crypto.password.PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * JWT Token 工具
     */
    @Bean
    public JwtTokenProvider jwtTokenProvider(JwtProperties jwtProperties) {
        return new JwtTokenProvider(jwtProperties);
    }

    /**
     * Token 黑名单（内存实现，后期 REQ-81 迁移 Redis）
     */
    @Bean
    public TokenBlacklist tokenBlacklist() {
        return new InMemoryTokenBlacklist();
    }

    /**
     * JWT 认证过滤器
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
                                                           TokenBlacklist tokenBlacklist) {
        return new JwtAuthenticationFilter(jwtTokenProvider, tokenBlacklist);
    }
}
