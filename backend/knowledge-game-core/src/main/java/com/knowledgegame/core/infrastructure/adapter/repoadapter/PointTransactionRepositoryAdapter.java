package com.knowledgegame.core.infrastructure.adapter.repoadapter;

import com.knowledgegame.core.domain.model.domainenum.ReferenceType;
import com.knowledgegame.core.domain.model.domainenum.TxType;
import com.knowledgegame.core.domain.model.entity.PointTransaction;
import com.knowledgegame.core.domain.model.vo.PageResult;
import com.knowledgegame.core.domain.model.vo.SortField;
import com.knowledgegame.core.domain.port.outbound.PointTransactionRepository;
import com.knowledgegame.core.domain.spec.SortFieldSpec;
import com.knowledgegame.core.infrastructure.adapter.support.SortFields;
import com.knowledgegame.core.infrastructure.db.converter.PointTransactionConverter;
import com.knowledgegame.core.infrastructure.db.entity.PointTransactionPO;
import com.knowledgegame.core.infrastructure.db.repository.PointTransactionJpaRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 积分流水仓储适配器（实现领域层出端口）
 */
@Repository
public class PointTransactionRepositoryAdapter implements PointTransactionRepository {

    private static final Map<String, String> ALLOWED_SORT_FIELDS = new LinkedHashMap<>() {{
        put("createdAt", "创建时间");
        put("amount", "金额");
    }};

    private final PointTransactionJpaRepository jpaRepository;

    public PointTransactionRepositoryAdapter(PointTransactionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public PointTransaction save(PointTransaction tx) {
        PointTransactionPO po = PointTransactionConverter.INSTANCE.toPO(tx);
        PointTransactionPO saved = jpaRepository.save(po);
        return PointTransactionConverter.INSTANCE.toDomain(saved);
    }

    @Override
    public PageResult<PointTransaction> findByGroup(Long groupId, Long userId,
                                                     TxType type, ReferenceType refType,
                                                     LocalDateTime startDate, LocalDateTime endDate,
                                                     SortField sortField, int page, int size) {
        Specification<PointTransactionPO> spec = buildSpec(groupId, userId,
                type, refType, startDate, endDate);
        return executeQuery(spec, sortField, page, size);
    }

    @Override
    public PageResult<PointTransaction> findByUser(Long userId, Long groupId,
                                                    TxType type, ReferenceType refType,
                                                    LocalDateTime startDate, LocalDateTime endDate,
                                                    SortField sortField, int page, int size) {
        Specification<PointTransactionPO> spec = buildSpec(groupId, userId,
                type, refType, startDate, endDate);
        return executeQuery(spec, sortField, page, size);
    }

    private Specification<PointTransactionPO> buildSpec(Long groupId, Long userId,
                                                         TxType type, ReferenceType refType,
                                                         LocalDateTime startDate, LocalDateTime endDate) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (groupId != null) {
                predicates.add(cb.equal(root.get("groupId"), groupId));
            }
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (refType != null) {
                predicates.add(cb.equal(root.get("referenceType"), refType));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private PageResult<PointTransaction> executeQuery(Specification<PointTransactionPO> spec,
                                                       SortField sortField, int page, int size) {
        Sort springSort = toSpringSort(sortField);
        Page<PointTransactionPO> springPage = jpaRepository.findAll(spec,
                PageRequest.of(page, size, springSort));

        return PageResult.<PointTransaction>builder()
                .content(springPage.getContent().stream()
                        .map(PointTransactionConverter.INSTANCE::toDomain).toList())
                .totalElements(springPage.getTotalElements())
                .pageNumber(springPage.getNumber())
                .pageSize(springPage.getSize())
                .totalPages(springPage.getTotalPages())
                .build();
    }

    private Sort toSpringSort(SortField sortField) {
        SortField validated = SortFieldSpec.validate(sortField, ALLOWED_SORT_FIELDS);
        if (validated == null) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        return SortFields.toSpringSort(validated);
    }
}
