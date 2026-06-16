package com.knowledgegame.core.infrastructure.db.repository;

import com.knowledgegame.core.domain.model.domainenum.CardTemplateStatus;
import com.knowledgegame.core.infrastructure.db.entity.CardTemplatePO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
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

    /**
     * 统计指定 IP 系列下的 ACTIVE 卡牌模板数量
     */
    @Query("SELECT COUNT(c) FROM CardTemplatePO c WHERE c.ipSeriesId = :ipSeriesId AND c.status = 'ACTIVE'")
    long countActiveByIpSeriesId(@Param("ipSeriesId") Long ipSeriesId);

    /**
     * 批量更新卡牌模板状态
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE CardTemplatePO c SET c.status = :status, c.updatedAt = CURRENT_TIMESTAMP WHERE c.id IN :ids")
    void batchUpdateStatus(@Param("ids") List<Long> ids, @Param("status") CardTemplateStatus status);
}
