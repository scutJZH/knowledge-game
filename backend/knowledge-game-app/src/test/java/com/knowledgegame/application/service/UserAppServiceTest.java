package com.knowledgegame.application.service;

import com.knowledgegame.api.dto.response.UserResponse;
import com.knowledgegame.application.command.RegisterCommand;
import com.knowledgegame.common.exception.BusinessException;
import com.knowledgegame.domain.model.domainenum.UserRole;
import com.knowledgegame.domain.model.entity.User;
import com.knowledgegame.domain.port.outbound.UserRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserAppService 单元测试（纯 Mockito，不启动 Spring 上下文）
 */
@ExtendWith(MockitoExtension.class)
class UserAppServiceTest {

    @Mock
    private UserRepositoryPort userRepositoryPort;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserAppService userAppService;

    @BeforeEach
    void setUp() {
        userAppService = new UserAppService(userRepositoryPort, passwordEncoder);
    }

    // ==================== register ====================

    @Nested
    @DisplayName("用户注册")
    class Register {

        @Test
        @DisplayName("正常注册成功，密码被加密")
        void register_success() {
            // 准备命令
            RegisterCommand command = RegisterCommand.builder()
                    .username("testuser")
                    .rawPassword("plainPwd")
                    .nickname("测试昵称")
                    .build();

            // mock: 用户名不存在
            when(userRepositoryPort.findByUsername("testuser")).thenReturn(Optional.empty());
            // mock: 密码加密
            when(passwordEncoder.encode("plainPwd")).thenReturn("encodedPwd");
            // mock: 保存后返回带 id 的用户
            User savedUser = User.reconstruct(1L, "testuser", "encodedPwd", "测试昵称",
                    null, UserRole.USER, LocalDateTime.now(), LocalDateTime.now());
            when(userRepositoryPort.save(any(User.class))).thenReturn(savedUser);

            // 执行
            UserResponse response = userAppService.register(command);

            // 验证返回值
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getUsername()).isEqualTo("testuser");
            assertThat(response.getNickname()).isEqualTo("测试昵称");
            assertThat(response.getRole()).isEqualTo("USER");

            // 验证密码加密被调用
            verify(passwordEncoder).encode("plainPwd");
            // 验证仓储保存被调用
            verify(userRepositoryPort).save(any(User.class));
        }

        @Test
        @DisplayName("用户名已存在时抛出 BusinessException")
        void register_usernameExists_throws() {
            RegisterCommand command = RegisterCommand.builder()
                    .username("duplicate")
                    .rawPassword("anyPwd")
                    .nickname("any")
                    .build();

            // mock: 用户名已存在
            User existingUser = User.reconstruct(1L, "duplicate", "hash", "昵称",
                    null, UserRole.USER, LocalDateTime.now(), LocalDateTime.now());
            when(userRepositoryPort.findByUsername("duplicate")).thenReturn(Optional.of(existingUser));

            // 执行并验证异常
            assertThatThrownBy(() -> userAppService.register(command))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("用户名已存在: duplicate");
        }
    }

    // ==================== getUserById ====================

    @Nested
    @DisplayName("根据 ID 查询用户")
    class GetUserById {

        @Test
        @DisplayName("正常查询返回用户信息")
        void getUserById_success() {
            User user = User.reconstruct(1L, "testuser", "hash", "昵称",
                    "avatar.png", UserRole.USER, LocalDateTime.now(), LocalDateTime.now());
            when(userRepositoryPort.findById(1L)).thenReturn(Optional.of(user));

            UserResponse response = userAppService.getUserById(1L);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getUsername()).isEqualTo("testuser");
            assertThat(response.getNickname()).isEqualTo("昵称");
            assertThat(response.getAvatar()).isEqualTo("avatar.png");
            assertThat(response.getRole()).isEqualTo("USER");
        }

        @Test
        @DisplayName("用户不存在时抛出 BusinessException")
        void getUserById_notFound_throws() {
            when(userRepositoryPort.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userAppService.getUserById(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("用户不存在: 999");
        }
    }

    // ==================== listUsers ====================

    @Nested
    @DisplayName("查询所有用户")
    class ListUsers {

        @Test
        @DisplayName("正常返回用户列表")
        void listUsers_success() {
            User user1 = User.reconstruct(1L, "user1", "hash1", "昵称1",
                    null, UserRole.USER, LocalDateTime.now(), LocalDateTime.now());
            User user2 = User.reconstruct(2L, "user2", "hash2", "昵称2",
                    null, UserRole.USER, LocalDateTime.now(), LocalDateTime.now());
            when(userRepositoryPort.findAll()).thenReturn(List.of(user1, user2));

            List<UserResponse> responses = userAppService.listUsers();

            assertThat(responses).hasSize(2);
            assertThat(responses.get(0).getUsername()).isEqualTo("user1");
            assertThat(responses.get(1).getUsername()).isEqualTo("user2");
        }

        @Test
        @DisplayName("无用户时返回空列表")
        void listUsers_empty() {
            when(userRepositoryPort.findAll()).thenReturn(List.of());

            List<UserResponse> responses = userAppService.listUsers();

            assertThat(responses).isEmpty();
        }
    }

    // ==================== updateUser ====================

    @Nested
    @DisplayName("更新用户信息")
    class UpdateUser {

        @Test
        @DisplayName("正常更新用户昵称和头像")
        void updateUser_success() {
            User originalUser = User.reconstruct(1L, "testuser", "hash", "旧昵称",
                    "oldAvatar.png", UserRole.USER, LocalDateTime.now(), LocalDateTime.now());
            when(userRepositoryPort.findById(1L)).thenReturn(Optional.of(originalUser));

            // 模拟 save 返回更新后的用户
            User updatedUser = User.reconstruct(1L, "testuser", "hash", "新昵称",
                    "newAvatar.png", UserRole.USER, LocalDateTime.now(), LocalDateTime.now());
            when(userRepositoryPort.save(any(User.class))).thenReturn(updatedUser);

            UserResponse response = userAppService.updateUser(1L, "新昵称", "newAvatar.png");

            assertThat(response).isNotNull();
            assertThat(response.getNickname()).isEqualTo("新昵称");
            assertThat(response.getAvatar()).isEqualTo("newAvatar.png");
            // 验证保存被调用
            verify(userRepositoryPort).save(any(User.class));
        }

        @Test
        @DisplayName("更新不存在的用户时抛出 BusinessException")
        void updateUser_notFound_throws() {
            when(userRepositoryPort.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userAppService.updateUser(999L, "昵称", "avatar"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("用户不存在: 999");
        }
    }

    // ==================== deleteUser ====================

    @Nested
    @DisplayName("删除用户")
    class DeleteUser {

        @Test
        @DisplayName("正常删除用户")
        void deleteUser_success() {
            User user = User.reconstruct(1L, "testuser", "hash", "昵称",
                    null, UserRole.USER, LocalDateTime.now(), LocalDateTime.now());
            when(userRepositoryPort.findById(1L)).thenReturn(Optional.of(user));

            userAppService.deleteUser(1L);

            // 验证 deleteById 被调用
            verify(userRepositoryPort).deleteById(1L);
        }

        @Test
        @DisplayName("删除不存在的用户时抛出 BusinessException")
        void deleteUser_notFound_throws() {
            when(userRepositoryPort.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userAppService.deleteUser(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("用户不存在: 999");
        }
    }
}
