package com.knowledgegame.admin.api.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * 管理端登录响应 DTO（包含双令牌 + 用户信息）
 */
@Data
@Builder
public class AdminLoginResponse {

    /** Access Token */
    private String accessToken;

    /** Refresh Token */
    private String refreshToken;

    /** Access Token 过期时间（秒） */
    private long expiresIn;

    /** 用户信息 */
    private AdminUserResponse user;
}
