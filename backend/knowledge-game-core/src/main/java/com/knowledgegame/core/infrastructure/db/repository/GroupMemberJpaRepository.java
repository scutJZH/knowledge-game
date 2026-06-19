package com.knowledgegame.core.infrastructure.db.repository;

import com.knowledgegame.core.infrastructure.db.entity.GroupMemberPO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 群组成员 Spring Data JPA Repository
 */
@Repository
public interface GroupMemberJpaRepository extends JpaRepository<GroupMemberPO, Long> {

    boolean existsByGroupIdAndUserId(Long groupId, Long userId);

    Optional<GroupMemberPO> findByGroupIdAndUserId(Long groupId, Long userId);
}
