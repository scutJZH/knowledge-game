package com.knowledgegame.domain.port.outbound;

import com.knowledgegame.domain.model.entity.User;

import java.util.List;
import java.util.Optional;

/**
 * 用户仓储出端口（领域层定义，基础设施层实现）
 */
public interface UserRepositoryPort {

    User save(User user);

    Optional<User> findById(Long id);

    Optional<User> findByUsername(String username);

    void deleteById(Long id);

    List<User> findAll();
}
