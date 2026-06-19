package com.knowledgegame.core.infrastructure.db.repository;

import com.knowledgegame.core.infrastructure.db.entity.CardTemplateDeletedPO;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 卡牌模板删除快照 Spring Data JPA Repository（REQ-105 对接时使用，本需求仅声明）
 */
public interface CardTemplateDeletedJpaRepository extends JpaRepository<CardTemplateDeletedPO, Long> {
}
