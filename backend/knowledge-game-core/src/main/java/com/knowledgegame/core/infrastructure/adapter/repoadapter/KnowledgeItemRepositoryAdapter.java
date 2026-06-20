package com.knowledgegame.core.infrastructure.adapter.repoadapter;

import com.knowledgegame.core.domain.model.domainenum.KnowledgeItemStatus;
import com.knowledgegame.core.domain.model.entity.KnowledgeItem;
import com.knowledgegame.core.domain.model.vo.PageResult;
import com.knowledgegame.core.domain.model.vo.SortField;
import com.knowledgegame.core.domain.port.outbound.KnowledgeItemRepository;
import com.knowledgegame.core.domain.spec.SortFieldSpec;
import com.knowledgegame.core.infrastructure.adapter.support.SortFields;
import com.knowledgegame.core.infrastructure.db.converter.KnowledgeItemConverter;
import com.knowledgegame.core.infrastructure.db.entity.KnowledgeCategoryPO;
import com.knowledgegame.core.infrastructure.db.entity.KnowledgeItemCategoryRelationPO;
import com.knowledgegame.core.infrastructure.db.entity.KnowledgeItemPO;
import com.knowledgegame.core.infrastructure.db.repository.KnowledgeItemCategoryRelationJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.KnowledgeItemJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
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
 * 知识条目仓储适配器（实现领域层出端口）
 */
@Repository
public class KnowledgeItemRepositoryAdapter implements KnowledgeItemRepository {

    private static final Map<String, String> ALLOWED_SORT_FIELDS = new LinkedHashMap<>() {{
        put("id", "ID");
        put("title", "标题");
        put("categoryName", "分类名称");
        put("sortOrder", "排序号");
        put("status", "状态");
        put("createdAt", "创建时间");
        put("updatedAt", "更新时间");
    }};

    private final KnowledgeItemJpaRepository itemJpaRepository;
    private final KnowledgeItemCategoryRelationJpaRepository relationJpaRepository;
    private final EntityManager em;

    public KnowledgeItemRepositoryAdapter(KnowledgeItemJpaRepository itemJpaRepository,
                                           KnowledgeItemCategoryRelationJpaRepository relationJpaRepository,
                                           EntityManager em) {
        this.itemJpaRepository = itemJpaRepository;
        this.relationJpaRepository = relationJpaRepository;
        this.em = em;
    }

    @Override
    public KnowledgeItem save(KnowledgeItem item) {
        if (item.getId() == null) {
            KnowledgeItemPO po = KnowledgeItemConverter.INSTANCE.toPO(item);
            KnowledgeItemPO saved = itemJpaRepository.save(po);
            return KnowledgeItemConverter.INSTANCE.toDomain(saved);
        }
        KnowledgeItemPO existing = itemJpaRepository.findById(item.getId())
                .orElseThrow(() -> new IllegalArgumentException("知识条目不存在: " + item.getId()));
        KnowledgeItemConverter.INSTANCE.updatePO(existing, item);
        KnowledgeItemPO saved = itemJpaRepository.save(existing);
        return KnowledgeItemConverter.INSTANCE.toDomain(saved);
    }

    @Override
    public Optional<KnowledgeItem> findById(Long id) {
        return itemJpaRepository.findById(id).map(KnowledgeItemConverter.INSTANCE::toDomain);
    }

    @Override
    public PageResult<KnowledgeItem> findByConditions(String keyword, Long categoryId, String tag,
                                                       KnowledgeItemStatus status, SortField sortField,
                                                       int pageNumber, int pageSize) {
        Specification<KnowledgeItemPO> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 关键词搜索（标题模糊匹配）
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.like(root.get("title"), "%" + keyword + "%"));
            }

            // 状态筛选
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            // 分类筛选（通过关联表子查询）
            if (categoryId != null) {
                Subquery<Long> subquery = query.subquery(Long.class);
                Root<KnowledgeItemCategoryRelationPO> relRoot = subquery.from(KnowledgeItemCategoryRelationPO.class);
                subquery.select(relRoot.get("itemId"))
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

        if (sortField != null && "categoryName".equals(sortField.getField())) {
            SortField validated = SortFieldSpec.validate(sortField, ALLOWED_SORT_FIELDS);
            return findByConditionsOrderByCategoryName(spec, validated, pageNumber, pageSize);
        }

        Sort springSort = toSpringSort(sortField);
        Page<KnowledgeItemPO> springPage = itemJpaRepository.findAll(spec,
                PageRequest.of(pageNumber, pageSize, springSort));

        return PageResult.<KnowledgeItem>builder()
                .content(springPage.getContent().stream()
                        .map(KnowledgeItemConverter.INSTANCE::toDomain).toList())
                .totalElements(springPage.getTotalElements())
                .pageNumber(springPage.getNumber())
                .pageSize(springPage.getSize())
                .totalPages(springPage.getTotalPages())
                .build();
    }

    @Override
    public List<KnowledgeItem> findByIds(List<Long> ids) {
        return itemJpaRepository.findAllById(ids).stream()
                .map(KnowledgeItemConverter.INSTANCE::toDomain)
                .toList();
    }

    @Override
    public void saveCategoryRelations(Long itemId, List<Long> categoryIds) {
        relationJpaRepository.deleteByItemId(itemId);
        // 强制 flush DELETE，避免 Hibernate 先 INSERT 后 DELETE 触发唯一键冲突
        relationJpaRepository.flush();
        if (categoryIds != null && !categoryIds.isEmpty()) {
            List<KnowledgeItemCategoryRelationPO> relations = categoryIds.stream()
                    .map(catId -> KnowledgeItemCategoryRelationPO.builder()
                            .itemId(itemId)
                            .categoryId(catId)
                            .build())
                    .toList();
            relationJpaRepository.saveAll(relations);
        }
    }

    @Override
    public List<Long> findActiveCategoryIdsByItemId(Long itemId) {
        return itemJpaRepository.findActiveCategoryIdsByItemId(itemId);
    }

    @Override
    public long countActiveByCategoryId(Long categoryId) {
        return relationJpaRepository.countActiveItemsByCategoryId(categoryId);
    }

    @Override
    public Map<Long, List<Long>> findActiveCategoryIdsByItemIds(List<Long> itemIds) {
        List<Object[]> rows = itemJpaRepository.findActiveCategoryIdsByItemIds(itemIds);
        return rows.stream()
                .collect(Collectors.groupingBy(
                        row -> (Long) row[0],
                        Collectors.mapping(
                                row -> (Long) row[1],
                                Collectors.toList()
                        )
                ));
    }

    @Override
    public Map<Long, List<Long>> findCategoryIdsByItemIds(List<Long> itemIds) {
        List<KnowledgeItemCategoryRelationPO> relations = relationJpaRepository.findAllByItemIdIn(itemIds);
        return relations.stream()
                .collect(Collectors.groupingBy(
                        KnowledgeItemCategoryRelationPO::getItemId,
                        Collectors.mapping(
                                KnowledgeItemCategoryRelationPO::getCategoryId,
                                Collectors.toList()
                        )
                ));
    }

    @Override
    public void batchUpdateStatus(List<Long> ids, KnowledgeItemStatus status) {
        itemJpaRepository.batchUpdateStatus(ids, status);
    }

    /**
     * 将领域排序字段转为 Spring Data Sort
     * sortOrder 字段使用复合排序：sortOrder ASC + createdAt DESC
     */
    private Sort toSpringSort(SortField sortField) {
        SortField validated = SortFieldSpec.validate(sortField, ALLOWED_SORT_FIELDS);
        if (validated == null) {
            return Sort.by(Sort.Direction.ASC, "sortOrder")
                    .and(Sort.by(Sort.Direction.DESC, "createdAt"));
        }
        if ("categoryName".equals(validated.getField())) {
            throw new IllegalStateException(
                    "categoryName 排序应在 findByConditions 入口分支处理，不应到达 toSpringSort");
        }
        if ("sortOrder".equals(validated.getField())) {
            return Sort.by(Sort.Direction.ASC, "sortOrder")
                    .and(Sort.by(Sort.Direction.DESC, "createdAt"));
        }
        return SortFields.toSpringSort(validated);
    }

    /**
     * categoryName 排序的独立查询路径（EntityManager + CriteriaBuilder + 子查询）
     * 因为 JPA Sort 不支持非实体属性的 JOIN 排序，需要手动构建 ORDER BY 子查询
     */
    private PageResult<KnowledgeItem> findByConditionsOrderByCategoryName(
            Specification<KnowledgeItemPO> spec, SortField sortField, int pageNumber, int pageSize) {
        CriteriaBuilder cb = em.getCriteriaBuilder();

        // 数据查询
        CriteriaQuery<KnowledgeItemPO> cq = cb.createQuery(KnowledgeItemPO.class);
        Root<KnowledgeItemPO> root = cq.from(KnowledgeItemPO.class);
        cq.where(spec.toPredicate(root, cq, cb));

        // 子查询：SELECT MIN(c.name) FROM relation r, category c
        //         WHERE r.categoryId = c.id AND r.itemId = root.id
        // 用于 ORDER BY（两个实体无 JPA 关系映射，需 cb.equal 手动连接）
        Subquery<String> subquery = cq.subquery(String.class);
        Root<KnowledgeItemCategoryRelationPO> relRoot = subquery.from(KnowledgeItemCategoryRelationPO.class);
        Root<KnowledgeCategoryPO> catRoot = subquery.from(KnowledgeCategoryPO.class);
        subquery.select(cb.function("MIN", String.class, catRoot.get("name")))
                .where(cb.equal(relRoot.get("categoryId"), catRoot.get("id")),
                       cb.equal(relRoot.get("itemId"), root.get("id")));

        // COALESCE 实现 NULLS LAST：ASC 时 null → "~~~"（排在所有真实名称后），
        // DESC 时 null → ""（排在所有真实名称后）
        boolean isAsc = sortField.getDirection() == SortField.Direction.ASC;
        Expression<String> categorySortExpr = isAsc
                ? cb.coalesce(subquery, "~~~")
                : cb.coalesce(subquery, "");
        cq.orderBy(isAsc ? cb.asc(categorySortExpr) : cb.desc(categorySortExpr),
                cb.asc(root.get("sortOrder")),
                cb.desc(root.get("createdAt")));

        // 用 GROUP BY 代替 DISTINCT，因为 MySQL 要求 DISTINCT 时 ORDER BY 表达式必须在 SELECT 列表中
        // 而子查询只在 ORDER BY 中，不在 SELECT 列表，MySQL 会静默忽略 ORDER BY
        cq.groupBy(root.get("id"));

        TypedQuery<KnowledgeItemPO> typedQuery = em.createQuery(cq);
        typedQuery.setFirstResult(pageNumber * pageSize);
        typedQuery.setMaxResults(pageSize);
        List<KnowledgeItemPO> results = typedQuery.getResultList();

        // 单独 COUNT 查询（无 JOIN/DISTINCT，避免 COUNT 受 DISTINCT+JOIN 干扰）
        CriteriaQuery<Long> countCq = cb.createQuery(Long.class);
        Root<KnowledgeItemPO> countRoot = countCq.from(KnowledgeItemPO.class);
        countCq.select(cb.count(countRoot));
        countCq.where(spec.toPredicate(countRoot, countCq, cb));
        Long total = em.createQuery(countCq).getSingleResult();

        int totalPages = (int) Math.ceil((double) total / pageSize);

        return PageResult.<KnowledgeItem>builder()
                .content(results.stream()
                        .map(KnowledgeItemConverter.INSTANCE::toDomain).toList())
                .totalElements(total)
                .pageNumber(pageNumber)
                .pageSize(pageSize)
                .totalPages(totalPages)
                .build();
    }
}
