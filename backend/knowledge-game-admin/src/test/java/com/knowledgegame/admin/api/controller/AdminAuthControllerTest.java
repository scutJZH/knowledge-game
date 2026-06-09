package com.knowledgegame.admin.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegame.admin.api.dto.request.AdminLoginRequest;
import com.knowledgegame.admin.api.dto.request.AdminRefreshTokenRequest;
import com.knowledgegame.admin.api.dto.request.LogoutRequest;
import com.knowledgegame.admin.api.dto.response.AdminLoginResponse;
import com.knowledgegame.admin.api.dto.response.AdminRefreshTokenResponse;
import com.knowledgegame.admin.api.dto.response.AdminUserResponse;
import com.knowledgegame.admin.application.service.AdminLoginAppService;
import com.knowledgegame.admin.common.exception.GlobalExceptionHandler;
import com.knowledgegame.core.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AdminAuthController 单元测试（禁用 Spring Security Filter，专注测试 Controller 逻辑）
 */
@WebMvcTest(controllers = AdminAuthController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminLoginAppService adminLoginAppService;

    // ==================== 登录接口 ====================

    @Test
    @DisplayName("POST /api/admin/login - ADMIN 登录成功返回双令牌")
    void login_success() throws Exception {
        AdminLoginRequest request = new AdminLoginRequest();
        request.setUsername("admin");
        request.setPassword("123456");

        AdminLoginResponse response = AdminLoginResponse.builder()
                .accessToken("access.token")
                .refreshToken("refresh.token")
                .expiresIn(1800)
                .user(AdminUserResponse.builder()
                        .id(1L).username("admin").nickname("管理员").role("ADMIN").build())
                .build();
        given(adminLoginAppService.login("admin", "123456")).willReturn(response);

        mockMvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").value("access.token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh.token"))
                .andExpect(jsonPath("$.data.expiresIn").value(1800))
                .andExpect(jsonPath("$.data.user.id").value(1))
                .andExpect(jsonPath("$.data.user.username").value("admin"))
                .andExpect(jsonPath("$.data.user.role").value("ADMIN"));

        verify(adminLoginAppService).login("admin", "123456");
    }

    @Test
    @DisplayName("POST /api/admin/login - 非 ADMIN 用户返回 400")
    void login_nonAdmin_returns400() throws Exception {
        AdminLoginRequest request = new AdminLoginRequest();
        request.setUsername("user");
        request.setPassword("123456");

        willThrow(new BusinessException("无管理员权限"))
                .given(adminLoginAppService).login("user", "123456");

        mockMvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("无管理员权限"));
    }

    @Test
    @DisplayName("POST /api/admin/login - 用户名为空校验失败")
    void login_blankUsername_validationFails() throws Exception {
        AdminLoginRequest request = new AdminLoginRequest();
        request.setUsername("");
        request.setPassword("123456");

        mockMvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ==================== 刷新令牌接口 ====================

    @Test
    @DisplayName("POST /api/admin/refresh-token - 正常刷新返回新令牌")
    void refreshToken_success() throws Exception {
        AdminRefreshTokenRequest request = new AdminRefreshTokenRequest();
        request.setRefreshToken("valid.refresh.token");

        AdminRefreshTokenResponse response = AdminRefreshTokenResponse.builder()
                .accessToken("new.access.token")
                .refreshToken("new.refresh.token")
                .expiresIn(1800)
                .build();
        given(adminLoginAppService.refreshToken("valid.refresh.token")).willReturn(response);

        mockMvc.perform(post("/api/admin/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").value("new.access.token"))
                .andExpect(jsonPath("$.data.refreshToken").value("new.refresh.token"));

        verify(adminLoginAppService).refreshToken("valid.refresh.token");
    }

    @Test
    @DisplayName("POST /api/admin/refresh-token - USER 角色 Token 返回 400")
    void refreshToken_userRole_returns400() throws Exception {
        AdminRefreshTokenRequest request = new AdminRefreshTokenRequest();
        request.setRefreshToken("user.refresh.token");

        willThrow(new BusinessException("无管理员权限"))
                .given(adminLoginAppService).refreshToken("user.refresh.token");

        mockMvc.perform(post("/api/admin/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("无管理员权限"));
    }

    // ==================== 登出接口 ====================

    @Test
    @DisplayName("POST /api/admin/logout - 正常登出返回 200")
    void logout_success() throws Exception {
        LogoutRequest request = new LogoutRequest();
        request.setRefreshToken("refresh.token");

        willDoNothing().given(adminLoginAppService).logout("access.token", "refresh.token");

        mockMvc.perform(post("/api/admin/logout")
                        .header("Authorization", "Bearer access.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isEmpty());

        verify(adminLoginAppService).logout("access.token", "refresh.token");
    }
}
