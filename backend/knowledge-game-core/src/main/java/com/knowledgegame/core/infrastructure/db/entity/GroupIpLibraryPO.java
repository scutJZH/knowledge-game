package com.knowledgegame.core.infrastructure.db.entity;

import com.knowledgegame.core.domain.model.domainenum.GroupIpLibraryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Getter
@Setter
@Accessors(chain = true)
@Entity
@Table(name = "group_ip_library",
        uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "ip_series_id"}))
public class GroupIpLibraryPO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "ip_series_id", nullable = false)
    private Long ipSeriesId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GroupIpLibraryStatus status;

    @Column(name = "added_at", nullable = false)
    private LocalDateTime addedAt;
}
