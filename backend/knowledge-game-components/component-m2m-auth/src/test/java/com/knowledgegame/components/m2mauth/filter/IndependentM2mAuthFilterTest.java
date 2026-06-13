package com.knowledgegame.components.m2mauth.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegame.components.m2mauth.config.M2mAuthProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * M2mAuthFilter 独立单元测试
 * <p>
 * 新校验模型：被调用方只校验 X-Service-Key == apiKey，X-Service-Name 可选（仅用于日志）。
 * 新增调用方只需在调用方配好被调用方的 key，被调用方无需改动。
 */
@ExtendWith(MockitoExtension.class)
class IndependentM2mAuthFilterTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private M2mAuthFilter filter;

    @Mock
    private FilterChain filterChain;

    private M2mAuthProperties properties;

    @BeforeEach
    void setUp() {
        properties = new M2mAuthProperties();
        properties.setEnabled(true);
        properties.setProtectedPaths(List.of("/internal/**"));
        properties.setApiKey("shared-api-key");
        filter = new M2mAuthFilter(properties);
    }

    // ==================== 非保护路径放行 ====================

    @Nested
    @DisplayName("非保护路径直接放行")
    class NonProtectedPathTests {

        @Test
        @DisplayName("公开 API 路径无需鉴权直接放行")
        void shouldPassPublicApiPath() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        }

        @Test
        @DisplayName("静态资源路径直接放行")
        void shouldPassStaticResources() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/static/app.js");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("非保护路径即使携带无效鉴权头也不拦截")
        void shouldPassEvenWithInvalidAuthHeaders() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/data");
            request.addHeader("X-Service-Key", "wrong-key");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }
    }

    // ==================== 保护路径校验通过 ====================

    @Nested
    @DisplayName("保护路径校验通过")
    class ProtectedPathSuccessTests {

        @Test
        @DisplayName("正确的密钥校验通过")
        void shouldPassWithCorrectKey() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/files/upload");
            request.addHeader("X-Service-Key", "shared-api-key");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        }

        @Test
        @DisplayName("携带 X-Service-Name 时存入 request.attribute（用于日志）")
        void shouldStoreServiceNameInAttributeWhenProvided() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/data/sync");
            request.addHeader("X-Service-Key", "shared-api-key");
            request.addHeader("X-Service-Name", "knowledge-game-admin");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(request.getAttribute("m2m.serviceName")).isEqualTo("knowledge-game-admin");
        }

        @Test
        @DisplayName("X-Service-Name 不传也能通过（可选）")
        void shouldPassWithoutServiceName() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/files/upload");
            request.addHeader("X-Service-Key", "shared-api-key");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            // 未传 X-Service-Name 时 attribute 为 null
            assertThat(request.getAttribute("m2m.serviceName")).isNull();
        }

        @Test
        @DisplayName("GET 请求保护路径同样需要校验")
        void shouldAuthenticateGetRequests() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/config");
            request.addHeader("X-Service-Key", "shared-api-key");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }
    }

    // ==================== 保护路径校验失败 ====================

    @Nested
    @DisplayName("保护路径校验失败 → 401 JSON")
    class ProtectedPathFailureTests {

        @Test
        @DisplayName("缺少 X-Service-Key 头 → 401，message='缺少服务密钥'")
        void shouldRejectMissingServiceKeyHeader() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/files/upload");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
            assertJsonBody(response, 401, "缺少服务密钥");
            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("X-Service-Key 为空字符串 → 401")
        void shouldRejectBlankServiceKeyHeader() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/files/upload");
            request.addHeader("X-Service-Key", "");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
            assertJsonBody(response, 401, "缺少服务密钥");
            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("密钥不匹配 → 401，message='服务身份验证失败'")
        void shouldRejectWrongKey() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/files/upload");
            request.addHeader("X-Service-Key", "wrong-key-value");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
            assertJsonBody(response, 401, "服务身份验证失败");
            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("大小写不同的密钥不匹配 → 401")
        void shouldRejectCaseSensitiveKey() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/files/upload");
            request.addHeader("X-Service-Key", "SHARED-API-KEY");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
            assertJsonBody(response, 401, "服务身份验证失败");
        }
    }

    // ==================== 401 响应格式 ====================

    @Nested
    @DisplayName("401 响应格式验证")
    class UnauthorizedResponseFormatTests {

        @Test
        @DisplayName("Content-Type 为 application/json;charset=UTF-8")
        void shouldReturnJsonContentType() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/files/upload");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
            assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");
            assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");
        }

        @Test
        @DisplayName("401 body 中 data 字段为 null")
        void shouldReturnNullDataField() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/files/upload");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            JsonNode json = objectMapper.readTree(response.getContentAsString());
            assertThat(json.has("data")).isTrue();
            assertThat(json.get("data").isNull()).isTrue();
        }

        @ParameterizedTest(name = "失败场景 {0} → message=''{1}''")
        @CsvSource({
                "缺少服务密钥, 缺少服务密钥",
                "身份验证失败, 服务身份验证失败"
        })
        void shouldReturnCorrectMessageForEachFailure(String scenario, String expectedMessage) throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/files/upload");
            switch (scenario) {
                case "缺少服务密钥" -> {
                    // 不设置任何头
                }
                case "身份验证失败" -> request.addHeader("X-Service-Key", "wrong");
                default -> {}
            }
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            JsonNode json = objectMapper.readTree(response.getContentAsString());
            assertThat(json.get("message").asText()).isEqualTo(expectedMessage);
        }
    }

    // ==================== Ant 风格路径匹配 ====================

    @Nested
    @DisplayName("AntPathMatcher 路径匹配")
    class AntPathMatchingTests {

        @Test
        @DisplayName("精确路径 /internal/data 匹配 /internal/**")
        void shouldMatchExactPathUnderInternal() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/data");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        }

        @Test
        @DisplayName("多层路径 /internal/api/v1/sync 匹配 /internal/**")
        void shouldMatchDeepPathUnderInternal() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/api/v1/sync");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        }

        @Test
        @DisplayName("/internalOther 不匹配 /internal/**")
        void shouldNotMatchSiblingPath() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internalOther");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("多个 protectedPaths 模式均可匹配")
        void shouldMatchMultiplePathPatterns() throws ServletException, IOException {
            properties.setProtectedPaths(List.of("/internal/**", "/admin/internal/**"));

            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/internal/config");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }

    // ==================== 边界条件 ====================

    @Nested
    @DisplayName("边界条件和异常配置")
    class EdgeCaseTests {

        @Test
        @DisplayName("未配置 apiKey 时保护路径一律 401")
        void shouldRejectWhenApiKeyNotConfigured() throws ServletException, IOException {
            properties.setApiKey(null);

            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/data");
            request.addHeader("X-Service-Key", "some-key");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
            assertJsonBody(response, 401, "服务端密钥未配置");
        }

        @Test
        @DisplayName("protectedPaths 为空时所有路径放行")
        void shouldPassAllWhenProtectedPathsEmpty() throws ServletException, IOException {
            properties.setProtectedPaths(List.of());

            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/secret");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("Filter 可复用 — 多次调用互不影响")
        void shouldBeStatelessAcrossRequests() throws ServletException, IOException {
            // 第一次：校验通过
            MockHttpServletRequest r1 = new MockHttpServletRequest("POST", "/internal/a");
            r1.addHeader("X-Service-Key", "shared-api-key");
            MockHttpServletResponse resp1 = new MockHttpServletResponse();
            filter.doFilterInternal(r1, resp1, filterChain);
            assertThat(resp1.getStatus()).isEqualTo(HttpServletResponse.SC_OK);

            // 第二次：错误密钥
            MockHttpServletRequest r2 = new MockHttpServletRequest("POST", "/internal/b");
            r2.addHeader("X-Service-Key", "wrong");
            MockHttpServletResponse resp2 = new MockHttpServletResponse();
            filter.doFilterInternal(r2, resp2, filterChain);
            assertThat(resp2.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);

            // 第三次：缺少密钥头
            MockHttpServletRequest r3 = new MockHttpServletRequest("POST", "/internal/c");
            MockHttpServletResponse resp3 = new MockHttpServletResponse();
            filter.doFilterInternal(r3, resp3, filterChain);
            assertThat(resp3.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);

            // 第四次：非保护路径
            MockHttpServletRequest r4 = new MockHttpServletRequest("GET", "/api/public");
            MockHttpServletResponse resp4 = new MockHttpServletResponse();
            filter.doFilterInternal(r4, resp4, filterChain);
            assertThat(resp4.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        }
    }

    // ==================== 辅助方法 ====================

    private void assertJsonBody(MockHttpServletResponse response, int expectedCode, String expectedMessage) throws IOException {
        JsonNode json = objectMapper.readTree(response.getContentAsString());
        assertThat(json.get("code").asInt()).isEqualTo(expectedCode);
        assertThat(json.get("message").asText()).isEqualTo(expectedMessage);
    }
}
