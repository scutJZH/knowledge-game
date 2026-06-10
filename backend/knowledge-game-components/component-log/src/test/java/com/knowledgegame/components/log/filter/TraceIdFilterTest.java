package com.knowledgegame.components.log.filter;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TraceIdFilter 单元测试
 */
class TraceIdFilterTest {

    private TraceIdFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new TraceIdFilter();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = new MockFilterChain();
        // 确保每个测试前 MDC 是干净的
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Nested
    @DisplayName("traceId 生成与传递")
    class TraceIdTests {

        @Test
        @DisplayName("入站无 X-Trace-Id 头时，自动生成 UUID 格式的 traceId")
        void shouldGenerateTraceIdWhenHeaderAbsent() throws ServletException, IOException {
            filter.doFilter(request, response, filterChain);

            // 验证响应头返回了 traceId
            String responseTraceId = response.getHeader(TraceIdFilter.TRACE_ID_HEADER);
            assertThat(responseTraceId).isNotNull();
            assertThat(responseTraceId).matches("[0-9a-f]{32}");
        }

        @Test
        @DisplayName("入站有 X-Trace-Id 头时，沿用人站 traceId")
        void shouldReuseIncomingTraceId() throws ServletException, IOException {
            String incomingTraceId = "abc123def456";
            request.addHeader(TraceIdFilter.TRACE_ID_HEADER, incomingTraceId);

            filter.doFilter(request, response, filterChain);

            // 响应头中的 traceId 应该等于入站的 traceId
            assertThat(response.getHeader(TraceIdFilter.TRACE_ID_HEADER))
                    .isEqualTo(incomingTraceId);
        }

        @Test
        @DisplayName("入站 X-Trace-Id 头为空白时，生成新的 traceId")
        void shouldGenerateTraceIdWhenHeaderBlank() throws ServletException, IOException {
            request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "   ");

            filter.doFilter(request, response, filterChain);

            String responseTraceId = response.getHeader(TraceIdFilter.TRACE_ID_HEADER);
            assertThat(responseTraceId).isNotNull();
            // 空白不算有效 traceId，应该生成新的
            assertThat(responseTraceId).matches("[0-9a-f]{32}");
        }
    }

    @Nested
    @DisplayName("MDC 上下文注入")
    class MdcInjectionTests {

        @Test
        @DisplayName("请求处理期间 MDC 注入了 4 个字段")
        void shouldInjectFourMdcFields() throws ServletException, IOException {
            // 用一个自定义 FilterChain 来在请求期间检查 MDC
            final String[] capturedTraceId = {null};
            final String[] capturedRequestId = {null};
            final String[] capturedClientIp = {null};
            final String[] capturedRequestPath = {null};

            MockFilterChain capturingChain = new MockFilterChain();

            filter.doFilter(request, response, (req, res) -> {
                capturedTraceId[0] = MDC.get(TraceIdFilter.MDC_TRACE_ID);
                capturedRequestId[0] = MDC.get(TraceIdFilter.MDC_REQUEST_ID);
                capturedClientIp[0] = MDC.get(TraceIdFilter.MDC_CLIENT_IP);
                capturedRequestPath[0] = MDC.get(TraceIdFilter.MDC_REQUEST_PATH);
            });

            assertThat(capturedTraceId[0]).isNotNull();
            assertThat(capturedRequestId[0]).isNotNull();
            assertThat(capturedClientIp[0]).isNotNull();
            assertThat(capturedRequestPath[0]).isNotNull();
        }

        @Test
        @DisplayName("MDC 中 requestId 是 UUID 格式")
        void shouldInjectUuidRequestId() throws ServletException, IOException {
            final String[] capturedRequestId = {null};

            filter.doFilter(request, response, (req, res) -> {
                capturedRequestId[0] = MDC.get(TraceIdFilter.MDC_REQUEST_ID);
            });

            assertThat(capturedRequestId[0]).matches("[0-9a-f]{32}");
        }

        @Test
        @DisplayName("MDC 中 requestPath 等于请求 URI")
        void shouldInjectRequestPath() throws ServletException, IOException {
            request.setRequestURI("/api/admin/users");
            final String[] capturedRequestPath = {null};

            filter.doFilter(request, response, (req, res) -> {
                capturedRequestPath[0] = MDC.get(TraceIdFilter.MDC_REQUEST_PATH);
            });

            assertThat(capturedRequestPath[0]).isEqualTo("/api/admin/users");
        }

        @Test
        @DisplayName("MDC 中 clientIp 优先取 X-Forwarded-For 头")
        void shouldPreferXForwardedForClientIp() throws ServletException, IOException {
            request.addHeader("X-Forwarded-For", "192.168.1.100, 10.0.0.1");
            request.setRemoteAddr("127.0.0.1");
            final String[] capturedClientIp = {null};

            filter.doFilter(request, response, (req, res) -> {
                capturedClientIp[0] = MDC.get(TraceIdFilter.MDC_CLIENT_IP);
            });

            // X-Forwarded-For 取第一个 IP
            assertThat(capturedClientIp[0]).isEqualTo("192.168.1.100");
        }

        @Test
        @DisplayName("无 X-Forwarded-For 时 clientIp 取 remoteAddr")
        void shouldUseRemoteAddrWhenNoXForwardedFor() throws ServletException, IOException {
            request.setRemoteAddr("10.0.0.5");
            final String[] capturedClientIp = {null};

            filter.doFilter(request, response, (req, res) -> {
                capturedClientIp[0] = MDC.get(TraceIdFilter.MDC_CLIENT_IP);
            });

            assertThat(capturedClientIp[0]).isEqualTo("10.0.0.5");
        }
    }

    @Nested
    @DisplayName("MDC 清理")
    class MdcCleanupTests {

        @Test
        @DisplayName("请求完成后 MDC 中 4 个字段被清理")
        void shouldCleanMdcAfterRequest() throws ServletException, IOException {
            filter.doFilter(request, response, filterChain);

            assertThat(MDC.get(TraceIdFilter.MDC_TRACE_ID)).isNull();
            assertThat(MDC.get(TraceIdFilter.MDC_REQUEST_ID)).isNull();
            assertThat(MDC.get(TraceIdFilter.MDC_CLIENT_IP)).isNull();
            assertThat(MDC.get(TraceIdFilter.MDC_REQUEST_PATH)).isNull();
        }

        @Test
        @DisplayName("请求异常时 MDC 仍然被清理")
        void shouldCleanMdcEvenOnException() throws ServletException, IOException {
            MockFilterChain throwingChain = new MockFilterChain() {
                @Override
                public void doFilter(jakarta.servlet.ServletRequest req,
                                     jakarta.servlet.ServletResponse res) {
                    throw new RuntimeException("模拟异常");
                }
            };

            try {
                filter.doFilter(request, response, throwingChain);
            } catch (RuntimeException ignored) {
                // 预期异常
            }

            assertThat(MDC.get(TraceIdFilter.MDC_TRACE_ID)).isNull();
            assertThat(MDC.get(TraceIdFilter.MDC_REQUEST_ID)).isNull();
            assertThat(MDC.get(TraceIdFilter.MDC_CLIENT_IP)).isNull();
            assertThat(MDC.get(TraceIdFilter.MDC_REQUEST_PATH)).isNull();
        }
    }

    @Nested
    @DisplayName("响应头")
    class ResponseHeaderTests {

        @Test
        @DisplayName("响应头返回 X-Trace-Id")
        void shouldReturnTraceIdInResponseHeader() throws ServletException, IOException {
            String incomingTraceId = "custom-trace-123";
            request.addHeader(TraceIdFilter.TRACE_ID_HEADER, incomingTraceId);

            filter.doFilter(request, response, filterChain);

            assertThat(response.getHeader(TraceIdFilter.TRACE_ID_HEADER))
                    .isEqualTo(incomingTraceId);
        }
    }
}
