package com.knowledgegame.core.infrastructure.db.repository;

import com.knowledgegame.core.infrastructure.db.entity.QuestionPO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 题目 Spring Data JPA Repository
 */
public interface QuestionJpaRepository extends JpaRepository<QuestionPO, Long>,
        JpaSpecificationExecutor<QuestionPO> {

    /**
     * 批量更新状态
     */
    @Modifying
    @Query("UPDATE QuestionPO q SET q.status = :status, q.updatedAt = CURRENT_TIMESTAMP WHERE q.id IN :ids")
    void batchUpdateStatus(@Param("ids") List<Long> ids, @Param("status") String status);

    /**
     * 根据题目 ID 查询关联的 ACTIVE 分类 ID 列表（过滤已停用分类）
     */
    @Query("SELECT r.categoryId FROM QuestionCategoryRelationPO r " +
            "WHERE r.questionId = :questionId " +
            "AND r.categoryId IN (SELECT c.id FROM KnowledgeCategoryPO c WHERE c.status = 'ACTIVE')")
    List<Long> findActiveCategoryIdsByQuestionId(@Param("questionId") Long questionId);
}