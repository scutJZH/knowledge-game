package com.knowledgegame.core.domain.model.vo;

import com.knowledgegame.core.domain.model.domainenum.KnowledgeItemStatus;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识条目摘要值对象（列表投影，不含正文 content/contentHtml）
 */
@Getter
public class KnowledgeItemSummary {

    private final Long id;
    private final String title;
    private final FileRef coverImage;
    private final List<String> tags;
    private final int sortOrder;
    private final KnowledgeItemStatus status;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private KnowledgeItemSummary(Long id, String title, FileRef coverImage,
                                 List<String> tags, int sortOrder,
                                 KnowledgeItemStatus status,
                                 LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.title = title;
        this.coverImage = coverImage;
        this.tags = tags;
        this.sortOrder = sortOrder;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 从持久化重建（用于 Repository 加载）
     */
    public static KnowledgeItemSummary reconstruct(Long id, String title,
                                                    FileRef coverImage,
                                                    List<String> tags, int sortOrder,
                                                    KnowledgeItemStatus status,
                                                    LocalDateTime createdAt,
                                                    LocalDateTime updatedAt) {
        return new KnowledgeItemSummary(id, title, coverImage, tags, sortOrder,
                status, createdAt, updatedAt);
    }
}
