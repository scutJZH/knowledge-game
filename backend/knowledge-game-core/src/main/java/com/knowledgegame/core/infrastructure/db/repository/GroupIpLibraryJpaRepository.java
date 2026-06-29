package com.knowledgegame.core.infrastructure.db.repository;

import com.knowledgegame.core.infrastructure.db.entity.GroupIpLibraryPO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;
import java.util.Optional;

public interface GroupIpLibraryJpaRepository extends JpaRepository<GroupIpLibraryPO, Long> {

    List<GroupIpLibraryPO> findByGroupId(Long groupId);

    boolean existsByGroupIdAndIpSeriesId(Long groupId, Long ipSeriesId);

    @Modifying
    void deleteByGroupIdAndIpSeriesIdIn(Long groupId, List<Long> ipSeriesIds);

    @Modifying
    void deleteAllByGroupId(Long groupId);

    Optional<GroupIpLibraryPO> findByGroupIdAndIpSeriesId(Long groupId, Long ipSeriesId);
}
