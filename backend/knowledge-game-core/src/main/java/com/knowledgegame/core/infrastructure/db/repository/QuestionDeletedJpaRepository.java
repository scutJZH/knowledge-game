package com.knowledgegame.core.infrastructure.db.repository;

import com.knowledgegame.core.infrastructure.db.entity.QuestionDeletedPO;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuestionDeletedJpaRepository extends JpaRepository<QuestionDeletedPO, Long> {

    Optional<QuestionDeletedPO> findByOriginalId(Long originalId);

    List<QuestionDeletedPO> findAllByOriginalIdIn(List<Long> originalIds);
}
