package com.knowledgegame.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置属性（绑定 application.yml 中的 jwt.* 配置）
 */
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /** 签名密钥（至少 32 字符，用于 HMAC-SHA256） */
    private String secret = "default-secret-key-for-knowledge-game-must-be-at-least-32-chars";

    /** Access Token 过期时间（毫秒），默认 30 分钟 */
    private long accessTokenExpiration = 1800000;

    /** Refresh Token 过期时间（毫秒），默认 7 天 */
    private long refreshTokenExpiration = 604800000;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getAccessTokenExpiration() {
        return accessTokenExpiration;
    }

    public void setAccessTokenExpiration(long accessTokenExpiration) {
        this.accessTokenExpiration = accessTokenExpiration;
    }

    public long getRefreshTokenExpiration() {
        return refreshTokenExpiration;
    }

    public void setRefreshTokenExpiration(long refreshTokenExpiration) {
        this.refreshTokenExpiration = refreshTokenExpiration;
    }
}
