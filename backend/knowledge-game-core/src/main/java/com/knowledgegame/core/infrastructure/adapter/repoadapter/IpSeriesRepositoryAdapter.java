package com.knowledgegame.core.infrastructure.adapter.repoadapter;

import com.knowledgegame.core.domain.model.domainenum.IpSeriesStatus;
import com.knowledgegame.core.domain.model.entity.IpSeries;
import com.knowledgegame.core.domain.model.vo.PageResult;
import com.knowledgegame.core.domain.model.vo.SortField;
import com.knowledgegame.core.domain.port.outbound.IpSeriesRepositoryPort;
import com.knowledgegame.core.domain.spec.SortFieldSpec;
import com.knowledgegame.core.infrastructure.adapter.support.SortFields;
import com.knowledgegame.core.infrastructure.db.converter.IpSeriesConverter;
import com.knowledgegame.core.infrastructure.db.entity.IpSeriesPO;
import com.knowledgegame.core.infrastructure.db.repository.IpSeriesJpaRepository;
import jakarta.persistence.criteria.Predicate;
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

/**
 * IP 系列仓储适配器（实现领域层出端口，注入 JPA Repository）
 */
@Repository
public class IpSeriesRepositoryAdapter implements IpSeriesRepositoryPort {

    /**
     * 列表查询允许的排序字段白名单（PO 字段名 → 中文显示名，保持插入顺序供错误消息稳定输出）
     */
    private static final Map<String, String> ALLOWED_SORT_FIELDS = new LinkedHashMap<>() {{
        put("id", "ID");
        put("code", "编码");
        put("name", "名称");
        put("status", "状态");
        put("createdAt", "创建时间");
        put("updatedAt", "更新时间");
    }};

    private final IpSeriesJpaRepository ipSeriesJpaRepository;

    public IpSeriesRepositoryAdapter(IpSeriesJpaRepository ipSeriesJpaRepository) {
        this.ipSeriesJpaRepository = ipSeriesJpaRepository;
    }

    @Override
    public IpSeries save(IpSeries ipSeries) {
        if (ipSeries.getId() == null) {
            IpSeriesPO po = IpSeriesConverter.INSTANCE.toPO(ipSeries);
            IpSeriesPO saved = ipSeriesJpaRepository.save(po);
            return IpSeriesConverter.INSTANCE.toDomain(saved);
        }
        IpSeriesPO existing = ipSeriesJpaRepository.findById(ipSeries.getId())
                .orElseThrow(() -> new IllegalArgumentException("IP 系列不存在: " + ipSeries.getId()));
        IpSeriesConverter.INSTANCE.updatePO(existing, ipSeries);
        IpSeriesPO saved = ipSeriesJpaRepository.save(existing);
        return IpSeriesConverter.INSTANCE.toDomain(saved);
    }

    @Override
    public Optional<IpSeries> findById(Long id) {
        return ipSeriesJpaRepository.findById(id).map(IpSeriesConverter.INSTANCE::toDomain);
    }

    @Override
    public Optional<IpSeries> findByCode(String code) {
        return ipSeriesJpaRepository.findByCode(code).map(IpSeriesConverter.INSTANCE::toDomain);
    }

    @Override
    public Optional<IpSeries> findByName(String name) {
        return ipSeriesJpaRepository.findByName(name).map(IpSeriesConverter.INSTANCE::toDomain);
    }

    @Override
    public PageResult<IpSeries> findByConditions(String name, String code, IpSeriesStatus status,
                                                  SortField sortField, int pageNumber, int pageSize) {
        Specification<IpSeriesPO> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (name != null && !name.isBlank()) {
                predicates.add(cb.like(root.get("name"), "%" + name + "%"));
            }
            if (code != null && !code.isBlank()) {
                predicates.add(cb.like(root.get("code"), "%" + code + "%"));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Sort springSort = toSpringSort(sortField);
        Page<IpSeriesPO> springPage = ipSeriesJpaRepository.findAll(spec,
                PageRequest.of(pageNumber, pageSize, springSort));
        return PageResult.<IpSeries>builder()
                .content(springPage.getContent().stream()
                        .map(IpSeriesConverter.INSTANCE::toDomain).toList())
                .totalElements(springPage.getTotalElements())
                .pageNumber(springPage.getNumber())
                .pageSize(springPage.getSize())
                .totalPages(springPage.getTotalPages())
                .build();
    }

    /**
     * 将领域排序字段转为 Spring Data Sort
     * <p>
     * 非法字段由 SortFieldSpec.validate 抛 BusinessException(400)。
     * sortField 为 null 时使用默认 createdAt DESC。
     */
    private Sort toSpringSort(SortField sortField) {
        SortField validated = SortFieldSpec.validate(sortField, ALLOWED_SORT_FIELDS);
        if (validated == null) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        return SortFields.toSpringSort(validated);
    }

    @Override
    public List<IpSeries> findAllByIdIn(List<Long> ids) {
        return ipSeriesJpaRepository.findAllById(ids).stream()
                .map(IpSeriesConverter.INSTANCE::toDomain)
                .toList();
    }

    @Override
    public List<IpSeries> findAll() {
        return ipSeriesJpaRepository.findAll().stream()
                .map(IpSeriesConverter.INSTANCE::toDomain)
                .toList();
    }

    @Override
    public boolean existsById(Long id) {
        return ipSeriesJpaRepository.existsById(id);
    }
}
