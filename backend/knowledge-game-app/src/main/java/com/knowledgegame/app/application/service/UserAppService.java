package com.knowledgegame.app.application.service;

import com.knowledgegame.app.api.assembler.UserAssembler;
import com.knowledgegame.app.api.dto.request.UpdateUserRequest;
import com.knowledgegame.app.api.dto.response.LoginResponse;
import com.knowledgegame.app.api.dto.response.RefreshTokenResponse;
import com.knowledgegame.app.api.dto.response.UserResponse;
import com.knowledgegame.app.application.command.LoginCommand;
import com.knowledgegame.app.application.command.RegisterCommand;
import com.knowledgegame.auth.security.JwtTokenProvider;
import com.knowledgegame.auth.security.SecurityUtils;
import com.knowledgegame.auth.security.TokenBlacklist;
import com.knowledgegame.components.feign.client.FileServiceClient;
import com.knowledgegame.components.feign.dto.FileInfoResponse;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.common.result.Result;
import com.knowledgegame.core.common.result.ResultCode;
import com.knowledgegame.core.domain.model.entity.User;
import com.knowledgegame.core.domain.model.vo.FileRef;
import com.knowledgegame.core.domain.port.outbound.UserRepositoryPort;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 用户应用服务（流程编排 + 事务）
 */
@Service
public class UserAppService {

    private final UserRepositoryPort userRepositoryPort;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklist tokenBlacklist;
    private final FileServiceClient fileServiceClient;


    public UserAppService(UserRepositoryPort userRepositoryPort, PasswordEncoder passwordEncoder,
                          JwtTokenProvider jwtTokenProvider, TokenBlacklist tokenBlacklist,
                          FileServiceClient fileServiceClient) {
        this.userRepositoryPort = userRepositoryPort;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.tokenBlacklist = tokenBlacklist;
        this.fileServiceClient = fileServiceClient;
    }

    /**
     * 用户注册（密码 BCrypt 加密存储）
     */
    @Transactional
    public UserResponse register(RegisterCommand command) {
        userRepositoryPort.findByUsername(command.getUsername()).ifPresent(existing -> {
            throw new BusinessException(ResultCode.DUPLICATE_USERNAME.getCode(),
                    ResultCode.DUPLICATE_USERNAME.getMessage() + ": " + command.getUsername());
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
                .orElseThrow(() -> new BusinessException(ResultCode.BAD_CREDENTIALS));

        if (!passwordEncoder.matches(command.getRawPassword(), user.getPasswordHash())) {
            throw new BusinessException(ResultCode.BAD_CREDENTIALS);
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

        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);

        // 确认用户仍然存在
        User user = userRepositoryPort.findById(userId)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));

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
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND.getCode(),
                        ResultCode.USER_NOT_FOUND.getMessage() + ": " + id));
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
     * 更新用户信息（支持 JsonNullable 三态）
     * <p>
     * nickname 必填，null=不更新；avatarFileId 可清空，三态语义。
     */
    @Transactional
    public UserResponse update(Long id, UpdateUserRequest req) {
        User user = userRepositoryPort.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND.getCode(),
                        ResultCode.USER_NOT_FOUND.getMessage() + ": " + id));

        // 必填字段：null=不更新
        user.updateProfile(req.getNickname());

        // 可清空 FileRef：avatar
        applyFileRefField(req.getAvatarFileId(), "USER_AVATAR",
                user::clearAvatar, user::updateAvatar);

        User saved = userRepositoryPort.save(user);
        return UserAssembler.INSTANCE.toResponse(saved);
    }

    /**
     * 三态分派工具：处理 JsonNullable<Long>（FileRef fileId）字段
     */
    private void applyFileRefField(JsonNullable<Long> fileIdField, String bizType,
                                   Runnable clear, Consumer<FileRef> update) {
        if (fileIdField == null || !fileIdField.isPresent()) {
            return;
        }
        Long fileId = fileIdField.get();
        if (fileId == null) {
            clear.run();
        } else {
            FileRef verified = verifyFileRef(fileId, bizType);
            update.accept(verified);
        }
    }

    private FileRef verifyFileRef(Long fileId, String expectedBizType) {
        if (fileId == null) return null;
        Result<FileInfoResponse> result = fileServiceClient.getFileInfo(fileId);
        FileInfoResponse info = result.getData();
        if (info == null) throw new BusinessException(400, "文件不存在: " + fileId);
        Map<String, Object> metadata = info.getMetadata();
        if (metadata == null || !expectedBizType.equals(metadata.get("bizType")))
            throw new BusinessException(400, "文件类型不匹配，期望 " + expectedBizType);
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Object metaUserId = metadata.get("userId");
        Long metaUserIdLong = metaUserId instanceof Number ? ((Number) metaUserId).longValue() : null;
        if (!Objects.equals(currentUserId, metaUserIdLong))
            throw new BusinessException(403, "无权使用该文件");
        return FileRef.of(fileId, info.getUrl());
    }

    /**
     * 删除用户
     */
    @Transactional
    public void deleteUser(Long id) {
        userRepositoryPort.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND.getCode(),
                        ResultCode.USER_NOT_FOUND.getMessage() + ": " + id));
        userRepositoryPort.deleteById(id);
    }

    /**
     * 用户登出（将 Access Token 和 Refresh Token 的 jti 加入黑名单）
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
