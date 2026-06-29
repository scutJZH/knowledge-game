package com.knowledgegame.core.infrastructure.adapter.repoadapter;

import com.knowledgegame.core.domain.model.domainenum.Difficulty;
import com.knowledgegame.core.domain.model.domainenum.QuestionStatus;
import com.knowledgegame.core.domain.model.domainenum.QuestionType;
import com.knowledgegame.core.domain.model.entity.Question;
import com.knowledgegame.core.domain.model.vo.PageResult;
import com.knowledgegame.core.domain.model.vo.SortField;
import com.knowledgegame.core.domain.port.outbound.QuestionRepository;
import com.knowledgegame.core.domain.spec.SortFieldSpec;
import com.knowledgegame.core.infrastructure.adapter.support.SortFields;
import com.knowledgegame.core.infrastructure.db.converter.QuestionConverter;
import com.knowledgegame.core.infrastructure.db.entity.QuestionCategoryRelationPO;
import com.knowledgegame.core.infrastructure.db.entity.QuestionPO;
import com.knowledgegame.core.infrastructure.db.repository.QuestionCategoryRelationJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.QuestionJpaRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 题目仓储适配器（实现领域层出端口）
 */
@Repository
public class QuestionRepositoryAdapter implements QuestionRepository {

    /**
     * 列表查询允许的排序字段白名单（PO 字段名 → 中文显示名，保持插入顺序供错误消息稳定输出）
     */
    private static final Map<String, String> ALLOWED_SORT_FIELDS = new LinkedHashMap<>() {{
        put("id", "ID");
        put("type", "题型");
        put("difficulty", "难度");
        put("createdAt", "创建时间");
        put("updatedAt", "更新时间");
    }};

    private final QuestionJpaRepository questionJpaRepository;
    private final QuestionCategoryRelationJpaRepository relationJpaRepository;

    public QuestionRepositoryAdapter(QuestionJpaRepository questionJpaRepository,
                                      QuestionCategoryRelationJpaRepository relationJpaRepository) {
        this.questionJpaRepository = questionJpaRepository;
        this.relationJpaRepository = relationJpaRepository;
    }

    @Override
    public Question save(Question question) {
        if (question.getId() == null) {
            QuestionPO po = QuestionConverter.INSTANCE.toPO(question);
            QuestionPO saved = questionJpaRepository.save(po);
            return QuestionConverter.INSTANCE.toDomain(saved);
        }
        QuestionPO existing = questionJpaRepository.findById(question.getId())
                .orElseThrow(() -> new IllegalArgumentException("题目不存在: " + question.getId()));
        QuestionConverter.INSTANCE.updatePO(existing, question);
        QuestionPO saved = questionJpaRepository.save(existing);
        return QuestionConverter.INSTANCE.toDomain(saved);
    }

    @Override
    public Optional<Question> findById(Long id) {
        return questionJpaRepository.findById(id).map(QuestionConverter.INSTANCE::toDomain);
    }

    @Override
    public PageResult<Question> findByConditions(String keyword, QuestionType type, Integer difficulty,
                                                   Long categoryId, String tag, QuestionStatus status,
                                                   SortField sortField, int pageNumber, int pageSize) {
        Specification<QuestionPO> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 关键词搜索（题目内容模糊匹配）
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.like(root.get("content"), "%" + keyword + "%"));
            }

            // 题型筛选
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }

            // 难度筛选（前端传入 level 整数，转为枚举比较）
            if (difficulty != null) {
                predicates.add(cb.equal(root.get("difficulty"), Difficulty.fromLevel(difficulty)));
            }

            // 状态筛选
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            // 分类筛选（通过关联表子查询）
            if (categoryId != null) {
                Subquery<Long> subquery = query.subquery(Long.class);
                Root<QuestionCategoryRelationPO> relRoot = subquery.from(QuestionCategoryRelationPO.class);
                subquery.select(relRoot.get("questionId"))
                        .where(cb.equal(relRoot.get("categoryId"), categoryId));
                predicates.add(root.get("id").in(subquery));
            }

            // 标签筛选（JSON_CONTAINS 函数）
            if (tag != null && !tag.isBlank()) {
                predicates.add(cb.isTrue(cb.function(
                        "JSON_CONTAINS", Boolean.class,
                        root.get("tags"),
                        cb.literal("\"" + tag + "\"")
                )));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        // 排序处理
        Sort springSort = toSpringSort(sortField);
        Page<QuestionPO> springPage = questionJpaRepository.findAll(spec,
                PageRequest.of(pageNumber, pageSize, springSort));

        return PageResult.<Question>builder()
                .content(springPage.getContent().stream()
                        .map(QuestionConverter.INSTANCE::toDomain).toList())
                .totalElements(springPage.getTotalElements())
                .pageNumber(springPage.getNumber())
                .pageSize(springPage.getSize())
                .totalPages(springPage.getTotalPages())
                .build();
    }

    @Override
    public List<Question> findByIds(List<Long> ids) {
        return questionJpaRepository.findAllById(ids).stream()
                .map(QuestionConverter.INSTANCE::toDomain)
                .toList();
    }

    @Override
    public void saveCategoryRelations(Long questionId, List<Long> categoryIds) {
        // 先删除旧关联，再批量插入新关联
        relationJpaRepository.deleteByQuestionId(questionId);
        relationJpaRepository.flush();
        if (categoryIds != null && !categoryIds.isEmpty()) {
            List<QuestionCategoryRelationPO> relations = categoryIds.stream()
                    .map(catId -> QuestionCategoryRelationPO.builder()
                            .questionId(questionId)
                            .categoryId(catId)
                            .build())
                    .toList();
            relationJpaRepository.saveAll(relations);
        }
    }

    @Override
    public List<Long> findActiveCategoryIdsByQuestionId(Long questionId) {
        return questionJpaRepository.findActiveCategoryIdsByQuestionId(questionId);
    }

    @Override
    public List<Long> findCategoryIdsByQuestionId(Long questionId) {
        return relationJpaRepository.findByQuestionId(questionId).stream()
                .map(QuestionCategoryRelationPO::getCategoryId)
                .toList();
    }

    @Override
    public long countActiveByCategoryId(Long categoryId) {
        return relationJpaRepository.countActiveQuestionsByCategoryId(categoryId);
    }

    @Override
    public long countByCategoryId(Long categoryId) {
        return relationJpaRepository.countQuestionsByCategoryId(categoryId);
    }

    @Override
    public Map<Long, List<Long>> findCategoryIdsByQuestionIds(List<Long> questionIds) {
        List<QuestionCategoryRelationPO> relations = relationJpaRepository.findAllByQuestionIdIn(questionIds);
        return relations.stream()
                .collect(Collectors.groupingBy(
                        QuestionCategoryRelationPO::getQuestionId,
                        Collectors.mapping(
                                QuestionCategoryRelationPO::getCategoryId,
                                Collectors.toList()
                        )
                ));
    }

    @Override
    public void batchUpdateStatus(List<Long> ids, QuestionStatus status) {
        questionJpaRepository.batchUpdateStatus(ids, status);
    }

    /**
     * 将领域排序字段转为 Spring Data Sort
     * <p>
     * 非法字段由 SortFieldSpec.validate 抛 BusinessException(400)，不再静默回退。
     * sortField 为 null 时使用默认 createdAt DESC（与改造前行为等价）。
     */
    private Sort toSpringSort(SortField sortField) {
        SortField validated = SortFieldSpec.validate(sortField, ALLOWED_SORT_FIELDS);
        if (validated == null) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        return SortFields.toSpringSort(validated);
    }
}
