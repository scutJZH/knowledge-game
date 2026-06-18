package com.knowledgegame.core.domain.model.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * SortField 值对象单元测试
 * <p>
 * 覆盖：
 * 1. 构造器 null 防御（值对象基础不变性）
 * 2. parse 静态工厂方法的所有边界场景
 */
class SortFieldTest {

    @Test
    @DisplayName("构造器 field 为 null 时抛 NullPointerException")
    void constructor_shouldThrowNpeWhenFieldIsNull() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new SortField(null, SortField.Direction.ASC));
        assertEquals("field 不能为 null", ex.getMessage());
    }

    @Test
    @DisplayName("构造器 direction 为 null 时抛 NullPointerException")
    void constructor_shouldThrowNpeWhenDirectionIsNull() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new SortField("createdAt", null));
        assertEquals("direction 不能为 null", ex.getMessage());
    }

    @Test
    @DisplayName("构造器正常赋值")
    void constructor_shouldAssignFields() {
        SortField sf = new SortField("createdAt", SortField.Direction.DESC);
        assertEquals("createdAt", sf.getField());
        assertEquals(SortField.Direction.DESC, sf.getDirection());
    }

    @Test
    @DisplayName("parse sort=null 时返回 null")
    void parse_shouldReturnNullWhenSortIsNull() {
        assertNull(SortField.parse(null, "asc"));
    }

    @Test
    @DisplayName("parse sort 为空串时返回 null")
    void parse_shouldReturnNullWhenSortIsEmpty() {
        assertNull(SortField.parse("", "asc"));
    }

    @Test
    @DisplayName("parse sort 为空白串时返回 null")
    void parse_shouldReturnNullWhenSortIsBlank() {
        assertNull(SortField.parse("   ", "asc"));
    }

    @Test
    @DisplayName("parse order=asc 时方向为 ASC（大小写不敏感）")
    void parse_shouldReturnAscWhenOrderIsAscLowercase() {
        SortField sf = SortField.parse("createdAt", "asc");
        assertNotNull(sf);
        assertEquals("createdAt", sf.getField());
        assertEquals(SortField.Direction.ASC, sf.getDirection());
    }

    @Test
    @DisplayName("parse order=ASC 时方向为 ASC（大小写不敏感）")
    void parse_shouldReturnAscWhenOrderIsAscUppercase() {
        SortField sf = SortField.parse("createdAt", "ASC");
        assertEquals(SortField.Direction.ASC, sf.getDirection());
    }

    @Test
    @DisplayName("parse order=Asc 混合大小写时方向为 ASC")
    void parse_shouldReturnAscWhenOrderIsMixedCase() {
        SortField sf = SortField.parse("createdAt", "Asc");
        assertEquals(SortField.Direction.ASC, sf.getDirection());
    }

    @Test
    @DisplayName("parse order=desc 时方向为 DESC")
    void parse_shouldReturnDescWhenOrderIsDesc() {
        SortField sf = SortField.parse("createdAt", "desc");
        assertEquals(SortField.Direction.DESC, sf.getDirection());
    }

    @Test
    @DisplayName("parse order=null 时方向为 DESC（默认）")
    void parse_shouldReturnDescWhenOrderIsNull() {
        SortField sf = SortField.parse("createdAt", null);
        assertEquals(SortField.Direction.DESC, sf.getDirection());
    }

    @Test
    @DisplayName("parse order=invalid 时方向为 DESC（非 asc 一律视为 desc）")
    void parse_shouldReturnDescWhenOrderIsInvalid() {
        SortField sf = SortField.parse("createdAt", "invalid");
        assertEquals(SortField.Direction.DESC, sf.getDirection());
    }

    @Test
    @DisplayName("parse order=空串时方向为 DESC（非 asc 一律视为 desc）")
    void parse_shouldReturnDescWhenOrderIsEmpty() {
        SortField sf = SortField.parse("createdAt", "");
        assertEquals(SortField.Direction.DESC, sf.getDirection());
    }
}
