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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * M2mAuthFilter 独立单元测试
 * 以独立测试工程师视角，全面覆盖 PRD 验收标准中的所有行为规格
 */
@ExtendWith(MockitoExtension.class)
class IndependentM2mAuthFilterTest {

    /** JSON 解析器，用于验证 401 响应 body 的 code/message 字段 */
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
        Map<String, String> keys = new HashMap<>();
        keys.put("app-service", "app-valid-key-001");
        keys.put("admin-service", "admin-valid-key-002");
        keys.put("notification-service", "notify-key-003");
        properties.setKeys(keys);
        filter = new M2mAuthFilter(properties);
    }

    // ==================== 非保护路径放行 ====================

    @Nested
    @DisplayName("规格3：非保护路径直接放行")
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
        @DisplayName("健康检查端点直接放行")
        void shouldPassHealthEndpoint() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("根路径直接放行")
        void shouldPassRootPath() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
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
            // 携带无效的鉴权头
            request.addHeader("X-Service-Name", "fake");
            request.addHeader("X-Service-Key", "invalid");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }
    }

    // ==================== 保护路径校验通过 ====================

    @Nested
    @DisplayName("规格4-5：保护路径校验通过，serviceName 存入 attribute")
    class ProtectedPathSuccessTests {

        @Test
        @DisplayName("正确的服务名+密钥绑定校验通过")
        void shouldPassWithCorrectBinding() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/files/upload");
            request.addHeader("X-Service-Name", "app-service");
            request.addHeader("X-Service-Key", "app-valid-key-001");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        }

        @Test
        @DisplayName("校验通过后 serviceName 存入 request.attribute")
        void shouldStoreServiceNameInAttribute() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/data/sync");
            request.addHeader("X-Service-Name", "admin-service");
            request.addHeader("X-Service-Key", "admin-valid-key-002");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            // 验证 attribute 名称为 m2m.serviceName，值为请求头中的服务名
            assertThat(request.getAttribute("m2m.serviceName")).isEqualTo("admin-service");
        }

        @Test
        @DisplayName("多个注册服务均可独立通过校验")
        void shouldPassForAllRegisteredServices() throws ServletException, IOException {
            // notification-service 的正确凭据
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/events/push");
            request.addHeader("X-Service-Name", "notification-service");
            request.addHeader("X-Service-Key", "notify-key-003");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(request.getAttribute("m2m.serviceName")).isEqualTo("notification-service");
        }

        @Test
        @DisplayName("GET 请求保护路径同样需要校验")
        void shouldAuthenticateGetRequests() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/config");
            request.addHeader("X-Service-Name", "app-service");
            request.addHeader("X-Service-Key", "app-valid-key-001");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }
    }

    // ==================== 保护路径校验失败 ====================

    @Nested
    @DisplayName("规格4：保护路径校验失败 → 401 JSON")
    class ProtectedPathFailureTests {

        @Test
        @DisplayName("缺少 X-Service-Name 头 → 401，message='缺少服务名标识'")
        void shouldRejectMissingServiceNameHeader() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/files/upload");
            // 只发送 Key，不发送 Name
            request.addHeader("X-Service-Key", "app-valid-key-001");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
            assertJsonBody(response, 401, "缺少服务名标识");
            // 校验失败不应继续 filterChain
            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("X-Service-Name 为空字符串 → 401")
        void shouldRejectBlankServiceNameHeader() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/files/upload");
            request.addHeader("X-Service-Name", "");
            request.addHeader("X-Service-Key", "app-valid-key-001");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
            assertJsonBody(response, 401, "缺少服务名标识");
            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("X-Service-Name 为纯空格 → 401")
        void shouldRejectWhitespaceOnlyServiceName() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/files/upload");
            request.addHeader("X-Service-Name", "   ");
            request.addHeader("X-Service-Key", "app-valid-key-001");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("缺少 X-Service-Key 头 → 401，message='缺少服务密钥'")
        void shouldRejectMissingServiceKeyHeader() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/files/upload");
            request.addHeader("X-Service-Name", "app-service");
            // 不发送 Key
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
            request.addHeader("X-Service-Name", "app-service");
            request.addHeader("X-Service-Key", "");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
            assertJsonBody(response, 401, "缺少服务密钥");
            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("X-Service-Key 为纯空格 → 401")
        void shouldRejectWhitespaceOnlyServiceKey() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/files/upload");
            request.addHeader("X-Service-Name", "app-service");
            request.addHeader("X-Service-Key", "   ");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("两个头都缺失 → 401（先校验 serviceName）")
        void shouldRejectWhenBothHeadersMissing() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/files/upload");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
            // 缺少 serviceName 应先被检测
            assertJsonBody(response, 401, "缺少服务名标识");
            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("服务名未知 → 401，message='服务身份验证失败'")
        void shouldRejectUnknownServiceName() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/files/upload");
            request.addHeader("X-Service-Name", "unknown-service");
            request.addHeader("X-Service-Key", "some-key");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
            assertJsonBody(response, 401, "服务身份验证失败");
            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("密钥不匹配 → 401，message='服务身份验证失败'")
        void shouldRejectWrongKey() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/files/upload");
            request.addHeader("X-Service-Name", "app-service");
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
            request.addHeader("X-Service-Name", "app-service");
            request.addHeader("X-Service-Key", "APP-VALID-KEY-001");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
            assertJsonBody(response, 401, "服务身份验证失败");
        }
    }

    // ==================== 冒充防护 ====================

    @Nested
    @DisplayName("规格4：冒充攻击防护（合法 Key + 伪造 Service-Name）")
    class ImpersonationPreventionTests {

        @Test
        @DisplayName("用 app-service 的 Key 冒充 admin-service → 401")
        void shouldRejectAppKeyImpersonatingAdmin() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/files/upload");
            request.addHeader("X-Service-Name", "admin-service");
            request.addHeader("X-Service-Key", "app-valid-key-001"); // app 的 key
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
            assertJsonBody(response, 401, "服务身份验证失败");
            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("用 admin-service 的 Key 冒充 app-service → 401")
        void shouldRejectAdminKeyImpersonatingApp() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/files/upload");
            request.addHeader("X-Service-Name", "app-service");
            request.addHeader("X-Service-Key", "admin-valid-key-002"); // admin 的 key
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
            assertJsonBody(response, 401, "服务身份验证失败");
            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("用 notification-service 的 Key 冒充 app-service → 401")
        void shouldRejectNotifyKeyImpersonatingApp() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/events/push");
            request.addHeader("X-Service-Name", "app-service");
            request.addHeader("X-Service-Key", "notify-key-003");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
            assertJsonBody(response, 401, "服务身份验证失败");
        }
    }

    // ==================== 401 响应格式 ====================

    @Nested
    @DisplayName("规格6：401 响应格式验证")
    class UnauthorizedResponseFormatTests {

        @Test
        @DisplayName("Content-Type 为 application/json;charset=UTF-8")
        void shouldReturnJsonContentType() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/files/upload");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
            // MockHttpServletResponse 会将 contentType 和 characterEncoding 合并返回
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

        @Test
        @DisplayName("401 body 中 code 字段为整数 401")
        void shouldReturnCodeFieldAsInteger401() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/files/upload");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            JsonNode json = objectMapper.readTree(response.getContentAsString());
            assertThat(json.get("code").asInt()).isEqualTo(401);
        }

        @ParameterizedTest(name = "失败场景 {0} → message=''{1}''")
        @CsvSource({
                "缺少服务名, 缺少服务名标识",
                "缺少服务密钥, 缺少服务密钥",
                "身份验证失败, 服务身份验证失败"
        })
        void shouldReturnCorrectMessageForEachFailure(String scenario, String expectedMessage) throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/files/upload");
            switch (scenario) {
                case "缺少服务名" -> {
                    // 不设置任何头
                }
                case "缺少服务密钥" -> {
                    request.addHeader("X-Service-Name", "app-service");
                }
                case "身份验证失败" -> {
                    request.addHeader("X-Service-Name", "app-service");
                    request.addHeader("X-Service-Key", "wrong");
                }
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
    @DisplayName("规格2：AntPathMatcher 路径匹配")
    class AntPathMatchingTests {

        @Test
        @DisplayName("精确路径 /internal/data 匹配 /internal/**")
        void shouldMatchExactPathUnderInternal() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/data");
            // 无鉴权头
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            // 应被拦截（保护路径内）
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
        @DisplayName("/internal 同级路径 /internalOther 不匹配 /internal/**")
        void shouldNotMatchSiblingPath() throws ServletException, IOException {
            // /internalOther 不是 /internal/** 的子路径
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internalOther");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            // 应放行（不在保护路径内）
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("多个 protectedPaths 模式均可匹配")
        void shouldMatchMultiplePathPatterns() throws ServletException, IOException {
            // 配置多个路径模式
            properties.setProtectedPaths(List.of("/internal/**", "/admin/internal/**"));

            // 测试第二个模式
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/internal/config");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        }

        @Test
        @DisplayName("单星号通配符路径 /secret/* 匹配一层子路径")
        void shouldMatchSingleAsteriskPattern() throws ServletException, IOException {
            properties.setProtectedPaths(List.of("/secret/*"));

            // 一层子路径应匹配
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/secret/data");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        }

        @Test
        @DisplayName("单星号通配符 /secret/* 不匹配多层子路径 /secret/a/b")
        void shouldNotMatchDeepPathWithSingleAsterisk() throws ServletException, IOException {
            properties.setProtectedPaths(List.of("/secret/*"));

            // 多层子路径不应匹配
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/secret/a/b");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }
    }

    // ==================== 边界条件 ====================

    @Nested
    @DisplayName("边界条件和异常配置")
    class EdgeCaseTests {

        @Test
        @DisplayName("keys 为空 Map 时保护路径一律 401")
        void shouldRejectAllWhenKeysEmpty() throws ServletException, IOException {
            properties.setKeys(new HashMap<>());

            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/data");
            request.addHeader("X-Service-Name", "app-service");
            request.addHeader("X-Service-Key", "app-valid-key-001");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
            assertJsonBody(response, 401, "服务身份验证失败");
        }

        @Test
        @DisplayName("protectedPaths 为空列表时所有路径放行")
        void shouldPassAllWhenProtectedPathsEmpty() throws ServletException, IOException {
            properties.setProtectedPaths(List.of());

            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/secret");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("protectedPaths 为 null 时所有路径放行")
        void shouldPassAllWhenProtectedPathsNull() throws ServletException, IOException {
            properties.setProtectedPaths(null);

            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/secret");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("密钥包含特殊字符时绑定校验仍正确")
        void shouldHandleSpecialCharactersInKey() throws ServletException, IOException {
            Map<String, String> keys = new HashMap<>();
            keys.put("special-service", "key-with-!@#$%^&*()");
            properties.setKeys(keys);

            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/test");
            request.addHeader("X-Service-Name", "special-service");
            request.addHeader("X-Service-Key", "key-with-!@#$%^&*()");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(request.getAttribute("m2m.serviceName")).isEqualTo("special-service");
        }

        @Test
        @DisplayName("服务名大小写敏感")
        void shouldBeCaseSensitiveOnServiceName() throws ServletException, IOException {
            // 配置中是 app-service，请求用 APP-SERVICE
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/data");
            request.addHeader("X-Service-Name", "APP-SERVICE");
            request.addHeader("X-Service-Key", "app-valid-key-001");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            // 大小写不同应校验失败
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        }

        @Test
        @DisplayName("Filter 可复用 — 多次调用互不影响")
        void shouldBeStatelessAcrossRequests() throws ServletException, IOException {
            // 第一次请求：校验通过
            MockHttpServletRequest request1 = new MockHttpServletRequest("POST", "/internal/data");
            request1.addHeader("X-Service-Name", "app-service");
            request1.addHeader("X-Service-Key", "app-valid-key-001");
            MockHttpServletResponse response1 = new MockHttpServletResponse();
            filter.doFilterInternal(request1, response1, filterChain);
            assertThat(response1.getStatus()).isEqualTo(HttpServletResponse.SC_OK);

            // 第二次请求：冒充攻击
            MockHttpServletRequest request2 = new MockHttpServletRequest("POST", "/internal/data");
            request2.addHeader("X-Service-Name", "admin-service");
            request2.addHeader("X-Service-Key", "app-valid-key-001");
            MockHttpServletResponse response2 = new MockHttpServletResponse();
            filter.doFilterInternal(request2, response2, filterChain);
            assertThat(response2.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);

            // 第三次请求：缺少头
            MockHttpServletRequest request3 = new MockHttpServletRequest("POST", "/internal/data");
            MockHttpServletResponse response3 = new MockHttpServletResponse();
            filter.doFilterInternal(request3, response3, filterChain);
            assertThat(response3.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);

            // 第四次请求：非保护路径
            MockHttpServletRequest request4 = new MockHttpServletRequest("GET", "/api/public");
            MockHttpServletResponse response4 = new MockHttpServletResponse();
            filter.doFilterInternal(request4, response4, filterChain);
            assertThat(response4.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 断言 401 响应的 JSON body 包含预期的 code 和 message
     */
    private void assertJsonBody(MockHttpServletResponse response, int expectedCode, String expectedMessage) throws IOException {
        JsonNode json = objectMapper.readTree(response.getContentAsString());
        assertThat(json.get("code").asInt()).isEqualTo(expectedCode);
        assertThat(json.get("message").asText()).isEqualTo(expectedMessage);
    }
}
