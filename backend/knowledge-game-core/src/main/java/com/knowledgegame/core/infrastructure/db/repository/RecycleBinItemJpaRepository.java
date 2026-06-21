package com.knowledgegame.core.infrastructure.db.repository;

import com.knowledgegame.core.infrastructure.db.entity.RecycleBinItemPO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 回收站总览表 Spring Data JPA Repository
 */
public interface RecycleBinItemJpaRepository extends JpaRepository<RecycleBinItemPO, Long>,
        JpaSpecificationExecutor<RecycleBinItemPO> {

    /**
     * 查询恢复截止时间早于指定时间的过期记录（REQ-101 定时清理用）
     *
     * @param deadline 截止时间
     * @return 过期记录列表
     */
    List<RecycleBinItemPO> findByRestoreDeadlineBefore(LocalDateTime deadline);
}
