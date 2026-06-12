package com.knowledgegame.core.infrastructure.db.repository;

import com.knowledgegame.core.infrastructure.db.entity.QuestionCategoryRelationPO;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 题目-分类关联 Spring Data JPA Repository
 */
public interface QuestionCategoryRelationJpaRepository extends JpaRepository<QuestionCategoryRelationPO, Long> {

    /**
     * 根据题目 ID 删除所有关联
     */
    void deleteByQuestionId(Long questionId);

    /**
     * 根据题目 ID 查询关联列表
     */
    List<QuestionCategoryRelationPO> findByQuestionId(Long questionId);
}