package com.knowledgegame.core.infrastructure.db.repository;

import com.knowledgegame.core.infrastructure.db.entity.KnowledgeItemDeletedPO;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 知识条目删除快照 Spring Data JPA Repository（REQ-108 对接时使用，本需求仅声明）
 */
public interface KnowledgeItemDeletedJpaRepository extends JpaRepository<KnowledgeItemDeletedPO, Long> {
}
