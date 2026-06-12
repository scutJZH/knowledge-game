package com.knowledgegame.core.domain.model.vo;

import java.util.Objects;

/**
 * 题目选项值对象（不可变）
 */
public class QuestionOption {

    private final String key;
    private final String content;

    private QuestionOption(String key, String content) {
        this.key = key;
        this.content = content;
    }

    /**
     * 静态工厂方法
     */
    public static QuestionOption of(String key, String content) {
        return new QuestionOption(key, content);
    }

    public String getKey() {
        return key;
    }

    public String getContent() {
        return content;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QuestionOption that = (QuestionOption) o;
        return Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }
}