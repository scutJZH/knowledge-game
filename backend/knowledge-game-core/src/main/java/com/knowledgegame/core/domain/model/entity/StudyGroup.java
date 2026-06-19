package com.knowledgegame.core.domain.model.entity;

import com.knowledgegame.core.domain.model.vo.FileRef;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 学习群组聚合根（无框架注解）
 */
@Getter
public class StudyGroup {

    private Long id;
    private String name;
    private String description;
    private FileRef avatar;
    private Long ownerId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 创建新群组（工厂方法）
     */
    public static StudyGroup create(String name, String description,
                                    FileRef avatar, Long ownerId) {
        StudyGroup group = new StudyGroup();
        group.name = name;
        group.description = description;
        group.avatar = avatar;
        group.ownerId = ownerId;
        group.createdAt = LocalDateTime.now();
        group.updatedAt = LocalDateTime.now();
        return group;
    }

    /**
     * 从持久化重建（用于 Repository 加载）
     */
    public static StudyGroup reconstruct(Long id, String name, String description,
                                         FileRef avatar, Long ownerId,
                                         LocalDateTime createdAt, LocalDateTime updatedAt) {
        StudyGroup group = new StudyGroup();
        group.id = id;
        group.name = name;
        group.description = description;
        group.avatar = avatar;
        group.ownerId = ownerId;
        group.createdAt = createdAt;
        group.updatedAt = updatedAt;
        return group;
    }
}
