package com.knowledgegame.admin.application.service;

import com.knowledgegame.admin.api.assembler.AdminUserAssembler;
import com.knowledgegame.admin.api.dto.response.AdminLoginResponse;
import com.knowledgegame.admin.api.dto.response.AdminRefreshTokenResponse;
import com.knowledgegame.auth.security.JwtTokenProvider;
import com.knowledgegame.auth.security.TokenBlacklist;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.common.result.ResultCode;
import com.knowledgegame.core.domain.model.domainenum.UserRole;
import com.knowledgegame.core.domain.model.entity.User;
import com.knowledgegame.core.domain.port.outbound.UserRepositoryPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 管理端认证应用服务（登录 + 刷新 + 登出）
 */
@Service
public class AdminLoginAppService {

    private final UserRepositoryPort userRepositoryPort;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklist tokenBlacklist;

    public AdminLoginAppService(UserRepositoryPort userRepositoryPort,
                                PasswordEncoder passwordEncoder,
                                JwtTokenProvider jwtTokenProvider,
                                TokenBlacklist tokenBlacklist) {
        this.userRepositoryPort = userRepositoryPort;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.tokenBlacklist = tokenBlacklist;
    }

    /**
     * 管理端登录（验证密码 + 校验 ADMIN 角色 + 签发双令牌）
     */
    public AdminLoginResponse login(String username, String password) {
        User user = userRepositoryPort.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ResultCode.BAD_CREDENTIALS));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(ResultCode.BAD_CREDENTIALS);
        }

        if (user.getRole() != UserRole.ADMIN) {
            throw new BusinessException(ResultCode.ACCESS_DENIED);
        }

        String role = user.getRole().name();
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), role);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), role);

        return AdminLoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtTokenProvider.getAccessTokenExpirationSeconds())
                .user(AdminUserAssembler.INSTANCE.toResponse(user))
                .build();
    }

    /**
     * 管理端刷新令牌（校验 Refresh Token 中的 ADMIN 角色）
     */
    public AdminRefreshTokenResponse refreshToken(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BusinessException(ResultCode.INVALID_TOKEN);
        }

        String type = jwtTokenProvider.getTypeFromToken(refreshToken);
        if (!"refresh".equals(type)) {
            throw new BusinessException(ResultCode.INVALID_TOKEN);
        }

        // 黑名单检查
        String jti = jwtTokenProvider.getJtiFromToken(refreshToken);
        if (jti != null && tokenBlacklist.isBlacklisted(jti)) {
            throw new BusinessException(ResultCode.INVALID_TOKEN);
        }

        // 校验 ADMIN 角色
        String role = jwtTokenProvider.getRoleFromToken(refreshToken);
        if (!UserRole.ADMIN.name().equals(role)) {
            throw new BusinessException(ResultCode.ACCESS_DENIED);
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        User user = userRepositoryPort.findById(userId)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));

        String currentRole = user.getRole().name();
        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), currentRole);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), currentRole);

        return AdminRefreshTokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .expiresIn(jwtTokenProvider.getAccessTokenExpirationSeconds())
                .build();
    }

    /**
     * 管理端登出（将 Access Token 和 Refresh Token 的 jti 加入黑名单）
     */
    public void logout(String accessToken, String refreshToken) {
        // accessToken 已经过 JwtAuthenticationFilter 验证，必然有效
        String accessJti = jwtTokenProvider.getJtiFromToken(accessToken);
        if (accessJti != null) {
            Instant accessExpiry = jwtTokenProvider.getExpirationFromToken(accessToken).toInstant();
            tokenBlacklist.blacklist(accessJti, accessExpiry);
        }

        // 黑名单 Refresh Token
        if (refreshToken != null && jwtTokenProvider.validateToken(refreshToken)) {
            String refreshJti = jwtTokenProvider.getJtiFromToken(refreshToken);
            if (refreshJti != null) {
                Instant refreshExpiry = jwtTokenProvider.getExpirationFromToken(refreshToken).toInstant();
                tokenBlacklist.blacklist(refreshJti, refreshExpiry);
            }
        }
    }
}
