package com.knowledgegame.core.infrastructure.db.repository;

import com.knowledgegame.core.infrastructure.db.entity.QuestionCategoryRelationPO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * 统计与指定分类关联的 ACTIVE 题目数量
     */
    @Query("""
           SELECT COUNT(DISTINCT r.questionId) FROM QuestionCategoryRelationPO r
           JOIN QuestionPO q ON r.questionId = q.id
           WHERE r.categoryId = :categoryId AND q.status = 'ACTIVE'
           """)
    long countActiveQuestionsByCategoryId(@Param("categoryId") Long categoryId);

    /**
     * 根据题目 ID 列表查询所有关联
     */
    @Query("SELECT r FROM QuestionCategoryRelationPO r WHERE r.questionId IN :questionIds")
    List<QuestionCategoryRelationPO> findAllByQuestionIdIn(@Param("questionIds") List<Long> questionIds);
}
