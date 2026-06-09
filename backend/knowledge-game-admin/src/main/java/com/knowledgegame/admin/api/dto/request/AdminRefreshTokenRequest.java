package com.knowledgegame.admin.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 管理端刷新令牌请求 DTO
 */
@Data
public class AdminRefreshTokenRequest {

    @NotBlank(message = "Refresh Token 不能为空")
    private String refreshToken;
}
