package com.knowledgegame.admin.api.controller;

import com.knowledgegame.admin.api.dto.request.AdminLoginRequest;
import com.knowledgegame.admin.api.dto.request.AdminRefreshTokenRequest;
import com.knowledgegame.admin.api.dto.request.LogoutRequest;
import com.knowledgegame.admin.api.dto.response.AdminLoginResponse;
import com.knowledgegame.admin.api.dto.response.AdminRefreshTokenResponse;
import com.knowledgegame.admin.application.service.AdminLoginAppService;
import com.knowledgegame.auth.security.SecurityUtils;
import com.knowledgegame.core.common.result.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端认证 Controller（登录/刷新/登出）
 */
@RestController
@RequestMapping("/api/admin")
public class AdminAuthController {

    private final AdminLoginAppService adminLoginAppService;

    public AdminAuthController(AdminLoginAppService adminLoginAppService) {
        this.adminLoginAppService = adminLoginAppService;
    }

    /**
     * 管理端登录
     */
    @PostMapping("/login")
    public Result<AdminLoginResponse> login(@Valid @RequestBody AdminLoginRequest request) {
        AdminLoginResponse response = adminLoginAppService.login(request.getUsername(), request.getPassword());
        return Result.success(response);
    }

    /**
     * 管理端刷新令牌
     */
    @PostMapping("/refresh-token")
    public Result<AdminRefreshTokenResponse> refreshToken(@Valid @RequestBody AdminRefreshTokenRequest request) {
        AdminRefreshTokenResponse response = adminLoginAppService.refreshToken(request.getRefreshToken());
        return Result.success(response);
    }

    /**
     * 管理端登出（将 Access Token 和 Refresh Token 的 jti 加入黑名单）
     */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader("Authorization") String authorization,
                               @Valid @RequestBody LogoutRequest request) {
        String accessToken = SecurityUtils.extractBearerToken(authorization);
        adminLoginAppService.logout(accessToken, request.getRefreshToken());
        return Result.success();
    }
}
