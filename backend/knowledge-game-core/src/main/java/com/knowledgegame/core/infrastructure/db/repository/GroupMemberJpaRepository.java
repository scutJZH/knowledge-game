package com.knowledgegame.core.infrastructure.db.repository;

import com.knowledgegame.core.infrastructure.db.entity.GroupMemberPO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 群组成员 Spring Data JPA Repository
 */
@Repository
public interface GroupMemberJpaRepository extends JpaRepository<GroupMemberPO, Long> {

    boolean existsByGroupIdAndUserId(Long groupId, Long userId);

    Optional<GroupMemberPO> findByGroupIdAndUserId(Long groupId, Long userId);

    @Modifying
    void deleteByGroupIdAndUserId(Long groupId, Long userId);

    List<GroupMemberPO> findByUserIdOrderByJoinedAtDesc(Long userId);

    @Query("SELECT gm.groupId, COUNT(gm) FROM GroupMemberPO gm WHERE gm.groupId IN :ids GROUP BY gm.groupId")
    List<Object[]> countByGroupIdIn(@Param("ids") List<Long> groupIds);
}
