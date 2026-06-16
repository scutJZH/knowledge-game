package com.knowledgegame.app.api.controller;

import com.knowledgegame.app.config.FilePathMapping;
import com.knowledgegame.components.exception.handler.GlobalExceptionHandler;
import com.knowledgegame.components.feign.client.FileServiceClient;
import com.knowledgegame.components.feign.dto.GenerateCredentialRequest;
import com.knowledgegame.core.common.result.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mockStatic;

import org.mockito.ArgumentCaptor;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FileController 单元测试（用户端，禁用 Spring Security Filter）
 */
@WebMvcTest(controllers = FileController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class FileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FileServiceClient fileServiceClient;

    @BeforeEach
    void setUp() {
        // 设置 SecurityContext，模拟已认证用户（userId=1L）
        Authentication auth = new TestingAuthenticationToken(1L, null, "ROLE_USER");
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("GET /api/upload-credential")
    class GetCredentialTests {

        @Test
        @DisplayName("bizType 必填校验（不传 bizType 返回 500）")
        void shouldReturnError_whenBizTypeMissing() throws Exception {
            // MissingServletRequestParameterException 被 GlobalExceptionHandler 当作系统异常处理，返回 500
            mockMvc.perform(get("/api/upload-credential"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500));
        }

        @Test
        @DisplayName("bizType 无映射返回 400（ip-series 不在 app 端映射中）")
        void shouldReturn400_whenBizTypeNotMapped() throws Exception {
            mockMvc.perform(get("/api/upload-credential")
                            .param("bizType", "ip-series"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("不支持的业务类型: ip-series"));
        }

        @Test
        @DisplayName("不同 bizType 无映射均返回 400")
        void shouldReturn400_forVariousBizTypes() throws Exception {
            mockMvc.perform(get("/api/upload-credential")
                            .param("bizType", "avatar"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("不支持的业务类型: avatar"));

            mockMvc.perform(get("/api/upload-credential")
                            .param("bizType", "card-star-image"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("不支持的业务类型: card-star-image"));
        }

        @Test
        @DisplayName("count 默认值验证（不传 count 参数时请求不报参数错误）")
        void shouldAcceptRequest_withoutCount() throws Exception {
            mockMvc.perform(get("/api/upload-credential")
                            .param("bizType", "ip-series"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("传入 count 参数时不报参数错误")
        void shouldAcceptRequest_withCount() throws Exception {
            mockMvc.perform(get("/api/upload-credential")
                            .param("bizType", "ip-series")
                            .param("count", "3"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("未登录调用返回 400（SecurityUtils 抛出未认证异常）")
        void shouldReturn400_whenNotAuthenticated() throws Exception {
            SecurityContextHolder.clearContext();

            mockMvc.perform(get("/api/upload-credential")
                            .param("bizType", "ip-series"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("未认证"));
        }
    }

    /**
     * 使用 mockStatic 模拟 FilePathMapping，测试 Feign 调用成功后的完整流程
     * <p>
     * 当前 FilePathMapping 为静态空 Map，通过 mockStatic 临时覆盖 toBasePath 返回值，
     * 验证 Feign 调用成功后的响应格式。
     */
    @Nested
    @DisplayName("完整流程测试（通过 mockStatic 模拟映射）")
    class FullFlowTests {

        @Test
        @DisplayName("Mock FileServiceClient 返回 token 时，验证 metadata 含 bizType + userId")
        void shouldReturnTokenAndUploadUrl_whenFeignSucceeds() throws Exception {
            try (MockedStatic<FilePathMapping> mockedMapping = mockStatic(FilePathMapping.class)) {
                mockedMapping.when(() -> FilePathMapping.toBasePath("USER_AVATAR")).thenReturn("user-avatar");
                ArgumentCaptor<GenerateCredentialRequest> captor =
                        ArgumentCaptor.forClass(GenerateCredentialRequest.class);
                given(fileServiceClient.generateCredential(captor.capture()))
                        .willReturn(Result.success("test-token-123"));

                mockMvc.perform(get("/api/upload-credential")
                                .param("bizType", "USER_AVATAR"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(200))
                        .andExpect(jsonPath("$.data.token").value("test-token-123"))
                        .andExpect(jsonPath("$.data.uploadUrl").value("http://localhost:8083/api/file/upload"));

                GenerateCredentialRequest req = captor.getValue();
                assertEquals("user-avatar", req.basePath());
                assertEquals("USER_AVATAR", req.metadata().get("bizType"));
                assertEquals(1L, req.metadata().get("userId"));
            }
        }

        @Test
        @DisplayName("count=1 时 uploadUrl 为单文件上传地址，metadata 正确")
        void shouldReturnSingleUploadUrl_whenCountIs1() throws Exception {
            try (MockedStatic<FilePathMapping> mockedMapping = mockStatic(FilePathMapping.class)) {
                mockedMapping.when(() -> FilePathMapping.toBasePath("USER_AVATAR")).thenReturn("user-avatar");
                ArgumentCaptor<GenerateCredentialRequest> captor =
                        ArgumentCaptor.forClass(GenerateCredentialRequest.class);
                given(fileServiceClient.generateCredential(captor.capture()))
                        .willReturn(Result.success("single-token"));

                mockMvc.perform(get("/api/upload-credential")
                                .param("bizType", "USER_AVATAR")
                                .param("count", "1"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.uploadUrl").value("http://localhost:8083/api/file/upload"));

                GenerateCredentialRequest req = captor.getValue();
                assertEquals(1, req.count());
                assertEquals("user-avatar", req.basePath());
            }
        }

        @Test
        @DisplayName("count>1 时 uploadUrl 为批量上传地址，metadata 含正确 bizType")
        void shouldReturnBatchUploadUrl_whenCountGreaterThan1() throws Exception {
            try (MockedStatic<FilePathMapping> mockedMapping = mockStatic(FilePathMapping.class)) {
                mockedMapping.when(() -> FilePathMapping.toBasePath("USER_AVATAR")).thenReturn("user-avatar");
                ArgumentCaptor<GenerateCredentialRequest> captor =
                        ArgumentCaptor.forClass(GenerateCredentialRequest.class);
                given(fileServiceClient.generateCredential(captor.capture()))
                        .willReturn(Result.success("batch-token"));

                mockMvc.perform(get("/api/upload-credential")
                                .param("bizType", "USER_AVATAR")
                                .param("count", "3"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.token").value("batch-token"))
                        .andExpect(jsonPath("$.data.uploadUrl").value("http://localhost:8083/api/file/batch-upload"));

                GenerateCredentialRequest req = captor.getValue();
                assertEquals(3, req.count());
                assertEquals("user-avatar", req.basePath());
                assertEquals("USER_AVATAR", req.metadata().get("bizType"));
            }
        }
    }
}
