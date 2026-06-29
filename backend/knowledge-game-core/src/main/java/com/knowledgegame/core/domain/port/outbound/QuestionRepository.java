package com.knowledgegame.core.domain.port.outbound;

import com.knowledgegame.core.domain.model.domainenum.QuestionStatus;
import com.knowledgegame.core.domain.model.domainenum.QuestionType;
import com.knowledgegame.core.domain.model.entity.Question;
import com.knowledgegame.core.domain.model.vo.PageResult;
import com.knowledgegame.core.domain.model.vo.SortField;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 题目仓储出端口（领域层定义，基础设施层实现）
 */
public interface QuestionRepository {

    /**
     * 保存题目
     */
    Question save(Question question);

    /**
     * 根据 ID 查询
     */
    Optional<Question> findById(Long id);

    /**
     * 分页查询（支持多条件筛选 + 排序）
     */
    PageResult<Question> findByConditions(String keyword, QuestionType type, Integer difficulty,
                                           Long categoryId, String tag, QuestionStatus status,
                                           SortField sortField, int pageNumber, int pageSize);

    /**
     * 批量查询 ID 列表对应的题目
     */
    List<Question> findByIds(List<Long> ids);

    /**
     * 保存分类关联（全量替换）
     */
    void saveCategoryRelations(Long questionId, List<Long> categoryIds);

    /**
     * 查询题目关联的分类 ID 列表（过滤 INACTIVE 分类）
     */
    List<Long> findActiveCategoryIdsByQuestionId(Long questionId);

    /**
     * 查询题目关联的全部分类 ID 列表（含 INACTIVE）
     */
    List<Long> findCategoryIdsByQuestionId(Long questionId);

    /**
     * 统计与指定分类关联的 ACTIVE 题目数量
     */
    long countActiveByCategoryId(Long categoryId);

    /**
     * 统计与指定分类关联的全部题目数量（含 INACTIVE）
     */
    long countByCategoryId(Long categoryId);

    /**
     * 查询多道题目关联的全部分类 ID（去重），用于批量启用前的分类状态校验
     * 返回 Map<questionId, List<categoryId>>
     */
    Map<Long, List<Long>> findCategoryIdsByQuestionIds(List<Long> questionIds);

    /**
     * 批量更新状态
     */
    void batchUpdateStatus(List<Long> ids, QuestionStatus status);
}
