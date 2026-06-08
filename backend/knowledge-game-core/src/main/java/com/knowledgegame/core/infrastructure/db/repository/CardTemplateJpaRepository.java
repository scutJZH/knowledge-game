package com.knowledgegame.core.infrastructure.db.repository;

import com.knowledgegame.core.infrastructure.db.entity.CardTemplatePO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * 卡牌模板 Spring Data JPA Repository
 */
public interface CardTemplateJpaRepository extends JpaRepository<CardTemplatePO, Long>,
        JpaSpecificationExecutor<CardTemplatePO> {

    /**
     * 根据 code 查询
     */
    Optional<CardTemplatePO> findByCode(String code);
}
