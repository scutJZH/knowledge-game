package com.knowledgegame.core.infrastructure.db.repository;

import com.knowledgegame.core.infrastructure.db.entity.PointTransactionPO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 积分流水 Spring Data JPA Repository（基础设施层，仅供 Adapter 使用）
 */
public interface PointTransactionJpaRepository extends JpaRepository<PointTransactionPO, Long>,
        JpaSpecificationExecutor<PointTransactionPO> {
}
