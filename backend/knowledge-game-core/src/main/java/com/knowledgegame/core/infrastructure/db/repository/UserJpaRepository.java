package com.knowledgegame.core.infrastructure.db.repository;

import com.knowledgegame.core.infrastructure.db.entity.UserPO;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 用户 JPA Repository（Spring Data JPA）
 */
public interface UserJpaRepository extends JpaRepository<UserPO, Long> {

    Optional<UserPO> findByUsername(String username);
}
