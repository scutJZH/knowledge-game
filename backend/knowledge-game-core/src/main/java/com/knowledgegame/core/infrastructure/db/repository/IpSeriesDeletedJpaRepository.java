package com.knowledgegame.core.infrastructure.db.repository;

import com.knowledgegame.core.infrastructure.db.entity.IpSeriesDeletedPO;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * IP 系列删除快照 Spring Data JPA Repository（REQ-104 对接时使用，本需求仅声明）
 */
public interface IpSeriesDeletedJpaRepository extends JpaRepository<IpSeriesDeletedPO, Long> {
}
