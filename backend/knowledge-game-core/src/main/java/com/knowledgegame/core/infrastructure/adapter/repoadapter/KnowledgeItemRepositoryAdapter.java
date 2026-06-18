package com.knowledgegame.core.infrastructure.adapter.repoadapter;

import com.knowledgegame.core.domain.model.domainenum.KnowledgeItemStatus;
import com.knowledgegame.core.domain.model.entity.KnowledgeItem;
import com.knowledgegame.core.domain.model.vo.PageResult;
import com.knowledgegame.core.domain.model.vo.SortField;
import com.knowledgegame.core.domain.port.outbound.KnowledgeItemRepository;
import com.knowledgegame.core.infrastructure.db.converter.KnowledgeItemConverter;
import com.knowledgegame.core.infrastructure.db.entity.KnowledgeItemCategoryRelationPO;
import com.knowledgegame.core.infrastructure.db.entity.KnowledgeItemPO;
import com.knowledgegame.core.infrastructure.db.repository.KnowledgeItemCategoryRelationJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.KnowledgeItemJpaRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 知识条目仓储适配器（实现领域层出端口）
 */
@Repository
public class KnowledgeItemRepositoryAdapter implements KnowledgeItemRepository {

    /**
     * 领域字段名 → PO 字段名映射
     */
    private static final Map<String, String> FIELD_MAPPING = Map.of(
            "createdAt", "createdAt",
            "updatedAt", "updatedAt",
            "sortOrder", "sortOrder"
    );

    private final KnowledgeItemJpaRepository itemJpaRepository;
    private final KnowledgeItemCategoryRelationJpaRepository relationJpaRepository;

    public KnowledgeItemRepositoryAdapter(KnowledgeItemJpaRepository itemJpaRepository,
                                           KnowledgeItemCategoryRelationJpaRepository relationJpaRepository) {
        this.itemJpaRepository = itemJpaRepository;
        this.relationJpaRepository = relationJpaRepository;
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
        if (sortField == null) {
            return Sort.by("sortOrder").ascending().and(Sort.by("createdAt").descending());
        }
        String field = sortField.getField();
        Sort.Direction direction = sortField.getDirection() == SortField.Direction.ASC
                ? Sort.Direction.ASC : Sort.Direction.DESC;

        if ("sortOrder".equals(field)) {
            return Sort.by("sortOrder").ascending().and(Sort.by("createdAt").descending());
        }
        if (!FIELD_MAPPING.containsKey(field)) {
            throw new IllegalArgumentException("不支持的排序字段: " + field);
        }
        String poField = FIELD_MAPPING.get(field);
        return Sort.by(direction, poField);
    }
}
