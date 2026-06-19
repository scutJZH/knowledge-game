package com.knowledgegame.core.infrastructure.db.repository;

import com.knowledgegame.core.infrastructure.db.entity.StudyGroupPO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 学习群组 Spring Data JPA Repository
 */
@Repository
public interface StudyGroupJpaRepository extends JpaRepository<StudyGroupPO, Long> {
}
