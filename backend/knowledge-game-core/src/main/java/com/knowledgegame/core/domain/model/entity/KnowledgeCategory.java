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
     * 更新必填字段（不支持清空）。可清空字段（description/color/icon/coverImage）请用对应的 updateXxx / clearXxx 方法
     */
    public void update(String name, Integer sortOrder) {
        if (name != null) {
            this.name = name;
        }
        if (sortOrder != null) {
            this.sortOrder = sortOrder;
        }
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 更新描述（清空请用 clearDescription）
     *
     * @throws IllegalArgumentException description 为 null 时抛出
     */
    public void updateDescription(String description) {
        if (description == null) {
            throw new IllegalArgumentException("description 清空请用 clearDescription()");
        }
        this.description = description;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 清空描述
     */
    public void clearDescription() {
        this.description = null;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 更新颜色（清空请用 clearColor）
     *
     * @throws IllegalArgumentException color 为 null 时抛出
     */
    public void updateColor(String color) {
        if (color == null) {
            throw new IllegalArgumentException("color 清空请用 clearColor()");
        }
        this.color = color;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 清空颜色
     */
    public void clearColor() {
        this.color = null;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 更新图标（清空请用 clearIcon）
     *
     * @throws IllegalArgumentException icon 为 null 时抛出
     */
    public void updateIcon(FileRef icon) {
        if (icon == null) {
            throw new IllegalArgumentException("icon 清空请用 clearIcon()");
        }
        this.icon = icon;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 清空图标
     */
    public void clearIcon() {
        this.icon = null;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 更新封面图（清空请用 clearCoverImage）
     *
     * @throws IllegalArgumentException coverImage 为 null 时抛出
     */
    public void updateCoverImage(FileRef coverImage) {
        if (coverImage == null) {
            throw new IllegalArgumentException("coverImage 清空请用 clearCoverImage()");
        }
        this.coverImage = coverImage;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 清空封面图
     */
    public void clearCoverImage() {
        this.coverImage = null;
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
