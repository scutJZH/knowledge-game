package com.knowledgegame.auth.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 密码加密自动配置（BCrypt）
 */
@AutoConfiguration
public class PasswordEncoder {

    /**
     * BCrypt 密码加密器
     */
    @Bean
    public org.springframework.security.crypto.password.PasswordEncoder passwordEncoder() {
        return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
    }
}
