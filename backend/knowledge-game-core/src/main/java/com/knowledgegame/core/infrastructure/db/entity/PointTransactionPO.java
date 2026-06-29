package com.knowledgegame.core.infrastructure.db.entity;

import com.knowledgegame.core.domain.model.domainenum.ReferenceType;
import com.knowledgegame.core.domain.model.domainenum.TxType;
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
 * 积分流水持久化对象（JPA Entity，仅在 infrastructure 层）
 */
@Entity
@Table(name = "point_transaction", indexes = {
        @Index(name = "idx_group_user_created", columnList = "group_id,user_id,created_at"),
        @Index(name = "idx_user_created", columnList = "user_id,created_at"),
        @Index(name = "idx_group_created", columnList = "group_id,created_at"),
        @Index(name = "idx_reference", columnList = "reference_type,reference_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PointTransactionPO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, columnDefinition = "VARCHAR(10)")
    private TxType type;

    @Column(nullable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false, length = 30, columnDefinition = "VARCHAR(30)")
    private ReferenceType referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "balance_after", nullable = false)
    private int balanceAfter;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
