package com.knowledgegame.core.domain.port.outbound;

import com.knowledgegame.core.domain.model.domainenum.KnowledgeItemStatus;
import com.knowledgegame.core.domain.model.entity.KnowledgeItem;
import com.knowledgegame.core.domain.model.vo.KnowledgeItemSummary;
import com.knowledgegame.core.domain.model.vo.PageResult;
import com.knowledgegame.core.domain.model.vo.SortField;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 知识条目仓储出端口（领域层定义，基础设施层实现）
 */
public interface KnowledgeItemRepository {

    /**
     * 保存知识条目
     */
    KnowledgeItem save(KnowledgeItem item);

    /**
     * 根据 ID 查询
     */
    Optional<KnowledgeItem> findById(Long id);

    /**
     * 分页查询（支持多条件筛选 + 排序）
     */
    PageResult<KnowledgeItem> findByConditions(String keyword, Long categoryId, String tag,
                                                KnowledgeItemStatus status, SortField sortField,
                                                int pageNumber, int pageSize);

    /**
     * 分页查询摘要（不含正文 content/contentHtml，支持多条件筛选 + 排序）
     */
    PageResult<KnowledgeItemSummary> findByConditionsSummary(String keyword, Long categoryId, String tag,
                                                              KnowledgeItemStatus status, SortField sortField,
                                                              int pageNumber, int pageSize);

    /**
     * 批量查询 ID 列表对应的条目
     */
    List<KnowledgeItem> findByIds(List<Long> ids);

    /**
     * 保存分类关联（全量替换）
     */
    void saveCategoryRelations(Long itemId, List<Long> categoryIds);

    /**
     * 查询条目关联的分类 ID 列表（过滤 INACTIVE 分类）
     */
    List<Long> findActiveCategoryIdsByItemId(Long itemId);

    /**
     * 查询条目关联的全部分类 ID 列表（含 INACTIVE）
     */
    List<Long> findCategoryIdsByItemId(Long itemId);

    /**
     * 统计与指定分类关联的 ACTIVE 知识条目数量
     */
    long countActiveByCategoryId(Long categoryId);

    /**
     * 统计与指定分类关联的全部知识条目数量（含 INACTIVE）
     */
    long countByCategoryId(Long categoryId);

    /**
     * 查询多个条目关联的全部 ACTIVE 分类 ID，用于列表响应组装
     * 返回 Map<itemId, List<categoryId>>（仅含 ACTIVE 分类）
     */
    Map<Long, List<Long>> findActiveCategoryIdsByItemIds(List<Long> itemIds);

    /**
     * 查询多个条目关联的全部分类 ID（含 INACTIVE），用于批量启用前的分类状态校验
     * 返回 Map<itemId, List<categoryId>>
     */
    Map<Long, List<Long>> findCategoryIdsByItemIds(List<Long> itemIds);

    /**
     * 批量更新状态
     */
    void batchUpdateStatus(List<Long> ids, KnowledgeItemStatus status);
}
