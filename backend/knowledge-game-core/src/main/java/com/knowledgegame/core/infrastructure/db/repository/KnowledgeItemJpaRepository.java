package com.knowledgegame.core.infrastructure.db.repository;

import com.knowledgegame.core.domain.model.domainenum.KnowledgeItemStatus;
import com.knowledgegame.core.infrastructure.db.entity.KnowledgeItemPO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 知识条目 Spring Data JPA Repository
 */
public interface KnowledgeItemJpaRepository extends JpaRepository<KnowledgeItemPO, Long>,
        JpaSpecificationExecutor<KnowledgeItemPO> {

    /**
     * 批量更新状态
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE KnowledgeItemPO i SET i.status = :status, i.updatedAt = CURRENT_TIMESTAMP WHERE i.id IN :ids")
    void batchUpdateStatus(@Param("ids") List<Long> ids, @Param("status") KnowledgeItemStatus status);

    /**
     * 根据条目 ID 查询关联的 ACTIVE 分类 ID 列表（过滤已停用分类）
     */
    @Query("SELECT r.categoryId FROM KnowledgeItemCategoryRelationPO r " +
            "WHERE r.itemId = :itemId " +
            "AND r.categoryId IN (SELECT c.id FROM KnowledgeCategoryPO c WHERE c.status = 'ACTIVE')")
    List<Long> findActiveCategoryIdsByItemId(@Param("itemId") Long itemId);

    /**
     * 批量查询多个条目的 ACTIVE 分类 ID（过滤已停用分类）
     * 返回 List<Object[]>，每个 Object[] = [itemId, categoryId]
     */
    @Query("SELECT r.itemId, r.categoryId FROM KnowledgeItemCategoryRelationPO r " +
            "WHERE r.itemId IN :itemIds " +
            "AND r.categoryId IN (SELECT c.id FROM KnowledgeCategoryPO c WHERE c.status = 'ACTIVE')")
    List<Object[]> findActiveCategoryIdsByItemIds(@Param("itemIds") List<Long> itemIds);
}
