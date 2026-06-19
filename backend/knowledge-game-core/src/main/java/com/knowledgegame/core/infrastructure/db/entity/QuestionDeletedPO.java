package com.knowledgegame.core.infrastructure.db.entity;

import com.knowledgegame.core.domain.model.domainenum.Difficulty;
import com.knowledgegame.core.domain.model.domainenum.QuestionStatus;
import com.knowledgegame.core.domain.model.domainenum.QuestionType;
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
 * 题目删除快照持久化对象
 * <p>
 * 字段完全镜像 question 表（id 除外，使用独立自增 PK）+ 审计字段。
 * 由 REQ-106 对接回收站时写入，本需求仅建表。
 */
@Entity
@Table(name = "question_deleted", indexes = {
        @Index(name = "idx_qd_original_id", columnList = "original_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionDeletedPO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_id", nullable = false)
    private Long originalId;

    // ==== 镜像 question 字段 ====

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuestionType type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "json")
    private String options;

    @Column(nullable = false, columnDefinition = "json")
    private String answer;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Difficulty difficulty;

    @Column(columnDefinition = "json")
    private String tags;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuestionStatus status;

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
