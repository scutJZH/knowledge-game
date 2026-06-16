package com.knowledgegame.app.api.controller;

import com.knowledgegame.app.api.dto.request.RegisterRequest;
import com.knowledgegame.app.api.dto.request.UpdateUserRequest;
import com.knowledgegame.app.api.dto.request.LoginRequest;
import com.knowledgegame.app.api.dto.request.LogoutRequest;
import com.knowledgegame.app.api.dto.request.RefreshTokenRequest;
import com.knowledgegame.app.api.dto.response.LoginResponse;
import com.knowledgegame.app.api.dto.response.RefreshTokenResponse;
import com.knowledgegame.app.api.dto.response.UserResponse;
import com.knowledgegame.app.application.command.RegisterCommand;
import com.knowledgegame.app.application.command.LoginCommand;
import com.knowledgegame.app.application.service.UserAppService;
import com.knowledgegame.auth.security.SecurityUtils;
import com.knowledgegame.core.common.result.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户 Controller（仅参数接收 + 结果返回，无业务逻辑）
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserAppService userAppService;

    public UserController(UserAppService userAppService) {
        this.userAppService = userAppService;
    }

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Result<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        RegisterCommand command = RegisterCommand.builder()
                .username(request.getUsername())
                .rawPassword(request.getPassword())
                .nickname(request.getNickname())
                .build();
        return Result.success(userAppService.register(command));
    }

    /**
     * 用户登录（返回 Access Token + Refresh Token）
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginCommand command = LoginCommand.builder()
                .username(request.getUsername())
                .rawPassword(request.getPassword())
                .build();
        return Result.success(userAppService.login(command));
    }

    /**
     * 刷新令牌
     */
    @PostMapping("/refresh-token")
    public Result<RefreshTokenResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return Result.success(userAppService.refreshToken(request.getRefreshToken()));
    }

    /**
     * 查询用户详情
     */
    @GetMapping("/{id}")
    public Result<UserResponse> getById(@PathVariable Long id) {
        return Result.success(userAppService.getUserById(id));
    }

    /**
     * 查询用户列表
     */
    @GetMapping
    public Result<List<UserResponse>> list() {
        return Result.success(userAppService.listUsers());
    }

    /**
     * 更新用户信息
     */
    @PutMapping("/{id}")
    public Result<UserResponse> update(@PathVariable Long id,
                                       @Valid @RequestBody UpdateUserRequest request) {
        return Result.success(userAppService.updateUser(id, request.getNickname(), request.getAvatarFileId()));
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userAppService.deleteUser(id);
        return Result.success();
    }

    /**
     * 用户登出
     */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader("Authorization") String authorization,
                               @Valid @RequestBody LogoutRequest request) {
        String accessToken = SecurityUtils.extractBearerToken(authorization);
        userAppService.logout(accessToken, request.getRefreshToken());
        return Result.success();
    }
}
