package com.knowledgegame.core.infrastructure.db.repository;

import com.knowledgegame.core.infrastructure.db.entity.KnowledgeItemCategoryRelationPO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 知识条目-分类关联 Spring Data JPA Repository
 */
public interface KnowledgeItemCategoryRelationJpaRepository
        extends JpaRepository<KnowledgeItemCategoryRelationPO, Long> {

    /**
     * 根据条目 ID 删除所有关联
     */
    void deleteByItemId(Long itemId);

    /**
     * 根据条目 ID 查询关联列表
     */
    List<KnowledgeItemCategoryRelationPO> findByItemId(Long itemId);

    /**
     * 统计与指定分类关联的 ACTIVE 知识条目数量
     */
    @Query("""
           SELECT COUNT(DISTINCT r.itemId) FROM KnowledgeItemCategoryRelationPO r
           JOIN KnowledgeItemPO i ON r.itemId = i.id
           WHERE r.categoryId = :categoryId AND i.status = 'ACTIVE'
           """)
    long countActiveItemsByCategoryId(@Param("categoryId") Long categoryId);

    /**
     * 统计与指定分类关联的全部知识条目数量（含 INACTIVE）
     */
    @Query("""
           SELECT COUNT(DISTINCT r.itemId) FROM KnowledgeItemCategoryRelationPO r
           JOIN KnowledgeItemPO i ON r.itemId = i.id
           WHERE r.categoryId = :categoryId
           """)
    long countItemsByCategoryId(@Param("categoryId") Long categoryId);

    /**
     * 根据条目 ID 列表查询所有关联
     */
    @Query("SELECT r FROM KnowledgeItemCategoryRelationPO r WHERE r.itemId IN :itemIds")
    List<KnowledgeItemCategoryRelationPO> findAllByItemIdIn(@Param("itemIds") List<Long> itemIds);
}
