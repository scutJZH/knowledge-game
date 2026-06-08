package com.knowledgegame.app.application.service;

import com.knowledgegame.app.api.assembler.UserAssembler;
import com.knowledgegame.app.api.dto.response.LoginResponse;
import com.knowledgegame.app.api.dto.response.RefreshTokenResponse;
import com.knowledgegame.app.api.dto.response.UserResponse;
import com.knowledgegame.app.application.command.LoginCommand;
import com.knowledgegame.app.application.command.RegisterCommand;
import com.knowledgegame.auth.security.JwtTokenProvider;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.entity.User;
import com.knowledgegame.core.domain.port.outbound.UserRepositoryPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户应用服务（流程编排 + 事务）
 */
@Service
public class UserAppService {

    private final UserRepositoryPort userRepositoryPort;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public UserAppService(UserRepositoryPort userRepositoryPort, PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider) {
        this.userRepositoryPort = userRepositoryPort;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * 用户注册（密码 BCrypt 加密存储）
     */
    @Transactional
    public UserResponse register(RegisterCommand command) {
        userRepositoryPort.findByUsername(command.getUsername()).ifPresent(existing -> {
            throw new BusinessException("用户名已存在: " + command.getUsername());
        });
        String encodedPassword = passwordEncoder.encode(command.getRawPassword());
        User user = User.create(command.getUsername(), encodedPassword, command.getNickname());
        User saved = userRepositoryPort.save(user);
        return UserAssembler.INSTANCE.toResponse(saved);
    }

    /**
     * 用户登录（校验密码 → 签发双令牌）
     */
    public LoginResponse login(LoginCommand command) {
        User user = userRepositoryPort.findByUsername(command.getUsername())
                .orElseThrow(() -> new BusinessException("用户名或密码错误"));

        if (!passwordEncoder.matches(command.getRawPassword(), user.getPasswordHash())) {
            throw new BusinessException("用户名或密码错误");
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtTokenProvider.getAccessTokenExpirationSeconds())
                .user(UserAssembler.INSTANCE.toResponse(user))
                .build();
    }

    /**
     * 刷新令牌（验证 Refresh Token → 签发新的双令牌）
     */
    public RefreshTokenResponse refreshToken(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BusinessException("Refresh Token 无效或已过期");
        }

        String type = jwtTokenProvider.getTypeFromToken(refreshToken);
        if (!"refresh".equals(type)) {
            throw new BusinessException("Refresh Token 无效或已过期");
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);

        // 确认用户仍然存在
        User user = userRepositoryPort.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));

        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        return RefreshTokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .expiresIn(jwtTokenProvider.getAccessTokenExpirationSeconds())
                .build();
    }

    /**
     * 根据 ID 查询用户
     */
    public UserResponse getUserById(Long id) {
        User user = userRepositoryPort.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在: " + id));
        return UserAssembler.INSTANCE.toResponse(user);
    }

    /**
     * 查询所有用户
     */
    public List<UserResponse> listUsers() {
        return userRepositoryPort.findAll().stream()
                .map(UserAssembler.INSTANCE::toResponse)
                .toList();
    }

    /**
     * 更新用户信息
     */
    @Transactional
    public UserResponse updateUser(Long id, String nickname, String avatar) {
        User user = userRepositoryPort.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在: " + id));
        user.updateProfile(nickname, avatar);
        User saved = userRepositoryPort.save(user);
        return UserAssembler.INSTANCE.toResponse(saved);
    }

    /**
     * 删除用户
     */
    @Transactional
    public void deleteUser(Long id) {
        userRepositoryPort.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在: " + id));
        userRepositoryPort.deleteById(id);
    }
}
