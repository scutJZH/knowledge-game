package com.knowledgegame.core.infrastructure.db.repository;

import com.knowledgegame.core.infrastructure.db.entity.RecycleBinItemPO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 回收站总览表 Spring Data JPA Repository
 */
public interface RecycleBinItemJpaRepository extends JpaRepository<RecycleBinItemPO, Long>,
        JpaSpecificationExecutor<RecycleBinItemPO> {
}
