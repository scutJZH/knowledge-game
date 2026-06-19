package com.knowledgegame.core.infrastructure.db.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

import com.knowledgegame.core.domain.model.domainenum.ResourceType;

/**
 * 回收站总览表持久化对象
 * <p>
 * 字段设计：无 updated_at / created_at（回收站记录创建后不可修改，deletedAt 即记录创建时间）。
 * originalCreatedBy / originalUpdatedBy 为预留字段，现有 PO 体系无审计字段，本需求交付时写入为 NULL。
 */
@Entity
@Table(name = "recycle_bin",
        uniqueConstraints = @UniqueConstraint(columnNames = {"resource_type", "original_id"}),
        indexes = {
                @Index(name = "idx_resource_type", columnList = "resource_type"),
                @Index(name = "idx_restore_deadline", columnList = "restore_deadline")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecycleBinItemPO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 50)
    private ResourceType resourceType;

    @Column(name = "original_id", nullable = false)
    private Long originalId;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "original_created_at")
    private LocalDateTime originalCreatedAt;

    @Column(name = "original_updated_at")
    private LocalDateTime originalUpdatedAt;

    @Column(name = "original_created_by", length = 64)
    private String originalCreatedBy;

    @Column(name = "original_updated_by", length = 64)
    private String originalUpdatedBy;

    @Column(name = "deleted_by", nullable = false, length = 64)
    private String deletedBy;

    @Column(name = "deleted_at", nullable = false)
    private LocalDateTime deletedAt;

    @Column(name = "restore_deadline", nullable = false)
    private LocalDateTime restoreDeadline;
}
