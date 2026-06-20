package com.knowledgegame.core.domain.port.outbound;

import com.knowledgegame.core.domain.model.entity.GroupMember;

import java.util.Optional;

/**
 * 群组成员仓储出端口（领域层定义，基础设施层实现）
 */
public interface GroupMemberRepository {

    /**
     * 保存成员记录
     */
    GroupMember save(GroupMember member);

    /**
     * 根据群组 ID 和用户 ID 查询
     */
    Optional<GroupMember> findByGroupIdAndUserId(Long groupId, Long userId);

    /**
     * 判断成员关系是否存在
     */
    boolean existsByGroupIdAndUserId(Long groupId, Long userId);

    /**
     * 删除指定群组中指定用户的成员关系
     */
    void deleteByGroupIdAndUserId(Long groupId, Long userId);

    /**
     * 根据 ID 查询成员记录
     */
    Optional<GroupMember> findById(Long id);
}
