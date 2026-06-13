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
     * 根据 IP 系列 ID 和编码查询（编码在同一 IP 系列下唯一）
     */
    Optional<CardTemplatePO> findByIpSeriesIdAndCode(Long ipSeriesId, String code);
}
