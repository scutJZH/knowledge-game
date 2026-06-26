package com.knowledgegame.core.infrastructure.db.repository;

import com.knowledgegame.core.infrastructure.db.entity.KnowledgeItemDeletedPO;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 知识条目删除快照 Spring Data JPA Repository
 */
public interface KnowledgeItemDeletedJpaRepository extends JpaRepository<KnowledgeItemDeletedPO, Long> {

    /**
     * 根据原表 ID 查询快照
     */
    Optional<KnowledgeItemDeletedPO> findByOriginalId(Long originalId);

    /**
     * 批量查询快照（定时清理用）
     */
    List<KnowledgeItemDeletedPO> findAllByOriginalIdIn(List<Long> originalIds);
}
