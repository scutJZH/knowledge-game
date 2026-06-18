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
     * 更新必填字段（不支持清空）。可清空字段（description/image）请用对应的 updateXxx / clearXxx 方法
     */
    public void update(String code, String name, CardRarity rarity, CardTemplateStatus status) {
        if (code != null) {
            this.code = code;
        }
        if (name != null) {
            this.name = name;
        }
        if (rarity != null) {
            this.rarity = rarity;
        }
        if (status != null) {
            this.status = status;
        }
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 更新描述（清空请用 clearDescription）
     *
     * @throws IllegalArgumentException description 为 null 时抛出
     */
    public void updateDescription(String description) {
        if (description == null) {
            throw new IllegalArgumentException("description 清空请用 clearDescription()");
        }
        this.description = description;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 清空描述
     */
    public void clearDescription() {
        this.description = null;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 更新卡面图（清空请用 clearImage）
     *
     * @throws IllegalArgumentException image 为 null 时抛出
     */
    public void updateImage(FileRef image) {
        if (image == null) {
            throw new IllegalArgumentException("image 清空请用 clearImage()");
        }
        this.image = image;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 清空卡面图
     */
    public void clearImage() {
        this.image = null;
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
