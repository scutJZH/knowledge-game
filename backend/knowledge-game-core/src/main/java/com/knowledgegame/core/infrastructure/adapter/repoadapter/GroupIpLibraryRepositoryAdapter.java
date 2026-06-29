package com.knowledgegame.core.infrastructure.adapter.repoadapter;

import com.knowledgegame.core.domain.model.entity.GroupIpLibrary;
import com.knowledgegame.core.domain.port.outbound.GroupIpLibraryRepository;
import com.knowledgegame.core.infrastructure.db.converter.GroupIpLibraryConverter;
import com.knowledgegame.core.infrastructure.db.entity.GroupIpLibraryPO;
import com.knowledgegame.core.infrastructure.db.repository.GroupIpLibraryJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 群组 IP 库关联仓储适配器（实现领域层出端口）
 */
@Repository
public class GroupIpLibraryRepositoryAdapter implements GroupIpLibraryRepository {

    private final GroupIpLibraryJpaRepository jpaRepository;

    public GroupIpLibraryRepositoryAdapter(GroupIpLibraryJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public GroupIpLibrary save(GroupIpLibrary item) {
        GroupIpLibraryPO po = GroupIpLibraryConverter.INSTANCE.toPO(item);
        GroupIpLibraryPO saved = jpaRepository.save(po);
        return GroupIpLibraryConverter.INSTANCE.toDomain(saved);
    }

    @Override
    public List<GroupIpLibrary> saveAll(List<GroupIpLibrary> items) {
        List<GroupIpLibraryPO> poList = items.stream()
                .map(GroupIpLibraryConverter.INSTANCE::toPO)
                .toList();
        List<GroupIpLibraryPO> saved = jpaRepository.saveAll(poList);
        return GroupIpLibraryConverter.INSTANCE.toDomainList(saved);
    }

    @Override
    public List<GroupIpLibrary> findByGroupId(Long groupId) {
        return GroupIpLibraryConverter.INSTANCE.toDomainList(
                jpaRepository.findByGroupId(groupId));
    }

    @Override
    public boolean existsByGroupIdAndIpSeriesId(Long groupId, Long ipSeriesId) {
        return jpaRepository.existsByGroupIdAndIpSeriesId(groupId, ipSeriesId);
    }

    @Override
    public void deleteByGroupIdAndIpSeriesIdIn(Long groupId, List<Long> ipSeriesIds) {
        jpaRepository.deleteByGroupIdAndIpSeriesIdIn(groupId, ipSeriesIds);
    }

    @Override
    public void deleteAllByGroupId(Long groupId) {
        jpaRepository.deleteAllByGroupId(groupId);
    }

    @Override
    public Optional<GroupIpLibrary> findByGroupIdAndIpSeriesId(Long groupId, Long ipSeriesId) {
        return jpaRepository.findByGroupIdAndIpSeriesId(groupId, ipSeriesId)
                .map(GroupIpLibraryConverter.INSTANCE::toDomain);
    }
}
