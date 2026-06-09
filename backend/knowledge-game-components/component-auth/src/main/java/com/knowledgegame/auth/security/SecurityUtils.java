package com.knowledgegame.auth.security;

import com.knowledgegame.core.common.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全上下文工具类（从 SecurityContextHolder 获取当前用户信息）
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * 获取当前用户 ID
     *
     * @return 用户 ID
     * @throws BusinessException 未认证时抛出
     */
    public static Long getCurrentUserId() {
        Authentication authentication = getRequiredAuthentication();
        return (Long) authentication.getPrincipal();
    }

    /**
     * 获取当前用户名
     *
     * @return 用户名
     * @throws BusinessException 未认证时抛出
     */
    public static String getCurrentUsername() {
        Authentication authentication = getRequiredAuthentication();
        return (String) authentication.getDetails();
    }

    /**
     * 获取当前用户角色（去掉 ROLE_ 前缀）
     *
     * @return 角色名称（如 USER、ADMIN）
     * @throws BusinessException 未认证时抛出
     */
    public static String getCurrentUserRole() {
        Authentication authentication = getRequiredAuthentication();
        return authentication.getAuthorities().stream()
                .findFirst()
                .map(authority -> authority.getAuthority().replace("ROLE_", ""))
                .orElseThrow(() -> new BusinessException("未认证"));
    }

    private static Authentication getRequiredAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException("未认证");
        }
        return authentication;
    }

    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * 从 Authorization Header 提取 Bearer Token
     *
     * @param authorization Authorization 请求头的值
     * @return Token 字符串，格式不合法时返回 null
     */
    public static String extractBearerToken(String authorization) {
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
