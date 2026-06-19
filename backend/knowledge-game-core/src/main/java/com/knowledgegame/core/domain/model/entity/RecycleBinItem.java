package com.knowledgegame.core.domain.model.entity;

import com.knowledgegame.core.domain.model.domainenum.ResourceType;

import java.time.LocalDateTime;

/**
 * 回收站条目聚合根（纯数据持有对象）
 * <p>
 * 表示回收站总览表中的一条记录。仅提供 getter 和 all-args 全参构造器，
 * 无行为方法。恢复/永久删除等行为方法由 REQ-103/104~108 补充。
 */
public class RecycleBinItem {

    private final Long id;
    private final ResourceType resourceType;
    private final Long originalId;
    private final String originalName;
    private final LocalDateTime originalCreatedAt;
    private final LocalDateTime originalUpdatedAt;
    private final String originalCreatedBy;
    private final String originalUpdatedBy;
    private final String deletedBy;
    private final LocalDateTime deletedAt;
    private final LocalDateTime restoreDeadline;

    public RecycleBinItem(Long id, ResourceType resourceType, Long originalId, String originalName,
                          LocalDateTime originalCreatedAt, LocalDateTime originalUpdatedAt,
                          String originalCreatedBy, String originalUpdatedBy,
                          String deletedBy, LocalDateTime deletedAt, LocalDateTime restoreDeadline) {
        this.id = id;
        this.resourceType = resourceType;
        this.originalId = originalId;
        this.originalName = originalName;
        this.originalCreatedAt = originalCreatedAt;
        this.originalUpdatedAt = originalUpdatedAt;
        this.originalCreatedBy = originalCreatedBy;
        this.originalUpdatedBy = originalUpdatedBy;
        this.deletedBy = deletedBy;
        this.deletedAt = deletedAt;
        this.restoreDeadline = restoreDeadline;
    }

    public Long getId() {
        return id;
    }

    public ResourceType getResourceType() {
        return resourceType;
    }

    public Long getOriginalId() {
        return originalId;
    }

    public String getOriginalName() {
        return originalName;
    }

    public LocalDateTime getOriginalCreatedAt() {
        return originalCreatedAt;
    }

    public LocalDateTime getOriginalUpdatedAt() {
        return originalUpdatedAt;
    }

    public String getOriginalCreatedBy() {
        return originalCreatedBy;
    }

    public String getOriginalUpdatedBy() {
        return originalUpdatedBy;
    }

    public String getDeletedBy() {
        return deletedBy;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public LocalDateTime getRestoreDeadline() {
        return restoreDeadline;
    }
}
