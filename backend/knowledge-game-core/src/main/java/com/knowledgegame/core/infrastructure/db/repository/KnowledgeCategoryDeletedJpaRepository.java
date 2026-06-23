package com.knowledgegame.core.infrastructure.db.repository;

import com.knowledgegame.core.infrastructure.db.entity.KnowledgeCategoryDeletedPO;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 知识分类删除快照 Spring Data JPA Repository（REQ-107 对接回收站）
 */
public interface KnowledgeCategoryDeletedJpaRepository extends JpaRepository<KnowledgeCategoryDeletedPO, Long> {

    Optional<KnowledgeCategoryDeletedPO> findByOriginalId(Long originalId);

    List<KnowledgeCategoryDeletedPO> findAllByOriginalIdIn(List<Long> originalIds);
}
