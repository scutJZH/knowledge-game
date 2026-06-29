package com.knowledgegame.core.infrastructure.db.entity;

import com.knowledgegame.core.domain.model.domainenum.GroupRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 群组成员持久化对象（JPA Entity，仅在 infrastructure 层）
 */
@Entity
@Table(name = "group_member", uniqueConstraints = {
        @UniqueConstraint(name = "uk_group_member_group_user", columnNames = {"group_id", "user_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupMemberPO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GroupRole role;

    @Column(nullable = false)
    @Builder.Default
    private int points = 0;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version;
}
