package com.knowledgegame.core.infrastructure.adapter.repoadapter;

import com.knowledgegame.core.domain.model.entity.StudyGroup;
import com.knowledgegame.core.domain.port.outbound.StudyGroupRepository;
import com.knowledgegame.core.infrastructure.db.converter.StudyGroupConverter;
import com.knowledgegame.core.infrastructure.db.entity.StudyGroupPO;
import com.knowledgegame.core.infrastructure.db.repository.StudyGroupJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 学习群组仓储适配器（实现领域层出端口）
 */
@Repository
public class StudyGroupRepositoryAdapter implements StudyGroupRepository {

    private final StudyGroupJpaRepository jpaRepository;

    public StudyGroupRepositoryAdapter(StudyGroupJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public StudyGroup save(StudyGroup group) {
        if (group.getId() == null) {
            StudyGroupPO po = StudyGroupConverter.INSTANCE.toPO(group);
            StudyGroupPO saved = jpaRepository.save(po);
            return StudyGroupConverter.INSTANCE.toDomain(saved);
        }
        StudyGroupPO existing = jpaRepository.findById(group.getId())
                .orElseThrow(() -> new IllegalArgumentException("群组不存在: " + group.getId()));
        StudyGroupConverter.INSTANCE.updatePO(existing, group);
        StudyGroupPO saved = jpaRepository.save(existing);
        return StudyGroupConverter.INSTANCE.toDomain(saved);
    }

    @Override
    public Optional<StudyGroup> findById(Long id) {
        return jpaRepository.findById(id).map(StudyGroupConverter.INSTANCE::toDomain);
    }

    @Override
    public boolean existsById(Long id) {
        return jpaRepository.existsById(id);
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }
}
