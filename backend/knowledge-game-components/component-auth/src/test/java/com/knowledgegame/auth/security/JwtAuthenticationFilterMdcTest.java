package com.knowledgegame.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * JwtAuthenticationFilter MDC 行为单元测试
 * 重点验证 userId 在 MDC 中的注入和清理
 */
class JwtAuthenticationFilterMdcTest {

    private JwtTokenProvider jwtTokenProvider;
    private TokenBlacklist tokenBlacklist;
    private JwtAuthenticationFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = Mockito.mock(JwtTokenProvider.class);
        tokenBlacklist = Mockito.mock(TokenBlacklist.class);
        filter = new JwtAuthenticationFilter(jwtTokenProvider, tokenBlacklist);

        request = Mockito.mock(HttpServletRequest.class);
        response = Mockito.mock(HttpServletResponse.class);
        filterChain = Mockito.mock(FilterChain.class);

        MDC.clear();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("有效 Token 的 MDC 注入")
    class ValidTokenMdcTests {

        @Test
        @DisplayName("有效 access token 认证成功后 MDC 中有 userId")
        void shouldPutUserIdInMdcForValidToken() throws ServletException, IOException {
            String token = "valid.jwt.token";
            when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
            when(jwtTokenProvider.validateToken(token)).thenReturn(true);
            when(jwtTokenProvider.getTypeFromToken(token)).thenReturn("access");
            when(jwtTokenProvider.getJtiFromToken(token)).thenReturn("jti-123");
            when(tokenBlacklist.isBlacklisted("jti-123")).thenReturn(false);
            when(jwtTokenProvider.getUserIdFromToken(token)).thenReturn(42L);
            when(jwtTokenProvider.getUsernameFromToken(token)).thenReturn("admin");
            when(jwtTokenProvider.getRoleFromToken(token)).thenReturn("ADMIN");

            // 在 filterChain 中捕获 MDC 值
            final String[] capturedUserId = {null};
            Mockito.doAnswer(invocation -> {
                capturedUserId[0] = MDC.get("userId");
                return null;
            }).when(filterChain).doFilter(request, response);

            filter.doFilterInternal(request, response, filterChain);

            assertThat(capturedUserId[0]).isEqualTo("42");
        }

        @Test
        @DisplayName("有效 token 认证成功后 SecurityContext 有 authentication")
        void shouldSetAuthenticationForValidToken() throws ServletException, IOException {
            String token = "valid.jwt.token";
            when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
            when(jwtTokenProvider.validateToken(token)).thenReturn(true);
            when(jwtTokenProvider.getTypeFromToken(token)).thenReturn("access");
            when(jwtTokenProvider.getJtiFromToken(token)).thenReturn("jti-123");
            when(tokenBlacklist.isBlacklisted("jti-123")).thenReturn(false);
            when(jwtTokenProvider.getUserIdFromToken(token)).thenReturn(1L);
            when(jwtTokenProvider.getUsernameFromToken(token)).thenReturn("user1");
            when(jwtTokenProvider.getRoleFromToken(token)).thenReturn("USER");

            filter.doFilterInternal(request, response, filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("无效 Token 的 MDC 行为")
    class InvalidTokenMdcTests {

        @Test
        @DisplayName("无 Authorization 头时 MDC 中无 userId")
        void shouldNotPutUserIdWhenNoAuthHeader() throws ServletException, IOException {
            when(request.getHeader("Authorization")).thenReturn(null);

            final String[] capturedUserId = {null};
            Mockito.doAnswer(invocation -> {
                capturedUserId[0] = MDC.get("userId");
                return null;
            }).when(filterChain).doFilter(request, response);

            filter.doFilterInternal(request, response, filterChain);

            assertThat(capturedUserId[0]).isNull();
        }

        @Test
        @DisplayName("无效 token 时 MDC 中无 userId")
        void shouldNotPutUserIdForInvalidToken() throws ServletException, IOException {
            String token = "invalid.jwt.token";
            when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
            when(jwtTokenProvider.validateToken(token)).thenReturn(false);

            final String[] capturedUserId = {null};
            Mockito.doAnswer(invocation -> {
                capturedUserId[0] = MDC.get("userId");
                return null;
            }).when(filterChain).doFilter(request, response);

            filter.doFilterInternal(request, response, filterChain);

            assertThat(capturedUserId[0]).isNull();
        }

        @Test
        @DisplayName("refresh 类型 token 不注入 MDC userId")
        void shouldNotPutUserIdForRefreshToken() throws ServletException, IOException {
            String token = "refresh.jwt.token";
            when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
            when(jwtTokenProvider.validateToken(token)).thenReturn(true);
            when(jwtTokenProvider.getTypeFromToken(token)).thenReturn("refresh");

            final String[] capturedUserId = {null};
            Mockito.doAnswer(invocation -> {
                capturedUserId[0] = MDC.get("userId");
                return null;
            }).when(filterChain).doFilter(request, response);

            filter.doFilterInternal(request, response, filterChain);

            assertThat(capturedUserId[0]).isNull();
        }

        @Test
        @DisplayName("黑名单中的 token 不注入 MDC userId")
        void shouldNotPutUserIdForBlacklistedToken() throws ServletException, IOException {
            String token = "blacklisted.jwt.token";
            when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
            when(jwtTokenProvider.validateToken(token)).thenReturn(true);
            when(jwtTokenProvider.getTypeFromToken(token)).thenReturn("access");
            when(jwtTokenProvider.getJtiFromToken(token)).thenReturn("jti-blacklisted");
            when(tokenBlacklist.isBlacklisted("jti-blacklisted")).thenReturn(true);

            final String[] capturedUserId = {null};
            Mockito.doAnswer(invocation -> {
                capturedUserId[0] = MDC.get("userId");
                return null;
            }).when(filterChain).doFilter(request, response);

            filter.doFilterInternal(request, response, filterChain);

            assertThat(capturedUserId[0]).isNull();
        }
    }

    @Nested
    @DisplayName("MDC 清理")
    class MdcCleanupTests {

        @Test
        @DisplayName("请求完成后 MDC 中 userId 被清理")
        void shouldCleanUserIdAfterRequest() throws ServletException, IOException {
            String token = "valid.jwt.token";
            when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
            when(jwtTokenProvider.validateToken(token)).thenReturn(true);
            when(jwtTokenProvider.getTypeFromToken(token)).thenReturn("access");
            when(jwtTokenProvider.getJtiFromToken(token)).thenReturn("jti-123");
            when(tokenBlacklist.isBlacklisted("jti-123")).thenReturn(false);
            when(jwtTokenProvider.getUserIdFromToken(token)).thenReturn(42L);
            when(jwtTokenProvider.getUsernameFromToken(token)).thenReturn("admin");
            when(jwtTokenProvider.getRoleFromToken(token)).thenReturn("ADMIN");

            filter.doFilterInternal(request, response, filterChain);

            assertThat(MDC.get("userId")).isNull();
        }

        @Test
        @DisplayName("无 token 请求完成后 MDC 中 userId 也被清理")
        void shouldCleanUserIdAfterNoTokenRequest() throws ServletException, IOException {
            when(request.getHeader("Authorization")).thenReturn(null);

            filter.doFilterInternal(request, response, filterChain);

            assertThat(MDC.get("userId")).isNull();
        }

        @Test
        @DisplayName("请求异常时 MDC 中 userId 仍然被清理")
        void shouldCleanUserIdEvenOnException() throws ServletException, IOException {
            String token = "valid.jwt.token";
            when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
            when(jwtTokenProvider.validateToken(token)).thenReturn(true);
            when(jwtTokenProvider.getTypeFromToken(token)).thenReturn("access");
            when(jwtTokenProvider.getJtiFromToken(token)).thenReturn("jti-123");
            when(tokenBlacklist.isBlacklisted("jti-123")).thenReturn(false);
            when(jwtTokenProvider.getUserIdFromToken(token)).thenReturn(42L);
            when(jwtTokenProvider.getUsernameFromToken(token)).thenReturn("admin");
            when(jwtTokenProvider.getRoleFromToken(token)).thenReturn("ADMIN");

            // 模拟 filterChain 抛出异常
            Mockito.doThrow(new RuntimeException("模拟异常"))
                    .when(filterChain).doFilter(request, response);

            try {
                filter.doFilterInternal(request, response, filterChain);
            } catch (RuntimeException ignored) {
                // 预期异常
            }

            assertThat(MDC.get("userId")).isNull();
        }
    }

    @Nested
    @DisplayName("FilterChain 调用")
    class FilterChainCallTests {

        @Test
        @DisplayName("无论 token 是否有效，filterChain.doFilter 都被调用")
        void shouldAlwaysCallFilterChain() throws ServletException, IOException {
            when(request.getHeader("Authorization")).thenReturn(null);

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("有效 token 时 filterChain.doFilter 也被调用")
        void shouldCallFilterChainForValidToken() throws ServletException, IOException {
            String token = "valid.jwt.token";
            when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
            when(jwtTokenProvider.validateToken(token)).thenReturn(true);
            when(jwtTokenProvider.getTypeFromToken(token)).thenReturn("access");
            when(jwtTokenProvider.getJtiFromToken(token)).thenReturn("jti-123");
            when(tokenBlacklist.isBlacklisted("jti-123")).thenReturn(false);
            when(jwtTokenProvider.getUserIdFromToken(token)).thenReturn(1L);
            when(jwtTokenProvider.getUsernameFromToken(token)).thenReturn("user");
            when(jwtTokenProvider.getRoleFromToken(token)).thenReturn("USER");

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }
    }
}
