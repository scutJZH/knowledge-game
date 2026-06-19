package com.knowledgegame.core.infrastructure.db.repository;

import com.knowledgegame.core.infrastructure.db.entity.QuestionDeletedPO;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 题目删除快照 Spring Data JPA Repository（REQ-106 对接时使用，本需求仅声明）
 */
public interface QuestionDeletedJpaRepository extends JpaRepository<QuestionDeletedPO, Long> {
}
