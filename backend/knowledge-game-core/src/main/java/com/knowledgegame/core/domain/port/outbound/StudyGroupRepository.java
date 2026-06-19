package com.knowledgegame.core.domain.port.outbound;

import com.knowledgegame.core.domain.model.entity.StudyGroup;

import java.util.Optional;

/**
 * 学习群组仓储出端口（领域层定义，基础设施层实现）
 */
public interface StudyGroupRepository {

    /**
     * 保存群组
     */
    StudyGroup save(StudyGroup group);

    /**
     * 根据 ID 查询
     */
    Optional<StudyGroup> findById(Long id);

    /**
     * 判断 ID 是否存在
     */
    boolean existsById(Long id);

    /**
     * 硬删除群组
     */
    void deleteById(Long id);
}
