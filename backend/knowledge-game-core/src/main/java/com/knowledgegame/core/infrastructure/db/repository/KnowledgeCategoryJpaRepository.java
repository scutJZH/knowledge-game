package com.knowledgegame.core.infrastructure.db.repository;

import com.knowledgegame.core.infrastructure.db.entity.KnowledgeCategoryPO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 知识点分类 Spring Data JPA Repository
 */
public interface KnowledgeCategoryJpaRepository extends JpaRepository<KnowledgeCategoryPO, Long>,
        JpaSpecificationExecutor<KnowledgeCategoryPO> {

    /**
     * 查询同一父级下是否存在同名分类
     */
    boolean existsByNameAndParentId(String name, Long parentId);

    /**
     * 递归查询所有后代分类 ID（MySQL 8 WITH RECURSIVE CTE）
     */
    @Query(value = """
            WITH RECURSIVE descendants AS (
                SELECT id FROM knowledge_category WHERE parent_id = :parentId
                UNION ALL
                SELECT kc.id FROM knowledge_category kc
                INNER JOIN descendants d ON kc.parent_id = d.id
            )
            SELECT id FROM descendants
            """, nativeQuery = true)
    List<Long> findDescendantIds(@Param("parentId") Long parentId);
}
