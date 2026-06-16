package com.knowledgegame.core.domain.model.entity;

import com.knowledgegame.core.domain.model.domainenum.KnowledgeCategoryStatus;
import com.knowledgegame.core.domain.model.vo.FileRef;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 知识点分类领域实体（无框架注解）
 */
@Getter
public class KnowledgeCategory {

    private Long id;
    private Long parentId;
    private String name;
    private String description;
    private FileRef icon;
    private String color;
    private FileRef coverImage;
    private int sortOrder;
    private KnowledgeCategoryStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 创建新知识点分类（工厂方法）
     */
    public static KnowledgeCategory create(String name, String description, Long parentId,
                                           FileRef icon, String color, FileRef coverImage,
                                           int sortOrder) {
        KnowledgeCategory category = new KnowledgeCategory();
        category.name = name;
        category.description = description;
        category.parentId = parentId;
        category.icon = icon;
        category.color = color;
        category.coverImage = coverImage;
        category.sortOrder = sortOrder;
        category.status = KnowledgeCategoryStatus.ACTIVE;
        category.createdAt = LocalDateTime.now();
        category.updatedAt = LocalDateTime.now();
        return category;
    }

    /**
     * 从持久化重建（用于 Repository 加载）
     */
    public static KnowledgeCategory reconstruct(Long id, Long parentId, String name,
                                                String description, FileRef icon, String color,
                                                FileRef coverImage, int sortOrder,
                                                KnowledgeCategoryStatus status,
                                                LocalDateTime createdAt, LocalDateTime updatedAt) {
        KnowledgeCategory category = new KnowledgeCategory();
        category.id = id;
        category.parentId = parentId;
        category.name = name;
        category.description = description;
        category.icon = icon;
        category.color = color;
        category.coverImage = coverImage;
        category.sortOrder = sortOrder;
        category.status = status;
        category.createdAt = createdAt;
        category.updatedAt = updatedAt;
        return category;
    }

    /**
     * 更新基本信息（不含 parentId，移动使用 moveTo）
     */
    public void update(String name, String description, FileRef icon, String color,
                       FileRef coverImage, Integer sortOrder) {
        if (name != null) {
            this.name = name;
        }
        if (description != null) {
            this.description = description;
        }
        if (icon != null) {
            this.icon = icon;
        }
        if (color != null) {
            this.color = color;
        }
        if (coverImage != null) {
            this.coverImage = coverImage;
        }
        if (sortOrder != null) {
            this.sortOrder = sortOrder;
        }
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 移动到新父级
     */
    public void moveTo(Long newParentId) {
        this.parentId = newParentId;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 软删除（status→INACTIVE）
     */
    public void deactivate() {
        this.status = KnowledgeCategoryStatus.INACTIVE;
        this.updatedAt = LocalDateTime.now();
    }
}
