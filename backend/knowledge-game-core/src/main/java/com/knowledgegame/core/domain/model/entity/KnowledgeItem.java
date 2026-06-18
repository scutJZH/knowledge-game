package com.knowledgegame.core.domain.model.entity;

import com.knowledgegame.core.domain.model.domainenum.KnowledgeItemStatus;
import com.knowledgegame.core.domain.model.vo.FileRef;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识条目聚合根（无框架注解）
 */
@Getter
public class KnowledgeItem {

    private Long id;
    private String title;
    private String content;
    private String contentHtml;
    private FileRef coverImage;
    private List<String> tags;
    private int sortOrder;
    private KnowledgeItemStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 创建新知识条目（工厂方法）
     */
    public static KnowledgeItem create(String title, String content, FileRef coverImage,
                                        List<String> tags, int sortOrder) {
        KnowledgeItem item = new KnowledgeItem();
        item.title = title;
        item.content = content;
        item.coverImage = coverImage;
        item.tags = tags;
        item.sortOrder = sortOrder;
        item.status = KnowledgeItemStatus.ACTIVE;
        item.createdAt = LocalDateTime.now();
        item.updatedAt = LocalDateTime.now();
        return item;
    }

    /**
     * 从持久化重建（用于 Repository 加载）
     */
    public static KnowledgeItem reconstruct(Long id, String title, String content,
                                             String contentHtml, FileRef coverImage,
                                             List<String> tags, int sortOrder,
                                             KnowledgeItemStatus status,
                                             LocalDateTime createdAt, LocalDateTime updatedAt) {
        KnowledgeItem item = new KnowledgeItem();
        item.id = id;
        item.title = title;
        item.content = content;
        item.contentHtml = contentHtml;
        item.coverImage = coverImage;
        item.tags = tags;
        item.sortOrder = sortOrder;
        item.status = status;
        item.createdAt = createdAt;
        item.updatedAt = updatedAt;
        return item;
    }

    /**
     * 更新知识条目信息（null 字段不更新；coverImage=null 保留原值）
     */
    public void update(String title, String content, FileRef coverImage,
                       List<String> tags, Integer sortOrder) {
        if (title != null) {
            this.title = title;
        }
        if (content != null) {
            this.content = content;
        }
        if (coverImage != null) {
            this.coverImage = coverImage;
        }
        if (tags != null) {
            this.tags = tags;
        }
        if (sortOrder != null) {
            this.sortOrder = sortOrder;
        }
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 更新渲染后的 HTML 内容
     */
    public void updateContentHtml(String html) {
        this.contentHtml = html;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 软删除（status→INACTIVE）
     */
    public void deactivate() {
        this.status = KnowledgeItemStatus.INACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 重新启用（status→ACTIVE）
     */
    public void activate() {
        this.status = KnowledgeItemStatus.ACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 移动到新的排序位置
     */
    public void moveToSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
        this.updatedAt = LocalDateTime.now();
    }
}
