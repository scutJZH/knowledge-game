package com.knowledgegame.app.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegame.app.api.dto.request.LoginRequest;
import com.knowledgegame.app.api.dto.request.LogoutRequest;
import com.knowledgegame.app.api.dto.request.RefreshTokenRequest;
import com.knowledgegame.app.api.dto.request.RegisterRequest;
import com.knowledgegame.app.api.dto.request.UpdateUserRequest;
import com.knowledgegame.app.api.dto.response.LoginResponse;
import com.knowledgegame.app.api.dto.response.RefreshTokenResponse;
import com.knowledgegame.app.api.dto.response.UserResponse;
import com.knowledgegame.app.application.command.LoginCommand;
import com.knowledgegame.app.application.command.RegisterCommand;
import com.knowledgegame.app.application.service.UserAppService;
import com.knowledgegame.components.exception.handler.GlobalExceptionHandler;
import com.knowledgegame.core.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UserController 单元测试（禁用 Spring Security Filter，专注测试 Controller 逻辑）
 */
@WebMvcTest(controllers = UserController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;

    @org.springframework.beans.factory.annotation.Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserAppService userAppService;

    // ==================== 注册接口 ====================

    @Test
    @DisplayName("POST /api/users/register - 正常注册返回 200")
    void register_success() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("testuser");
        request.setPassword("123456");
        request.setNickname("测试用户");

        UserResponse response = UserResponse.builder()
                .id(1L)
                .username("testuser")
                .nickname("测试用户")
                .avatarUrl(null)
                .role("USER")
                .build();
        given(userAppService.register(any(RegisterCommand.class))).willReturn(response);

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.username").value("testuser"))
                .andExpect(jsonPath("$.data.nickname").value("测试用户"))
                .andExpect(jsonPath("$.data.role").value("USER"));

        ArgumentCaptor<RegisterCommand> captor = ArgumentCaptor.forClass(RegisterCommand.class);
        verify(userAppService).register(captor.capture());
        RegisterCommand captured = captor.getValue();
        assertThat(captured.getUsername()).isEqualTo("testuser");
        assertThat(captured.getRawPassword()).isEqualTo("123456");
        assertThat(captured.getNickname()).isEqualTo("测试用户");
    }

    @Test
    @DisplayName("POST /api/users/register - 用户名已存在返回 400")
    void register_duplicateUsername_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("duplicate");
        request.setPassword("123456");
        request.setNickname("重复用户");

        willThrow(new BusinessException("用户名已存在: duplicate"))
                .given(userAppService).register(any(RegisterCommand.class));

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("用户名已存在: duplicate"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("POST /api/users/register - 用户名为空时校验失败返回 400")
    void register_blankUsername_validationFails() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("");
        request.setPassword("123456");
        request.setNickname("昵称");

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ==================== 登录接口 ====================

    @Test
    @DisplayName("POST /api/users/login - 正常登录返回双令牌")
    void login_success() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("123456");

        LoginResponse loginResponse = LoginResponse.builder()
                .accessToken("access.token.value")
                .refreshToken("refresh.token.value")
                .expiresIn(1800)
                .user(UserResponse.builder()
                        .id(1L).username("testuser").nickname("测试用户").role("USER").build())
                .build();
        given(userAppService.login(any(LoginCommand.class))).willReturn(loginResponse);

        mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").value("access.token.value"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh.token.value"))
                .andExpect(jsonPath("$.data.expiresIn").value(1800))
                .andExpect(jsonPath("$.data.user.id").value(1))
                .andExpect(jsonPath("$.data.user.username").value("testuser"))
                .andExpect(jsonPath("$.data.user.role").value("USER"));

        ArgumentCaptor<LoginCommand> captor = ArgumentCaptor.forClass(LoginCommand.class);
        verify(userAppService).login(captor.capture());
        LoginCommand captured = captor.getValue();
        assertThat(captured.getUsername()).isEqualTo("testuser");
        assertThat(captured.getRawPassword()).isEqualTo("123456");
    }

    @Test
    @DisplayName("POST /api/users/login - 用户名或密码错误返回 400")
    void login_wrongCredentials_returns400() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("wrongpassword");

        willThrow(new BusinessException("用户名或密码错误"))
                .given(userAppService).login(any(LoginCommand.class));

        mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    @DisplayName("POST /api/users/login - 密码为空时校验失败")
    void login_blankPassword_validationFails() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("");

        mockMvc.perform(post("/api/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ==================== 刷新令牌接口 ====================

    @Test
    @DisplayName("POST /api/users/refresh-token - 正常刷新返回新令牌")
    void refreshToken_success() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("valid.refresh.token");

        RefreshTokenResponse refreshResponse = RefreshTokenResponse.builder()
                .accessToken("new.access.token")
                .refreshToken("new.refresh.token")
                .expiresIn(1800)
                .build();
        given(userAppService.refreshToken("valid.refresh.token")).willReturn(refreshResponse);

        mockMvc.perform(post("/api/users/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").value("new.access.token"))
                .andExpect(jsonPath("$.data.refreshToken").value("new.refresh.token"))
                .andExpect(jsonPath("$.data.expiresIn").value(1800));

        verify(userAppService).refreshToken("valid.refresh.token");
    }

    @Test
    @DisplayName("POST /api/users/refresh-token - 无效 Token 返回 400")
    void refreshToken_invalid_returns400() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("invalid.token");

        willThrow(new BusinessException("Token 无效或已过期"))
                .given(userAppService).refreshToken("invalid.token");

        mockMvc.perform(post("/api/users/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Token 无效或已过期"));
    }

    // ==================== 查询接口 ====================

    @Test
    @DisplayName("GET /api/users/{id} - 正常查询用户详情")
    void getById_success() throws Exception {
        UserResponse response = UserResponse.builder()
                .id(1L)
                .username("testuser")
                .nickname("测试用户")
                .avatarUrl("avatar.png")
                .role("USER")
                .build();
        given(userAppService.getUserById(1L)).willReturn(response);

        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.username").value("testuser"))
                .andExpect(jsonPath("$.data.avatarUrl").value("avatar.png"));

        verify(userAppService).getUserById(1L);
    }

    @Test
    @DisplayName("GET /api/users - 列表查询返回所有用户")
    void list_success() throws Exception {
        UserResponse user1 = UserResponse.builder()
                .id(1L).username("user1").nickname("用户一").role("USER").build();
        UserResponse user2 = UserResponse.builder()
                .id(2L).username("user2").nickname("用户二").role("USER").build();
        given(userAppService.listUsers()).willReturn(List.of(user1, user2));

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].username").value("user1"))
                .andExpect(jsonPath("$.data[1].username").value("user2"));

        verify(userAppService).listUsers();
    }

    // ==================== 更新接口 ====================

    @Test
    @DisplayName("PUT /api/users/{id} - 更新用户信息")
    void update_success() throws Exception {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setNickname("新昵称");
        request.setAvatarFileId(1L);

        UserResponse response = UserResponse.builder()
                .id(1L)
                .username("testuser")
                .nickname("新昵称")
                .avatarUrl("new_avatar.png")
                .role("USER")
                .build();
        given(userAppService.updateUser(eq(1L), eq("新昵称"), eq(1L)))
                .willReturn(response);

        mockMvc.perform(put("/api/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.nickname").value("新昵称"))
                .andExpect(jsonPath("$.data.avatarUrl").value("new_avatar.png"));

        verify(userAppService).updateUser(1L, "新昵称", 1L);
    }

    // ==================== 删除接口 ====================

    @Test
    @DisplayName("DELETE /api/users/{id} - 删除用户")
    void delete_success() throws Exception {
        willDoNothing().given(userAppService).deleteUser(1L);

        mockMvc.perform(delete("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data").isEmpty());

        verify(userAppService).deleteUser(1L);
    }

    @Test
    @DisplayName("DELETE /api/users/{id} - 删除不存在的用户返回 400")
    void delete_notFound_returns400() throws Exception {
        willThrow(new BusinessException("用户不存在: 999"))
                .given(userAppService).deleteUser(999L);

        mockMvc.perform(delete("/api/users/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("用户不存在: 999"));
    }

    // ==================== 登出接口 ====================

    @Test
    @DisplayName("POST /api/users/logout - 正常登出返回 200")
    void logout_success() throws Exception {
        LogoutRequest request = new LogoutRequest();
        request.setRefreshToken("refresh.token.value");

        willDoNothing().given(userAppService).logout("access.token.value", "refresh.token.value");

        mockMvc.perform(post("/api/users/logout")
                        .header("Authorization", "Bearer access.token.value")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isEmpty());

        verify(userAppService).logout("access.token.value", "refresh.token.value");
    }

    @Test
    @DisplayName("POST /api/users/logout - refreshToken 为空时校验失败")
    void logout_blankRefreshToken_validationFails() throws Exception {
        LogoutRequest request = new LogoutRequest();
        request.setRefreshToken("");

        mockMvc.perform(post("/api/users/logout")
                        .header("Authorization", "Bearer access.token.value")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }
}
