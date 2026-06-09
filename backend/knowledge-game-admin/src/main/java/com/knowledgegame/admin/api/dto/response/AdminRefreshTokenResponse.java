package com.knowledgegame.admin.api.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * 管理端刷新令牌响应 DTO
 */
@Data
@Builder
public class AdminRefreshTokenResponse {

    /** 新的 Access Token */
    private String accessToken;

    /** 新的 Refresh Token */
    private String refreshToken;

    /** Access Token 过期时间（秒） */
    private long expiresIn;
}
