package com.knowledgegame.core.infrastructure.adapter.repoadapter;

import com.knowledgegame.core.domain.model.domainenum.KnowledgeCategoryStatus;
import com.knowledgegame.core.domain.model.entity.KnowledgeCategory;
import com.knowledgegame.core.domain.model.vo.PageResult;
import com.knowledgegame.core.domain.port.outbound.KnowledgeCategoryRepositoryPort;
import com.knowledgegame.core.infrastructure.db.converter.KnowledgeCategoryConverter;
import com.knowledgegame.core.infrastructure.db.entity.KnowledgeCategoryPO;
import com.knowledgegame.core.infrastructure.db.repository.KnowledgeCategoryJpaRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 知识点分类仓储适配器（实现领域层出端口，注入 JPA Repository）
 */
@Repository
public class KnowledgeCategoryRepositoryAdapter implements KnowledgeCategoryRepositoryPort {

    private final KnowledgeCategoryJpaRepository jpaRepository;

    public KnowledgeCategoryRepositoryAdapter(KnowledgeCategoryJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public KnowledgeCategory save(KnowledgeCategory category) {
        if (category.getId() == null) {
            KnowledgeCategoryPO po = KnowledgeCategoryConverter.INSTANCE.toPO(category);
            KnowledgeCategoryPO saved = jpaRepository.save(po);
            return KnowledgeCategoryConverter.INSTANCE.toDomain(saved);
        }
        KnowledgeCategoryPO existing = jpaRepository.findById(category.getId())
                .orElseThrow(() -> new IllegalArgumentException("知识点分类不存在: " + category.getId()));
        KnowledgeCategoryConverter.INSTANCE.updatePO(existing, category);
        // 显式设置 parentId，因为 Converter 的 IGNORE 策略会跳过 null（moveTo(null) 移到顶级时需要）
        existing.setParentId(category.getParentId());
        KnowledgeCategoryPO saved = jpaRepository.save(existing);
        return KnowledgeCategoryConverter.INSTANCE.toDomain(saved);
    }

    @Override
    public Optional<KnowledgeCategory> findById(Long id) {
        return jpaRepository.findById(id).map(KnowledgeCategoryConverter.INSTANCE::toDomain);
    }

    @Override
    public boolean existsByNameAndParentId(String name, Long parentId) {
        return jpaRepository.existsByNameAndParentId(name, parentId);
    }

    @Override
    public PageResult<KnowledgeCategory> findByConditions(String keyword, KnowledgeCategoryStatus status,
                                                           Long parentId, int pageNumber, int pageSize) {
        Specification<KnowledgeCategoryPO> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.like(root.get("name"), "%" + keyword + "%"));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (parentId != null) {
                if (parentId == -1L) {
                    predicates.add(cb.isNull(root.get("parentId")));
                } else {
                    predicates.add(cb.equal(root.get("parentId"), parentId));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<KnowledgeCategoryPO> springPage = jpaRepository.findAll(spec,
                PageRequest.of(pageNumber, pageSize,
                        Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.desc("createdAt"))));
        return PageResult.<KnowledgeCategory>builder()
                .content(springPage.getContent().stream()
                        .map(KnowledgeCategoryConverter.INSTANCE::toDomain).toList())
                .totalElements(springPage.getTotalElements())
                .pageNumber(springPage.getNumber())
                .pageSize(springPage.getSize())
                .totalPages(springPage.getTotalPages())
                .build();
    }

    @Override
    public List<KnowledgeCategory> findAll() {
        return jpaRepository.findAll().stream()
                .map(KnowledgeCategoryConverter.INSTANCE::toDomain)
                .toList();
    }

    @Override
    public List<Long> findDescendantIds(Long parentId) {
        return jpaRepository.findDescendantIds(parentId);
    }

    @Override
    public long countActiveByParentId(Long parentId) {
        return jpaRepository.countActiveByParentId(parentId);
    }

    @Override
    public List<KnowledgeCategory> findAllByIdIn(List<Long> ids) {
        return jpaRepository.findAllById(ids).stream()
                .map(KnowledgeCategoryConverter.INSTANCE::toDomain)
                .toList();
    }

    @Override
    public Integer findMaxSortOrderByParentId(Long parentId) {
        return jpaRepository.findMaxSortOrderByParentId(parentId);
    }

    @Override
    public Integer findMaxSortOrderForRoot() {
        return jpaRepository.findMaxSortOrderForRoot();
    }
}
