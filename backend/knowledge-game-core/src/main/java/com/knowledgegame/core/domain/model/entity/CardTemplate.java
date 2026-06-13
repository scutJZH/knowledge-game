package com.knowledgegame.core.domain.model.entity;

import com.knowledgegame.core.domain.model.domainenum.CardRarity;
import com.knowledgegame.core.domain.model.domainenum.CardTemplateStatus;
import com.knowledgegame.core.domain.model.vo.CardStarImage;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 卡牌模板聚合根（无框架注解）
 */
@Getter
public class CardTemplate {

    /** 默认图片 URL */
    private static final String DEFAULT_IMAGE_URL = "";

    private Long id;
    private Long ipSeriesId;
    private String code;
    private String name;
    private CardRarity rarity;
    private String description;
    private CardTemplateStatus status;
    private List<CardStarImage> starImages;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 创建新卡牌模板（工厂方法）
     * starImages 为空时自动生成 1 星默认图片
     */
    public static CardTemplate create(Long ipSeriesId, String code, String name,
                                      CardRarity rarity, String description,
                                      CardTemplateStatus status,
                                      List<CardStarImage> starImages) {
        CardTemplate template = new CardTemplate();
        template.ipSeriesId = ipSeriesId;
        template.code = code;
        template.name = name;
        template.rarity = rarity;
        template.description = description;
        template.status = status;
        template.starImages = new ArrayList<>();
        template.createdAt = LocalDateTime.now();
        template.updatedAt = LocalDateTime.now();

        if (starImages == null || starImages.isEmpty()) {
            // 空图片时自动生成 1 星默认图片
            template.starImages.add(CardStarImage.create(1, DEFAULT_IMAGE_URL));
        } else {
            template.starImages.addAll(starImages);
        }

        return template;
    }

    /**
     * 从持久化重建（用于 Repository 加载）
     */
    public static CardTemplate reconstruct(Long id, Long ipSeriesId, String code, String name,
                                           CardRarity rarity, String description,
                                           CardTemplateStatus status,
                                           List<CardStarImage> starImages,
                                           LocalDateTime createdAt, LocalDateTime updatedAt) {
        CardTemplate template = new CardTemplate();
        template.id = id;
        template.ipSeriesId = ipSeriesId;
        template.code = code;
        template.name = name;
        template.rarity = rarity;
        template.description = description;
        template.status = status;
        template.starImages = starImages != null ? new ArrayList<>(starImages) : new ArrayList<>();
        template.createdAt = createdAt;
        template.updatedAt = updatedAt;
        return template;
    }

    /**
     * 更新基础字段（null 不修改）
     */
    public void update(String code, String name, CardRarity rarity,
                       String description, CardTemplateStatus status) {
        if (code != null) {
            this.code = code;
        }
        if (name != null) {
            this.name = name;
        }
        if (rarity != null) {
            this.rarity = rarity;
        }
        if (description != null) {
            this.description = description;
        }
        if (status != null) {
            this.status = status;
        }
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 全量替换星级图片
     */
    public void replaceStarImages(List<CardStarImage> starImages) {
        this.starImages.clear();
        if (starImages != null) {
            this.starImages.addAll(starImages);
        }
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 添加或替换单个星级图片（按 starLevel 匹配）
     */
    public void addOrUpdateStarImage(CardStarImage starImage) {
        // 移除同 starLevel 的旧图片
        this.starImages.removeIf(img -> img.getStarLevel() == starImage.getStarLevel());
        this.starImages.add(starImage);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 软删除
     */
    public void deactivate() {
        this.status = CardTemplateStatus.INACTIVE;
        this.updatedAt = LocalDateTime.now();
    }
}
