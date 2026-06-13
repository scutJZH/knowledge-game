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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * M2mAuthFilter 单元测试
 * <p>
 * 新校验模型：调用方和被调用方持有同一个 apiKey，
 * 被调用方只校验 X-Service-Key == apiKey，不关心调用方身份。
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
        properties.setApiKey("shared-api-key");
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
    @DisplayName("保护路径 - 校验通过")
    class ProtectedPathSuccessTests {

        @Test
        @DisplayName("正确的密钥，校验通过")
        void shouldPassWithCorrectKey() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/files/upload");
            request.addHeader("X-Service-Key", "shared-api-key");
            request.addHeader("X-Service-Name", "knowledge-game-admin");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            // 验证 serviceName 存入 request attribute（用于日志追踪）
            assertThat(request.getAttribute("m2m.serviceName")).isEqualTo("knowledge-game-admin");
        }

        @Test
        @DisplayName("X-Service-Name 可选，不传也能通过")
        void shouldPassWithoutServiceName() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/files/upload");
            request.addHeader("X-Service-Key", "shared-api-key");
            // 不传 X-Service-Name
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        }
    }

    @Nested
    @DisplayName("保护路径 - 校验失败")
    class ProtectedPathFailureTests {

        @Test
        @DisplayName("缺少 X-Service-Key 头返回 401")
        void shouldRejectMissingServiceKey() throws ServletException, IOException {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/files/upload");
            request.addHeader("X-Service-Name", "knowledge-game-admin");
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
            request.addHeader("X-Service-Key", "wrong-key");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
            assertThat(response.getContentAsString()).contains("服务身份验证失败");
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
        @DisplayName("未配置 apiKey 时一律 401")
        void shouldRejectWhenNoApiKey() throws ServletException, IOException {
            properties.setApiKey(null);

            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/files/upload");
            request.addHeader("X-Service-Key", "shared-api-key");
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
            request.addHeader("X-Service-Key", "shared-api-key");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }
    }
}
