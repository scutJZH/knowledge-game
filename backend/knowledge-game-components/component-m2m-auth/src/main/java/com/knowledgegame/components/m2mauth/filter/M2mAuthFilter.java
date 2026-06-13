package com.knowledgegame.components.m2mauth.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegame.components.m2mauth.config.M2mAuthProperties;
import com.knowledgegame.core.common.result.Result;
import com.knowledgegame.core.common.result.ResultCode;
import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 机机鉴权过滤器
 * <p>
 * 校验 X-Service-Key 是否与自身 apiKey 一致。调用方通过 X-Service-Name 标识身份
 * （可选，仅用于日志），被调用方不关心调用方是谁，只校验密钥是否匹配。
 */
public class M2mAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(M2mAuthFilter.class);

    private static final String HEADER_SERVICE_NAME = "X-Service-Name";
    private static final String HEADER_SERVICE_KEY = "X-Service-Key";
    private static final String ATTR_SERVICE_NAME = "m2m.serviceName";

    private final M2mAuthProperties properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public M2mAuthFilter(M2mAuthProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(@Nonnull HttpServletRequest request,
                                    @Nonnull HttpServletResponse response,
                                    @Nonnull FilterChain filterChain) throws ServletException, IOException {
        String requestPath = request.getRequestURI();

        // 不在保护路径内，直接放行
        if (!isProtectedPath(requestPath)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 简单密钥校验：调用方持有的 key 与被调用方的 apiKey 一致即通过
        String serviceKey = request.getHeader(HEADER_SERVICE_KEY);
        String serviceName = request.getHeader(HEADER_SERVICE_NAME);

        if (!StringUtils.hasText(serviceKey)) {
            log.warn("M2M 鉴权失败：缺少 {} 头，路径={}, 来源IP={}", HEADER_SERVICE_KEY,
                    requestPath, request.getRemoteAddr());
            writeUnauthorized(response, "缺少服务密钥");
            return;
        }

        String expectedKey = properties.getApiKey();
        if (!StringUtils.hasText(expectedKey)) {
            log.error("M2M 鉴权配置错误：服务端未配置 apiKey，路径={}", requestPath);
            writeUnauthorized(response, "服务端密钥未配置");
            return;
        }

        if (!expectedKey.equals(serviceKey)) {
            log.warn("M2M 鉴权失败：密钥不匹配，路径={}, 调用方={}, 来源IP={}",
                    requestPath, serviceName, request.getRemoteAddr());
            writeUnauthorized(response, "服务身份验证失败");
            return;
        }

        // 校验通过，将调用方服务名存入 request attribute（可选，用于日志）
        if (StringUtils.hasText(serviceName)) {
            request.setAttribute(ATTR_SERVICE_NAME, serviceName);
        }
        log.debug("M2M 鉴权通过：调用方={}, 路径={}", serviceName, requestPath);

        filterChain.doFilter(request, response);
    }

    /**
     * 判断请求路径是否在保护范围内
     */
    private boolean isProtectedPath(String requestPath) {
        List<String> protectedPaths = properties.getProtectedPaths();
        if (protectedPaths == null || protectedPaths.isEmpty()) {
            return false;
        }
        for (String pattern : protectedPaths) {
            if (pathMatcher.match(pattern, requestPath)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 写入 401 JSON 响应
     */
    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Result<Void> result = Result.fail(ResultCode.UNAUTHORIZED.getCode(), message);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
