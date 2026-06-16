package com.knowledgegame.core.domain.model.entity;

import com.knowledgegame.core.domain.model.domainenum.CardRarity;
import com.knowledgegame.core.domain.model.domainenum.CardTemplateStatus;
import com.knowledgegame.core.domain.model.vo.FileRef;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 卡牌模板聚合根（无框架注解）
 */
@Getter
public class CardTemplate {

    private Long id;
    private Long ipSeriesId;
    private String code;
    private String name;
    private CardRarity rarity;
    private String description;
    private CardTemplateStatus status;
    private FileRef image;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 创建新卡牌模板（工厂方法）
     */
    public static CardTemplate create(Long ipSeriesId, String code, String name,
                                      CardRarity rarity, String description,
                                      CardTemplateStatus status, FileRef image) {
        CardTemplate template = new CardTemplate();
        template.ipSeriesId = ipSeriesId;
        template.code = code;
        template.name = name;
        template.rarity = rarity;
        template.description = description;
        template.status = status;
        template.image = image;
        template.createdAt = LocalDateTime.now();
        template.updatedAt = LocalDateTime.now();
        return template;
    }

    /**
     * 从持久化重建（用于 Repository 加载）
     */
    public static CardTemplate reconstruct(Long id, Long ipSeriesId, String code, String name,
                                           CardRarity rarity, String description,
                                           CardTemplateStatus status, FileRef image,
                                           LocalDateTime createdAt, LocalDateTime updatedAt) {
        CardTemplate template = new CardTemplate();
        template.id = id;
        template.ipSeriesId = ipSeriesId;
        template.code = code;
        template.name = name;
        template.rarity = rarity;
        template.description = description;
        template.status = status;
        template.image = image;
        template.createdAt = createdAt;
        template.updatedAt = updatedAt;
        return template;
    }

    /**
     * 更新基础字段（null 不修改）
     */
    public void update(String code, String name, CardRarity rarity,
                       String description, CardTemplateStatus status, FileRef image) {
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
        if (image != null) {
            this.image = image;
        }
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
