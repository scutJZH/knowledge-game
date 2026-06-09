package com.knowledgegame.app.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登出请求 DTO（传 refreshToken 用于黑名单）
 */
@Data
public class LogoutRequest {

    @NotBlank(message = "Refresh Token 不能为空")
    private String refreshToken;
}
