package com.knowledgegame.core.infrastructure.db.repository;

import com.knowledgegame.core.infrastructure.db.entity.IpSeriesDeletedPO;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * IP 系列删除快照 Spring Data JPA Repository
 */
public interface IpSeriesDeletedJpaRepository extends JpaRepository<IpSeriesDeletedPO, Long> {

    /**
     * 按原始 ID 查询删除快照
     *
     * @param originalId 原始 ip_series 记录的 ID
     * @return 删除快照，不存在返回 empty
     */
    Optional<IpSeriesDeletedPO> findByOriginalId(Long originalId);
}
