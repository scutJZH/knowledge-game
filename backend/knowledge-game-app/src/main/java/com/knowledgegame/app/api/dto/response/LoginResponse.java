package com.knowledgegame.app.api.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * 登录响应 DTO（包含双令牌 + 用户信息）
 */
@Data
@Builder
public class LoginResponse {

    /** Access Token（短期，用于 API 认证） */
    private String accessToken;

    /** Refresh Token（长期，用于刷新 Access Token） */
    private String refreshToken;

    /** Access Token 过期时间（秒） */
    private long expiresIn;

    /** 用户信息 */
    private UserResponse user;
}
