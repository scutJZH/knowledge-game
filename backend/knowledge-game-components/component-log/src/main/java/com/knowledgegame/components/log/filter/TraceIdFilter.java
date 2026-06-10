package com.knowledgegame.components.log.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.UUID;

/**
 * TraceId 过滤器：为每个请求注入 MDC 上下文，打印请求入口/出口日志
 */
public class TraceIdFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);

    /** TraceId 请求头名称 */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /** MDC 字段名 */
    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_REQUEST_ID = "requestId";
    public static final String MDC_CLIENT_IP = "clientIp";
    public static final String MDC_REQUEST_PATH = "requestPath";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        try {
            // 注入 MDC 上下文
            injectMdc(httpRequest);
            // 响应头返回 traceId
            httpResponse.setHeader(TRACE_ID_HEADER, MDC.get(MDC_TRACE_ID));

            // 打印请求入口日志
            log.info("请求进入: {} {} clientIp={}",
                    httpRequest.getMethod(),
                    httpRequest.getRequestURI(),
                    MDC.get(MDC_CLIENT_IP));

            long startTime = System.currentTimeMillis();
            try {
                chain.doFilter(request, response);
            } finally {
                // 出口日志在 finally 中，确保即使未捕获异常也能打印
                long duration = System.currentTimeMillis() - startTime;
                log.info("请求完成: {} {} status={} duration={}ms",
                        httpRequest.getMethod(),
                        httpRequest.getRequestURI(),
                        httpResponse.getStatus(),
                        duration);
            }
        } finally {
            // 清理 MDC，防止线程池复用导致上下文污染
            MDC.remove(MDC_TRACE_ID);
            MDC.remove(MDC_REQUEST_ID);
            MDC.remove(MDC_CLIENT_IP);
            MDC.remove(MDC_REQUEST_PATH);
        }
    }

    /**
     * 注入 MDC 上下文字段
     */
    private void injectMdc(HttpServletRequest request) {
        // traceId：入站有则沿用，无则生成
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        MDC.put(MDC_TRACE_ID, traceId);

        // requestId：每次请求唯一
        MDC.put(MDC_REQUEST_ID, UUID.randomUUID().toString().replace("-", ""));

        // clientIp：优先从代理头获取
        MDC.put(MDC_CLIENT_IP, getClientIp(request));

        // requestPath
        MDC.put(MDC_REQUEST_PATH, request.getRequestURI());
    }

    /**
     * 获取客户端真实 IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        } else {
            // X-Forwarded-For 可能含多个 IP，取第一个
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
