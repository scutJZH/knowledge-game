package com.knowledgegame.core.domain.model.vo;

import java.util.Objects;

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

    /**
     * 构造器：field 与 direction 均不可为 null（值对象基础不变性）
     *
     * @param field     排序字段名（PO 字段驼峰式，如 createdAt）
     * @param direction 排序方向（ASC/DESC）
     * @throws NullPointerException field 或 direction 为 null
     */
    public SortField(String field, Direction direction) {
        this.field = Objects.requireNonNull(field, "field 不能为 null");
        this.direction = Objects.requireNonNull(direction, "direction 不能为 null");
    }

    /**
     * 解析 sort/order 字符串为 SortField
     * <p>
     * 行为约定：
     * <ul>
     *   <li>sort 为 null/空白 → 返回 null（由调用方决定默认排序）</li>
     *   <li>sort 非空 → 方向按 asc/desc 大小写不敏感解析；非 asc 一律视为 desc（含 null/空串/任意非法值）</li>
     * </ul>
     *
     * @param sort  排序字段名（PO 字段驼峰式）
     * @param order 排序方向字符串（asc/desc，大小写不敏感）
     * @return SortField 实例；sort 为 null/空白时返回 null
     */
    public static SortField parse(String sort, String order) {
        if (sort == null || sort.isBlank()) {
            return null;
        }
        Direction direction = "asc".equalsIgnoreCase(order)
                ? Direction.ASC : Direction.DESC;
        return new SortField(sort, direction);
    }

    public String getField() {
        return field;
    }

    public Direction getDirection() {
        return direction;
    }
}
