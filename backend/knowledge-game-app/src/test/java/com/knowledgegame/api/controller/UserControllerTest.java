package com.knowledgegame.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegame.api.dto.request.RegisterRequest;
import com.knowledgegame.api.dto.request.UpdateUserRequest;
import com.knowledgegame.api.dto.response.UserResponse;
import com.knowledgegame.application.command.RegisterCommand;
import com.knowledgegame.application.service.UserAppService;
import com.knowledgegame.common.exception.BusinessException;
import com.knowledgegame.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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
 * UserController 单元测试（MockMvc + @WebMvcTest + Mockito）
 */
@WebMvcTest(controllers = UserController.class)
@Import(GlobalExceptionHandler.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 模拟用户应用服务（Controller 的唯一依赖）
     */
    @MockitoBean
    private UserAppService userAppService;

    // ==================== 注册接口 ====================

    @Test
    @DisplayName("POST /api/users/register - 正常注册返回 200")
    void register_success() throws Exception {
        // 准备请求
        RegisterRequest request = new RegisterRequest();
        request.setUsername("testuser");
        request.setPassword("123456");
        request.setNickname("测试用户");

        // 准备 Mock 返回
        UserResponse response = UserResponse.builder()
                .id(1L)
                .username("testuser")
                .nickname("测试用户")
                .avatar(null)
                .role("USER")
                .build();
        given(userAppService.register(any(RegisterCommand.class))).willReturn(response);

        // 执行并验证
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

        // 验证传入 AppService 的 Command 参数正确
        ArgumentCaptor<RegisterCommand> captor = ArgumentCaptor.forClass(RegisterCommand.class);
        verify(userAppService).register(captor.capture());
        RegisterCommand captured = captor.getValue();
        assertThat(captured.getUsername()).isEqualTo("testuser");
        assertThat(captured.getRawPassword()).isEqualTo("123456");
        assertThat(captured.getNickname()).isEqualTo("测试用户");
    }

    @Test
    @DisplayName("POST /api/users/register - 用户名已存在返回 400（BusinessException）")
    void register_duplicateUsername_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("duplicate");
        request.setPassword("123456");
        request.setNickname("重复用户");

        // 模拟抛出业务异常
        willThrow(new BusinessException("用户名已存在: duplicate"))
                .given(userAppService).register(any(RegisterCommand.class));

        // 执行并验证：GlobalExceptionHandler 捕获 BusinessException 后返回业务错误码
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

        // 参数校验异常由 GlobalExceptionHandler 处理
        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ==================== 查询接口 ====================

    @Test
    @DisplayName("GET /api/users/{id} - 正常查询用户详情")
    void getById_success() throws Exception {
        UserResponse response = UserResponse.builder()
                .id(1L)
                .username("testuser")
                .nickname("测试用户")
                .avatar("avatar.png")
                .role("USER")
                .build();
        given(userAppService.getUserById(1L)).willReturn(response);

        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.username").value("testuser"))
                .andExpect(jsonPath("$.data.avatar").value("avatar.png"));

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
        request.setAvatar("new_avatar.png");

        UserResponse response = UserResponse.builder()
                .id(1L)
                .username("testuser")
                .nickname("新昵称")
                .avatar("new_avatar.png")
                .role("USER")
                .build();
        given(userAppService.updateUser(eq(1L), eq("新昵称"), eq("new_avatar.png")))
                .willReturn(response);

        mockMvc.perform(put("/api/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.nickname").value("新昵称"))
                .andExpect(jsonPath("$.data.avatar").value("new_avatar.png"));

        verify(userAppService).updateUser(1L, "新昵称", "new_avatar.png");
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
    @DisplayName("DELETE /api/users/{id} - 删除不存在的用户返回 400（BusinessException）")
    void delete_notFound_returns400() throws Exception {
        willThrow(new BusinessException("用户不存在: 999"))
                .given(userAppService).deleteUser(999L);

        mockMvc.perform(delete("/api/users/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("用户不存在: 999"));
    }
}
