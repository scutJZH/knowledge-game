package com.knowledgegame.app.application.service;

import com.knowledgegame.app.api.assembler.UserAssembler;
import com.knowledgegame.app.api.dto.response.UserResponse;
import com.knowledgegame.app.application.command.RegisterCommand;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.entity.User;
import com.knowledgegame.core.domain.port.outbound.UserRepositoryPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户应用服务（流程编排 + 事务）
 */
@Service
public class UserAppService {

    private final UserRepositoryPort userRepositoryPort;
    private final PasswordEncoder passwordEncoder;

    public UserAppService(UserRepositoryPort userRepositoryPort, PasswordEncoder passwordEncoder) {
        this.userRepositoryPort = userRepositoryPort;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 用户注册（密码 BCrypt 加密存储）
     */
    @Transactional
    public UserResponse register(RegisterCommand command) {
        userRepositoryPort.findByUsername(command.getUsername()).ifPresent(existing -> {
            throw new BusinessException("用户名已存在: " + command.getUsername());
        });
        String encodedPassword = passwordEncoder.encode(command.getRawPassword());
        User user = User.create(command.getUsername(), encodedPassword, command.getNickname());
        User saved = userRepositoryPort.save(user);
        return UserAssembler.INSTANCE.toResponse(saved);
    }

    /**
     * 根据 ID 查询用户
     */
    public UserResponse getUserById(Long id) {
        User user = userRepositoryPort.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在: " + id));
        return UserAssembler.INSTANCE.toResponse(user);
    }

    /**
     * 查询所有用户
     */
    public List<UserResponse> listUsers() {
        return userRepositoryPort.findAll().stream()
                .map(UserAssembler.INSTANCE::toResponse)
                .toList();
    }

    /**
     * 更新用户信息
     */
    @Transactional
    public UserResponse updateUser(Long id, String nickname, String avatar) {
        User user = userRepositoryPort.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在: " + id));
        user.updateProfile(nickname, avatar);
        User saved = userRepositoryPort.save(user);
        return UserAssembler.INSTANCE.toResponse(saved);
    }

    /**
     * 删除用户
     */
    @Transactional
    public void deleteUser(Long id) {
        userRepositoryPort.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在: " + id));
        userRepositoryPort.deleteById(id);
    }
}
