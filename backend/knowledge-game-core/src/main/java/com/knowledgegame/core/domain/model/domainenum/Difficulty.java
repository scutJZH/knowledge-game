package com.knowledgegame.core.domain.model.domainenum;

import lombok.Getter;

/**
 * 难度等级枚举
 */
@Getter
public enum Difficulty {

    EASY(1, "简单"),
    MEDIUM(2, "中等"),
    HARD(3, "困难");

    private final int level;
    private final String label;

    Difficulty(int level, String label) {
        this.level = level;
        this.label = label;
    }

    /**
     * 根据 level 值获取枚举
     */
    public static Difficulty fromLevel(int level) {
        for (Difficulty d : values()) {
            if (d.level == level) {
                return d;
            }
        }
        throw new IllegalArgumentException("无效的难度等级: " + level);
    }
}