package com.knowledgegame.admin.application.service;

import com.knowledgegame.admin.api.dto.response.AdminLoginResponse;
import com.knowledgegame.admin.api.dto.response.AdminRefreshTokenResponse;
import com.knowledgegame.auth.security.InMemoryTokenBlacklist;
import com.knowledgegame.auth.security.JwtProperties;
import com.knowledgegame.auth.security.JwtTokenProvider;
import com.knowledgegame.auth.security.TokenBlacklist;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.domainenum.UserRole;
import com.knowledgegame.core.domain.model.entity.User;
import com.knowledgegame.core.domain.port.outbound.UserRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * AdminLoginAppService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class AdminLoginAppServiceTest {

    @Mock
    private UserRepositoryPort userRepositoryPort;

    @Mock
    private PasswordEncoder passwordEncoder;

    private JwtTokenProvider jwtTokenProvider;
    private TokenBlacklist tokenBlacklist;
    private AdminLoginAppService adminLoginAppService;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret("test-secret-key-for-unit-test-must-be-at-least-32-chars");
        jwtProperties.setAccessTokenExpiration(1800000);
        jwtProperties.setRefreshTokenExpiration(604800000);
        jwtTokenProvider = new JwtTokenProvider(jwtProperties);
        tokenBlacklist = new InMemoryTokenBlacklist();
        adminLoginAppService = new AdminLoginAppService(userRepositoryPort, passwordEncoder, jwtTokenProvider, tokenBlacklist);
    }

    // ==================== login ====================

    @Nested
    @DisplayName("管理端登录")
    class Login {

        @Test
        @DisplayName("ADMIN 用户登录成功，返回双令牌 + 用户信息")
        void login_admin_success() {
            User admin = User.reconstruct(1L, "admin", "$2a$10$hash", "管理员",
                    null, UserRole.ADMIN, LocalDateTime.now(), LocalDateTime.now());
            when(userRepositoryPort.findByUsername("admin")).thenReturn(Optional.of(admin));
            when(passwordEncoder.matches("123456", "$2a$10$hash")).thenReturn(true);

            AdminLoginResponse response = adminLoginAppService.login("admin", "123456");

            assertThat(response).isNotNull();
            assertThat(response.getAccessToken()).isNotBlank();
            assertThat(response.getRefreshToken()).isNotBlank();
            assertThat(response.getExpiresIn()).isEqualTo(1800);
            assertThat(response.getUser()).isNotNull();
            assertThat(response.getUser().getId()).isEqualTo(1L);
            assertThat(response.getUser().getUsername()).isEqualTo("admin");
            assertThat(response.getUser().getRole()).isEqualTo("ADMIN");

            // 验证 Token 有效
            assertThat(jwtTokenProvider.validateToken(response.getAccessToken())).isTrue();
            assertThat(jwtTokenProvider.getTypeFromToken(response.getAccessToken())).isEqualTo("access");
            assertThat(jwtTokenProvider.getRoleFromToken(response.getAccessToken())).isEqualTo("ADMIN");
            // 验证 Refresh Token 含 role
            assertThat(jwtTokenProvider.validateToken(response.getRefreshToken())).isTrue();
            assertThat(jwtTokenProvider.getRoleFromToken(response.getRefreshToken())).isEqualTo("ADMIN");
        }

        @Test
        @DisplayName("USER 角色登录被拒绝")
        void login_userRole_rejected() {
            User user = User.reconstruct(2L, "normaluser", "$2a$10$hash", "普通用户",
                    null, UserRole.USER, LocalDateTime.now(), LocalDateTime.now());
            when(userRepositoryPort.findByUsername("normaluser")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("123456", "$2a$10$hash")).thenReturn(true);

            assertThatThrownBy(() -> adminLoginAppService.login("normaluser", "123456"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("无管理员权限");
        }

        @Test
        @DisplayName("用户名不存在时抛出 BusinessException")
        void login_userNotFound_throws() {
            when(userRepositoryPort.findByUsername("nouser")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminLoginAppService.login("nouser", "123456"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("用户名或密码错误");
        }

        @Test
        @DisplayName("密码错误时抛出 BusinessException")
        void login_wrongPassword_throws() {
            User admin = User.reconstruct(1L, "admin", "$2a$10$hash", "管理员",
                    null, UserRole.ADMIN, LocalDateTime.now(), LocalDateTime.now());
            when(userRepositoryPort.findByUsername("admin")).thenReturn(Optional.of(admin));
            when(passwordEncoder.matches("wrongpwd", "$2a$10$hash")).thenReturn(false);

            assertThatThrownBy(() -> adminLoginAppService.login("admin", "wrongpwd"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("用户名或密码错误");
        }
    }

    // ==================== refreshToken ====================

    @Nested
    @DisplayName("管理端刷新令牌")
    class RefreshToken {

        @Test
        @DisplayName("ADMIN 角色刷新成功")
        void refresh_admin_success() {
            User admin = User.reconstruct(1L, "admin", "hash", "管理员",
                    null, UserRole.ADMIN, LocalDateTime.now(), LocalDateTime.now());
            when(userRepositoryPort.findById(1L)).thenReturn(Optional.of(admin));

            // 生成含 ADMIN role 的 Refresh Token
            String refreshToken = jwtTokenProvider.generateRefreshToken(1L, "ADMIN");

            AdminRefreshTokenResponse response = adminLoginAppService.refreshToken(refreshToken);

            assertThat(response).isNotNull();
            assertThat(response.getAccessToken()).isNotBlank();
            assertThat(response.getRefreshToken()).isNotBlank();
            assertThat(response.getExpiresIn()).isEqualTo(1800);
        }

        @Test
        @DisplayName("USER 角色的 Refresh Token 被拒绝")
        void refresh_userRole_rejected() {
            // 生成含 USER role 的 Refresh Token
            String refreshToken = jwtTokenProvider.generateRefreshToken(2L, "USER");

            assertThatThrownBy(() -> adminLoginAppService.refreshToken(refreshToken))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("无管理员权限");
        }

        @Test
        @DisplayName("无效 Token 抛出异常")
        void refresh_invalidToken_throws() {
            assertThatThrownBy(() -> adminLoginAppService.refreshToken("invalid.token"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("Token 无效或已过期");
        }

        @Test
        @DisplayName("Token 有效但用户不存在时抛出 BusinessException")
        void refresh_userNotFound_throws() {
            when(userRepositoryPort.findById(999L)).thenReturn(Optional.empty());

            String refreshToken = jwtTokenProvider.generateRefreshToken(999L, "ADMIN");

            assertThatThrownBy(() -> adminLoginAppService.refreshToken(refreshToken))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("用户不存在");
        }
    }

    // ==================== logout ====================

    @Nested
    @DisplayName("管理端登出")
    class Logout {

        @Test
        @DisplayName("登出后 Access Token 和 Refresh Token 的 jti 均在黑名单中")
        void logout_bothTokensBlacklisted() {
            User admin = User.reconstruct(1L, "admin", "hash", "管理员",
                    null, UserRole.ADMIN, LocalDateTime.now(), LocalDateTime.now());
            when(userRepositoryPort.findByUsername("admin")).thenReturn(Optional.of(admin));
            when(passwordEncoder.matches("123456", "hash")).thenReturn(true);

            // 先登录获取 Token
            AdminLoginResponse loginResponse = adminLoginAppService.login("admin", "123456");

            // 提取 jti
            String accessJti = jwtTokenProvider.getJtiFromToken(loginResponse.getAccessToken());
            String refreshJti = jwtTokenProvider.getJtiFromToken(loginResponse.getRefreshToken());

            // 登出
            adminLoginAppService.logout(loginResponse.getAccessToken(), loginResponse.getRefreshToken());

            // 验证两个 jti 都在黑名单中
            assertThat(tokenBlacklist.isBlacklisted(accessJti)).isTrue();
            assertThat(tokenBlacklist.isBlacklisted(refreshJti)).isTrue();
        }

        @Test
        @DisplayName("登出后原 Token 被黑名单拦截")
        void logout_tokenBlacklisted() {
            User admin = User.reconstruct(1L, "admin", "hash", "管理员",
                    null, UserRole.ADMIN, LocalDateTime.now(), LocalDateTime.now());
            when(userRepositoryPort.findByUsername("admin")).thenReturn(Optional.of(admin));
            when(passwordEncoder.matches("123456", "hash")).thenReturn(true);

            AdminLoginResponse loginResponse = adminLoginAppService.login("admin", "123456");
            adminLoginAppService.logout(loginResponse.getAccessToken(), loginResponse.getRefreshToken());

            String accessJti = jwtTokenProvider.getJtiFromToken(loginResponse.getAccessToken());
            assertThat(tokenBlacklist.isBlacklisted(accessJti)).isTrue();
        }
    }
}
