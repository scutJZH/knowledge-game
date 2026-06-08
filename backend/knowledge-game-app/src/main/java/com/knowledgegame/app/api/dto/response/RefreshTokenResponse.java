package com.knowledgegame.app.api.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * 刷新令牌响应 DTO
 */
@Data
@Builder
public class RefreshTokenResponse {

    /** 新的 Access Token */
    private String accessToken;

    /** 新的 Refresh Token */
    private String refreshToken;

    /** Access Token 过期时间（秒） */
    private long expiresIn;
}
