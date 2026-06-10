package com.knowledgegame.core.infrastructure.db.entity;

import com.knowledgegame.core.domain.model.domainenum.KnowledgeCategoryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 知识点分类持久化对象（JPA Entity，仅在 infrastructure 层）
 */
@Entity
@Table(name = "knowledge_category",
        uniqueConstraints = @UniqueConstraint(columnNames = {"parent_id", "name"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnowledgeCategoryPO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "icon_url", length = 500)
    private String iconUrl;

    @Column(length = 20)
    private String color;

    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private KnowledgeCategoryStatus status;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
