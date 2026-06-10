package com.knowledgegame.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器（从 Authorization Header 提取 Token → 验证 → 黑名单检查 → 设置 SecurityContext）
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklist tokenBlacklist;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, TokenBlacklist tokenBlacklist) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.tokenBlacklist = tokenBlacklist;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = extractToken(request);
            if (token != null && jwtTokenProvider.validateToken(token)) {
                String type = jwtTokenProvider.getTypeFromToken(token);
                // 只接受 access 类型的 Token 用于认证
                if ("access".equals(type)) {
                    // 黑名单检查（jti 为 null 的旧 Token 直接放行）
                    String jti = jwtTokenProvider.getJtiFromToken(token);
                    if (jti != null && tokenBlacklist.isBlacklisted(jti)) {
                        filterChain.doFilter(request, response);
                        return;
                    }

                    Long userId = jwtTokenProvider.getUserIdFromToken(token);
                    String username = jwtTokenProvider.getUsernameFromToken(token);
                    String role = jwtTokenProvider.getRoleFromToken(token);

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userId, null,
                                    List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                    authentication.setDetails(username);
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    // 认证成功后注入 userId 到 MDC，供日志组件使用
                    MDC.put("userId", String.valueOf(userId));
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            // 清理 MDC 中的 userId，防止线程池复用导致上下文污染
            MDC.remove("userId");
        }
    }

    /**
     * 从请求头提取 Bearer Token
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
