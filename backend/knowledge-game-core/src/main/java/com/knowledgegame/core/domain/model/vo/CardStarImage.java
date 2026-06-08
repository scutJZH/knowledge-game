package com.knowledgegame.core.domain.model.vo;

import java.util.Objects;

/**
 * 卡牌星级图片值对象（不可变，同一模板内 starLevel 唯一）
 */
public class CardStarImage {

    private final int starLevel;
    private final String imageUrl;

    /**
     * 私有构造器
     */
    private CardStarImage(int starLevel, String imageUrl) {
        this.starLevel = starLevel;
        this.imageUrl = imageUrl;
    }

    /**
     * 静态工厂方法
     */
    public static CardStarImage create(int starLevel, String imageUrl) {
        return new CardStarImage(starLevel, imageUrl);
    }

    public int getStarLevel() {
        return starLevel;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    /**
     * 相等性由 starLevel 决定（同一模板内星级唯一）
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CardStarImage that = (CardStarImage) o;
        return starLevel == that.starLevel;
    }

    @Override
    public int hashCode() {
        return Objects.hash(starLevel);
    }
}
