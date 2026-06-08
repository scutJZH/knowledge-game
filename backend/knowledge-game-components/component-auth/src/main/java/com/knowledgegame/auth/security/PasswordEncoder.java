package com.knowledgegame.auth.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 密码加密配置（BCrypt）
 */
@Configuration
public class PasswordEncoder {

    /**
     * BCrypt 密码加密器
     */
    @Bean
    public org.springframework.security.crypto.password.PasswordEncoder passwordEncoder() {
        return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
    }
}
