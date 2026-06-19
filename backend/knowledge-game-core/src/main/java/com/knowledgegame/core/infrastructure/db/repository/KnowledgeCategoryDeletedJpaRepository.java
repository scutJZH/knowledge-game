package com.knowledgegame.core.infrastructure.db.repository;

import com.knowledgegame.core.infrastructure.db.entity.KnowledgeCategoryDeletedPO;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 知识分类删除快照 Spring Data JPA Repository（REQ-107 对接时使用，本需求仅声明）
 */
public interface KnowledgeCategoryDeletedJpaRepository extends JpaRepository<KnowledgeCategoryDeletedPO, Long> {
}
