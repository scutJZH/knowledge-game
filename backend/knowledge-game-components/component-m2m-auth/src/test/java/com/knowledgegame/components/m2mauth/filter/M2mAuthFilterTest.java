package com.knowledgegame.components.m2mauth.filter;

import com.knowledgegame.components.m2mauth.config.M2mAuthProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
 * M2mAuthFilter 单元测试
 */
@ExtendWith(MockitoExtension.class)
class M2mAuthFilterTest {

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
        keys.put("app", "app-secret-key");
        keys.put("admin", "admin-secret-key");
        properties.setKeys(keys);
        filter = new M2mAuthFilter(properties);
    }

    @Nested
    @DisplayName("非保护路径")
    class NonProtectedPathTests {

        @Test
        @DisplayName("非保护路径直接放行，无需鉴权头")
        void shouldPassForNonProtectedPath() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        }
    }

    @Nested
    @DisplayName("保护路径 - 绑定校验通过")
    class ProtectedPathSuccessTests {

        @Test
        @DisplayName("正确的服务名+密钥绑定，校验通过")
        void shouldPassWithCorrectBinding() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/files/upload");
            request.addHeader("X-Service-Name", "app");
            request.addHeader("X-Service-Key", "app-secret-key");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            // 验证 serviceName 存入 request attribute
            assertThat(request.getAttribute("m2m.serviceName")).isEqualTo("app");
        }

        @Test
        @DisplayName("多个服务的绑定密钥均可通过")
        void shouldPassForMultipleServices() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/files/upload");
            request.addHeader("X-Service-Name", "admin");
            request.addHeader("X-Service-Key", "admin-secret-key");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(request.getAttribute("m2m.serviceName")).isEqualTo("admin");
        }
    }

    @Nested
    @DisplayName("保护路径 - 校验失败")
    class ProtectedPathFailureTests {

        @Test
        @DisplayName("缺少 X-Service-Name 头返回 401")
        void shouldRejectMissingServiceName() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/files/upload");
            request.addHeader("X-Service-Key", "app-secret-key");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
            assertThat(response.getContentAsString()).contains("缺少服务名标识");
            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("缺少 X-Service-Key 头返回 401")
        void shouldRejectMissingServiceKey() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/files/upload");
            request.addHeader("X-Service-Name", "app");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
            assertThat(response.getContentAsString()).contains("缺少服务密钥");
            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("错误密钥返回 401")
        void shouldRejectWrongKey() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/files/upload");
            request.addHeader("X-Service-Name", "app");
            request.addHeader("X-Service-Key", "wrong-key");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
            assertThat(response.getContentAsString()).contains("服务身份验证失败");
            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("合法密钥 + 伪造服务名（冒充攻击）返回 401")
        void shouldRejectImpersonation() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/files/upload");
            // 用 app 的 key 但声称自己是 admin
            request.addHeader("X-Service-Name", "admin");
            request.addHeader("X-Service-Key", "app-secret-key");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
            assertThat(response.getContentAsString()).contains("服务身份验证失败");
            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("未知服务名返回 401")
        void shouldRejectUnknownService() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/files/upload");
            request.addHeader("X-Service-Name", "unknown-service");
            request.addHeader("X-Service-Key", "some-key");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
            verify(filterChain, never()).doFilter(any(), any());
        }
    }

    @Nested
    @DisplayName("边界条件")
    class EdgeCaseTests {

        @Test
        @DisplayName("protectedPaths 为空时所有路径放行")
        void shouldPassWhenNoProtectedPaths() throws ServletException, IOException {
            properties.setProtectedPaths(List.of());

            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/files/upload");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("keys 为空时保护路径一律 401")
        void shouldRejectWhenNoKeys() throws ServletException, IOException {
            properties.setKeys(new HashMap<>());

            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/files/upload");
            request.addHeader("X-Service-Name", "app");
            request.addHeader("X-Service-Key", "app-secret-key");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        }

        @Test
        @DisplayName("401 响应为 JSON 格式")
        void shouldReturnJsonOnUnauthorized() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/files/upload");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getContentType()).startsWith("application/json");
            assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");
        }

        @Test
        @DisplayName("Ant 风格路径匹配正常工作")
        void shouldMatchAntStylePaths() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/api/v1/data");
            request.addHeader("X-Service-Name", "app");
            request.addHeader("X-Service-Key", "app-secret-key");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }
    }
}
