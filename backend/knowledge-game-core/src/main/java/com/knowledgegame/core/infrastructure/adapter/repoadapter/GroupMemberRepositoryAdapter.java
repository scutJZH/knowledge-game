package com.knowledgegame.core.infrastructure.adapter.repoadapter;

import com.knowledgegame.core.domain.model.entity.GroupMember;
import com.knowledgegame.core.domain.port.outbound.GroupMemberRepository;
import com.knowledgegame.core.infrastructure.db.converter.GroupMemberConverter;
import com.knowledgegame.core.infrastructure.db.entity.GroupMemberPO;
import com.knowledgegame.core.infrastructure.db.repository.GroupMemberJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 群组成员仓储适配器（实现领域层出端口）
 */
@Repository
public class GroupMemberRepositoryAdapter implements GroupMemberRepository {

    private final GroupMemberJpaRepository jpaRepository;

    public GroupMemberRepositoryAdapter(GroupMemberJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public GroupMember save(GroupMember member) {
        if (member.getId() == null) {
            GroupMemberPO po = GroupMemberConverter.INSTANCE.toPO(member);
            GroupMemberPO saved = jpaRepository.save(po);
            return GroupMemberConverter.INSTANCE.toDomain(saved);
        }
        GroupMemberPO existing = jpaRepository.findById(member.getId())
                .orElseThrow(() -> new IllegalArgumentException("成员记录不存在: " + member.getId()));
        GroupMemberConverter.INSTANCE.updatePO(existing, member);
        GroupMemberPO saved = jpaRepository.save(existing);
        return GroupMemberConverter.INSTANCE.toDomain(saved);
    }

    @Override
    public Optional<GroupMember> findByGroupIdAndUserId(Long groupId, Long userId) {
        return jpaRepository.findByGroupIdAndUserId(groupId, userId)
                .map(GroupMemberConverter.INSTANCE::toDomain);
    }

    @Override
    public boolean existsByGroupIdAndUserId(Long groupId, Long userId) {
        return jpaRepository.existsByGroupIdAndUserId(groupId, userId);
    }

    @Override
    public void deleteByGroupIdAndUserId(Long groupId, Long userId) {
        jpaRepository.deleteByGroupIdAndUserId(groupId, userId);
    }
}
