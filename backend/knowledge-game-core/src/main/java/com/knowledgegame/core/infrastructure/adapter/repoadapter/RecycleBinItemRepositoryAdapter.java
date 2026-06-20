package com.knowledgegame.core.infrastructure.adapter.repoadapter;

import com.knowledgegame.core.domain.model.domainenum.ResourceType;
import com.knowledgegame.core.domain.model.entity.RecycleBinItem;
import com.knowledgegame.core.domain.model.vo.PageResult;
import com.knowledgegame.core.domain.model.vo.SortField;
import com.knowledgegame.core.domain.port.outbound.RecycleBinItemRepositoryPort;
import com.knowledgegame.core.domain.spec.SortFieldSpec;
import com.knowledgegame.core.infrastructure.adapter.support.SortFields;
import com.knowledgegame.core.infrastructure.db.converter.RecycleBinItemConverter;
import com.knowledgegame.core.infrastructure.db.entity.RecycleBinItemPO;
import com.knowledgegame.core.infrastructure.db.repository.RecycleBinItemJpaRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 回收站条目仓储适配器（实现领域层出端口）
 * <p>
 * 实现 findAll / findById / findAllById。save / deleteById 在 REQ-104~108 各资源对接时需要添加，
 * REQ-104~108 PRD 将恢复这两个方法签名到 Port 并在此 Adapter 中实现（通过 JPA Repository 直接操作）。
 */
@Repository
public class RecycleBinItemRepositoryAdapter implements RecycleBinItemRepositoryPort {

    /**
     * 列表查询允许的排序字段白名单（PO 字段名 → 中文显示名）
     */
    private static final Map<String, String> ALLOWED_SORT_FIELDS = new LinkedHashMap<>() {{
        put("deletedAt", "删除时间");
        put("restoreDeadline", "剩余保留天数");
        put("resourceType", "资源类型");
        put("originalName", "资源名称");
        put("originalCreatedAt", "原始创建时间");
    }};

    private final RecycleBinItemJpaRepository jpaRepository;

    public RecycleBinItemRepositoryAdapter(RecycleBinItemJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public PageResult<RecycleBinItem> findAll(ResourceType type, String keyword,
                                               int page, int size, SortField sortField) {
        Sort springSort = toSpringSort(sortField);
        PageRequest pageRequest = PageRequest.of(page, size, springSort);

        Specification<RecycleBinItemPO> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (type != null) {
                predicates.add(cb.equal(root.get("resourceType"), type));
            }
            if (StringUtils.hasText(keyword)) {
                predicates.add(cb.like(root.get("originalName"), "%" + keyword + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<RecycleBinItemPO> poPage = jpaRepository.findAll(spec, pageRequest);
        return PageResult.<RecycleBinItem>builder()
                .content(poPage.getContent().stream()
                        .map(RecycleBinItemConverter.INSTANCE::toDomain).toList())
                .totalElements(poPage.getTotalElements())
                .pageNumber(poPage.getNumber())
                .pageSize(poPage.getSize())
                .totalPages(poPage.getTotalPages())
                .build();
    }

    @Override
    public Optional<RecycleBinItem> findById(Long id) {
        return jpaRepository.findById(id).map(RecycleBinItemConverter.INSTANCE::toDomain);
    }

    @Override
    public List<RecycleBinItem> findAllById(Collection<Long> ids) {
        return jpaRepository.findAllById(ids).stream()
                .map(RecycleBinItemConverter.INSTANCE::toDomain)
                .toList();
    }

    /**
     * 将领域排序字段转为 Spring Data Sort
     * <p>
     * 非法字段由 SortFieldSpec.validate 抛 BusinessException(400)。
     * sortField 为 null 时使用默认 deletedAt DESC。
     */
    private Sort toSpringSort(SortField sortField) {
        SortField validated = SortFieldSpec.validate(sortField, ALLOWED_SORT_FIELDS);
        if (validated == null) {
            return Sort.by(Sort.Direction.DESC, "deletedAt");
        }
        return SortFields.toSpringSort(validated);
    }
}
