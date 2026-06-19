package com.knowledgegame.core.infrastructure.db.entity;

import com.knowledgegame.core.domain.model.domainenum.KnowledgeItemStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 知识条目删除快照持久化对象
 * <p>
 * 字段完全镜像 knowledge_item 表（id 除外，使用独立自增 PK）+ 审计字段。
 * 由 REQ-108 对接回收站时写入，本需求仅建表。
 */
@Entity
@Table(name = "knowledge_item_deleted", indexes = {
        @Index(name = "idx_kid_original_id", columnList = "original_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnowledgeItemDeletedPO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_id", nullable = false)
    private Long originalId;

    // ==== 镜像 knowledge_item 字段 ====

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    @Column(name = "content_html", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String contentHtml;

    @Column(name = "cover_image_file_id")
    private Long coverImageFileId;

    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    @Column(columnDefinition = "json")
    private String tags;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private KnowledgeItemStatus status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ==== 回收站审计字段 ====

    @Column(name = "related_data", columnDefinition = "json")
    private String relatedData;

    @Column(name = "deleted_by", nullable = false, length = 64)
    private String deletedBy;

    @Column(name = "deleted_at", nullable = false)
    private LocalDateTime deletedAt;
}
