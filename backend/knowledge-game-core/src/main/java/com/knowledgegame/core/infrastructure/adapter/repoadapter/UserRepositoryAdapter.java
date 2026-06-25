package com.knowledgegame.core.infrastructure.adapter.repoadapter;

import com.knowledgegame.core.domain.model.entity.User;
import com.knowledgegame.core.domain.port.outbound.UserRepositoryPort;
import com.knowledgegame.core.infrastructure.db.converter.UserConverter;
import com.knowledgegame.core.infrastructure.db.entity.UserPO;
import com.knowledgegame.core.infrastructure.db.repository.UserJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 用户仓储适配器（实现领域层出端口，注入 JPA Repository）
 */
@Repository
public class UserRepositoryAdapter implements UserRepositoryPort {

    private final UserJpaRepository userJpaRepository;

    public UserRepositoryAdapter(UserJpaRepository userJpaRepository) {
        this.userJpaRepository = userJpaRepository;
    }

    @Override
    public User save(User user) {
        // 新用户无 id，创建 PO
        if (user.getId() == null) {
            UserPO po = UserConverter.toPO(user);
            UserPO saved = userJpaRepository.save(po);
            User result = UserConverter.toDomain(saved);
            return result;
        }
        // 已有用户，查找并更新
        UserPO existing = userJpaRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + user.getId()));
        UserConverter.updatePO(existing, user);
        UserPO saved = userJpaRepository.save(existing);
        return UserConverter.toDomain(saved);
    }

    @Override
    public Optional<User> findById(Long id) {
        return userJpaRepository.findById(id).map(UserConverter::toDomain);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return userJpaRepository.findByUsername(username).map(UserConverter::toDomain);
    }

    @Override
    public void deleteById(Long id) {
        userJpaRepository.deleteById(id);
    }

    @Override
    public List<User> findAll() {
        return userJpaRepository.findAll().stream()
                .map(UserConverter::toDomain)
                .toList();
    }

    @Override
    public List<User> findByIdIn(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return userJpaRepository.findAllById(ids).stream()
                .map(UserConverter::toDomain)
                .toList();
    }
}
