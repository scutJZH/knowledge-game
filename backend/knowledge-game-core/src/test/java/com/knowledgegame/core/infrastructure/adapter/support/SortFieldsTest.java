package com.knowledgegame.core.infrastructure.adapter.support;

import com.knowledgegame.core.domain.model.vo.SortField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SortFields 工具类单元测试
 * <p>
 * 覆盖 ASC/DESC 两个方向的转换正确性
 */
class SortFieldsTest {

    @Test
    @DisplayName("toSpringSort ASC 方向正确转换")
    void toSpringSort_shouldConvertAscCorrectly() {
        SortField sf = new SortField("createdAt", SortField.Direction.ASC);
        Sort sort = SortFields.toSpringSort(sf);

        Sort.Order order = sort.getOrderFor("createdAt");
        assertEquals(true, order != null, "Sort 应包含 createdAt 字段");
        assertEquals(Sort.Direction.ASC, order.getDirection());
    }

    @Test
    @DisplayName("toSpringSort DESC 方向正确转换")
    void toSpringSort_shouldConvertDescCorrectly() {
        SortField sf = new SortField("updatedAt", SortField.Direction.DESC);
        Sort sort = SortFields.toSpringSort(sf);

        Sort.Order order = sort.getOrderFor("updatedAt");
        assertEquals(true, order != null, "Sort 应包含 updatedAt 字段");
        assertEquals(Sort.Direction.DESC, order.getDirection());
    }

    @Test
    @DisplayName("toSpringSort 多次调用返回独立 Sort 实例（无状态）")
    void toSpringSort_shouldReturnIndependentInstance() {
        SortField sf = new SortField("name", SortField.Direction.ASC);
        Sort s1 = SortFields.toSpringSort(sf);
        Sort s2 = SortFields.toSpringSort(sf);

        // 不同实例但语义相等
        assertEquals(s1, s2);
    }
}
