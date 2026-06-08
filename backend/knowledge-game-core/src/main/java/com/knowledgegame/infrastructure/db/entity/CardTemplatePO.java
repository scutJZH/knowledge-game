package com.knowledgegame.infrastructure.db.entity;

import com.knowledgegame.domain.model.domainenum.CardRarity;
import com.knowledgegame.domain.model.domainenum.CardTemplateStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 卡牌模板持久化对象（JPA Entity，仅在 infrastructure 层）
 */
@Entity
@Table(name = "card_template")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardTemplatePO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ip_series_id", nullable = false)
    private Long ipSeriesId;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CardRarity rarity;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CardTemplateStatus status;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true,
            mappedBy = "cardTemplate", fetch = FetchType.LAZY)
    @Builder.Default
    private List<CardStarImagePO> starImages = new ArrayList<>();

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
