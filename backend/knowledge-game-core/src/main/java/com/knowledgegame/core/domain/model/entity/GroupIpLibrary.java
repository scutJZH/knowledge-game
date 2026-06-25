package com.knowledgegame.core.domain.model.entity;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 群组 IP 库关联聚合根（无框架注解）
 */
@Getter
public class GroupIpLibrary {

    private Long id;
    private Long groupId;
    private Long ipSeriesId;
    private LocalDateTime addedAt;

    public static GroupIpLibrary create(Long groupId, Long ipSeriesId) {
        GroupIpLibrary item = new GroupIpLibrary();
        item.groupId = groupId;
        item.ipSeriesId = ipSeriesId;
        item.addedAt = LocalDateTime.now();
        return item;
    }

    public static GroupIpLibrary reconstruct(Long id, Long groupId, Long ipSeriesId,
                                              LocalDateTime addedAt) {
        GroupIpLibrary item = new GroupIpLibrary();
        item.id = id;
        item.groupId = groupId;
        item.ipSeriesId = ipSeriesId;
        item.addedAt = addedAt;
        return item;
    }
}
