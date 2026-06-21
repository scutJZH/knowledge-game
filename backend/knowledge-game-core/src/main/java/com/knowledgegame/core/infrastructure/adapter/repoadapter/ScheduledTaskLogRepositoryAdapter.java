package com.knowledgegame.core.infrastructure.adapter.repoadapter;

import com.knowledgegame.core.domain.model.entity.ScheduledTaskLog;
import com.knowledgegame.core.domain.model.vo.PageResult;
import com.knowledgegame.core.domain.port.outbound.ScheduledTaskLogRepositoryPort;
import com.knowledgegame.core.infrastructure.db.converter.ScheduledTaskLogConverter;
import com.knowledgegame.core.infrastructure.db.entity.ScheduledTaskLogPO;
import com.knowledgegame.core.infrastructure.db.repository.ScheduledTaskLogJpaRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 定时任务执行日志仓储适配器（实现领域层出端口）
 * <p>
 * @Repository 由 core 的 @ComponentScan 自动发现，无需在 AutoConfiguration 额外注册 @Bean。
 */
@Repository
public class ScheduledTaskLogRepositoryAdapter implements ScheduledTaskLogRepositoryPort {

    private final ScheduledTaskLogJpaRepository jpaRepository;

    public ScheduledTaskLogRepositoryAdapter(ScheduledTaskLogJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(ScheduledTaskLog log) {
        ScheduledTaskLogPO po = ScheduledTaskLogConverter.INSTANCE.toPO(log);
        jpaRepository.save(po);
    }

    @Override
    public PageResult<ScheduledTaskLog> findAll(String taskName, int page, int size) {
        Specification<ScheduledTaskLogPO> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(taskName)) {
                predicates.add(cb.equal(root.get("taskName"), taskName));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "executedAt"));
        Page<ScheduledTaskLogPO> poPage = jpaRepository.findAll(spec, pageRequest);

        return PageResult.<ScheduledTaskLog>builder()
                .content(poPage.getContent().stream()
                        .map(ScheduledTaskLogConverter.INSTANCE::toDomain)
                        .toList())
                .totalElements(poPage.getTotalElements())
                .pageNumber(poPage.getNumber())
                .pageSize(poPage.getSize())
                .totalPages(poPage.getTotalPages())
                .build();
    }
}
