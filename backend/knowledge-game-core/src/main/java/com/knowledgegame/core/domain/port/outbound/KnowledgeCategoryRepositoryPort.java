package com.knowledgegame.core.domain.port.outbound;

import com.knowledgegame.core.domain.model.domainenum.KnowledgeCategoryStatus;
import com.knowledgegame.core.domain.model.entity.KnowledgeCategory;
import com.knowledgegame.core.domain.model.vo.PageResult;

import java.util.List;
import java.util.Optional;

/**
 * 知识点分类仓储出端口（领域层定义，基础设施层实现）
 */
public interface KnowledgeCategoryRepositoryPort {

    /**
     * 保存知识点分类
     */
    KnowledgeCategory save(KnowledgeCategory category);

    /**
     * 根据 ID 查询
     */
    Optional<KnowledgeCategory> findById(Long id);

    /**
     * 查询同一父级下是否存在同名分类（包含 INACTIVE）
     */
    boolean existsByNameAndParentId(String name, Long parentId);

    /**
     * 分页查询（支持名称搜索 + 状态筛选 + 父级筛选）
     */
    PageResult<KnowledgeCategory> findByConditions(String keyword, KnowledgeCategoryStatus status,
                                                    Long parentId, int pageNumber, int pageSize);

    /**
     * 查询所有分类（用于构建树）
     */
    List<KnowledgeCategory> findAll();

    /**
     * 查询指定分类的所有后代分类 ID（递归）
     */
    List<Long> findDescendantIds(Long parentId);

    /**
     * 统计指定父级下的 ACTIVE 子分类数量
     */
    long countActiveByParentId(Long parentId);

    /**
     * 根据 ID 批量查询分类（用于批量启用时的状态判定）
     */
    List<KnowledgeCategory> findAllByIdIn(List<Long> ids);

    /**
     * 查询指定父级下的最大排序号
     */
    Integer findMaxSortOrderByParentId(Long parentId);

    /**
     * 查询顶级分类的最大排序号
     */
    Integer findMaxSortOrderForRoot();
}
