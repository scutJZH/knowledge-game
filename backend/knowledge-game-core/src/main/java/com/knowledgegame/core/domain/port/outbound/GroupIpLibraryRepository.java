package com.knowledgegame.core.domain.port.outbound;

import com.knowledgegame.core.domain.model.entity.GroupIpLibrary;

import java.util.List;

/**
 * 群组 IP 库关联仓储出端口（领域层定义，基础设施层实现）
 */
public interface GroupIpLibraryRepository {

    GroupIpLibrary save(GroupIpLibrary item);

    List<GroupIpLibrary> saveAll(List<GroupIpLibrary> items);

    List<GroupIpLibrary> findByGroupId(Long groupId);

    boolean existsByGroupIdAndIpSeriesId(Long groupId, Long ipSeriesId);

    void deleteByGroupIdAndIpSeriesIdIn(Long groupId, List<Long> ipSeriesIds);

    void deleteAllByGroupId(Long groupId);
}
