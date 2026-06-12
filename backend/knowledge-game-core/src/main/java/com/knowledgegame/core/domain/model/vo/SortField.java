package com.knowledgegame.core.domain.model.vo;

/**
 * 通用排序值对象（领域语义，零框架依赖）
 */
public class SortField {

    /**
     * 排序方向
     */
    public enum Direction {
        ASC, DESC
    }

    private final String field;
    private final Direction direction;

    public SortField(String field, Direction direction) {
        this.field = field;
        this.direction = direction;
    }

    /**
     * 默认排序：按创建时间倒序
     */
    public static SortField defaultSort() {
        return new SortField("createdAt", Direction.DESC);
    }

    public String getField() {
        return field;
    }

    public Direction getDirection() {
        return direction;
    }
}