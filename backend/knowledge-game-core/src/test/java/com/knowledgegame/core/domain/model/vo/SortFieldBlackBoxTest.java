package com.knowledgegame.core.domain.model.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * SortField.parse 黑盒契约测试。
 * <p>
 * 与既有 {@link SortFieldTest} 互补：后者覆盖基本分支（null/空/空白/asc/Asc/ASC/desc/null/invalid/空串），
 * 本测试聚焦 PRD §4.1 / §5.1 中"非 asc 一律视为 desc（含特殊字符、空格、长前缀）"
 * 的边界契约，以及 isBlank 对各种空白字符的判定。
 */
class SortFieldBlackBoxTest {

    private static final String FIELD = "createdAt";

    @Nested
    @DisplayName("order 大小写不敏感契约：仅完全等同 asc（任意大小写）才解析为 ASC")
    class OrderCaseInsensitiveContract {

        @Test
        @DisplayName("parse order=ASC（大写）→ ASC")
        void parse_shouldReturnAscWhenOrderIsUpperCase() {
            SortField sf = SortField.parse(FIELD, "ASC");
            assertNotNull(sf);
            assertEquals(SortField.Direction.ASC, sf.getDirection());
            assertEquals(FIELD, sf.getField());
        }

        @Test
        @DisplayName("parse order=aSc（任意大小写混合）→ ASC")
        void parse_shouldReturnAscWhenOrderIsMixedCase() {
            assertEquals(SortField.Direction.ASC, SortField.parse(FIELD, "aSc").getDirection());
            assertEquals(SortField.Direction.ASC, SortField.parse(FIELD, "aSC").getDirection());
            assertEquals(SortField.Direction.ASC, SortField.parse(FIELD, "ASc").getDirection());
        }
    }

    @Nested
    @DisplayName("非 asc 一律视为 DESC 契约：严格等于，不前缀匹配、不 trim")
    class NonAscAlwaysDescContract {

        @Test
        @DisplayName("parse order='  asc  '（带前后空格）→ DESC：不 trim，严格 equalsIgnoreCase")
        void parse_shouldReturnDescWhenOrderHasWhitespacePadding() {
            assertEquals(SortField.Direction.DESC, SortField.parse(FIELD, "  asc  ").getDirection());
        }

        @Test
        @DisplayName("parse order='asc\\0'（含 NUL 字符）→ DESC")
        void parse_shouldReturnDescWhenOrderContainsNulChar() {
            assertEquals(SortField.Direction.DESC, SortField.parse(FIELD, "asc\0").getDirection());
        }

        @Test
        @DisplayName("parse order='ASCENDING'（长前缀）→ DESC：不前缀匹配")
        void parse_shouldReturnDescWhenOrderIsAscendingLongPrefix() {
            assertEquals(SortField.Direction.DESC, SortField.parse(FIELD, "ASCENDING").getDirection());
            assertEquals(SortField.Direction.DESC, SortField.parse(FIELD, "ascending").getDirection());
        }

        @Test
        @DisplayName("parse order='ascc'（asc + 一个 c）→ DESC：不容错")
        void parse_shouldReturnDescWhenOrderIsNearMiss() {
            assertEquals(SortField.Direction.DESC, SortField.parse(FIELD, "ascc").getDirection());
            assertEquals(SortField.Direction.DESC, SortField.parse(FIELD, "acc").getDirection());
        }

        @Test
        @DisplayName("parse order='desc'（显式小写）→ DESC")
        void parse_shouldReturnDescWhenOrderIsDescLowercase() {
            assertEquals(SortField.Direction.DESC, SortField.parse(FIELD, "desc").getDirection());
        }

        @Test
        @DisplayName("parse order='DESC'（显式大写）→ DESC")
        void parse_shouldReturnDescWhenOrderIsDescUppercase() {
            assertEquals(SortField.Direction.DESC, SortField.parse(FIELD, "DESC").getDirection());
        }

        @Test
        @DisplayName("parse order='Desc'（混合大小写）→ DESC")
        void parse_shouldReturnDescWhenOrderIsDescMixedCase() {
            assertEquals(SortField.Direction.DESC, SortField.parse(FIELD, "Desc").getDirection());
        }

        @Test
        @DisplayName("parse order=''（空串）→ DESC")
        void parse_shouldReturnDescWhenOrderIsEmpty() {
            assertEquals(SortField.Direction.DESC, SortField.parse(FIELD, "").getDirection());
        }

        @Test
        @DisplayName("parse order='乱码'（中文）→ DESC")
        void parse_shouldReturnDescWhenOrderIsChinese() {
            assertEquals(SortField.Direction.DESC, SortField.parse(FIELD, "乱码").getDirection());
        }

        @Test
        @DisplayName("parse order='1'（数字）→ DESC")
        void parse_shouldReturnDescWhenOrderIsNumeric() {
            assertEquals(SortField.Direction.DESC, SortField.parse(FIELD, "1").getDirection());
        }
    }

    @Nested
    @DisplayName("sort 字段 isBlank 判定契约：所有 Unicode 空白字符均视为 blank")
    class SortIsBlankContract {

        @Test
        @DisplayName("parse sort='\\t'（制表符）+ order=asc → null")
        void parse_shouldReturnNullWhenSortIsTab() {
            assertNull(SortField.parse("\t", "asc"));
        }

        @Test
        @DisplayName("parse sort='\\n'（换行）+ order=asc → null")
        void parse_shouldReturnNullWhenSortIsNewline() {
            assertNull(SortField.parse("\n", "asc"));
        }

        @Test
        @DisplayName("parse sort='\\r\\n'（CRLF）+ order=asc → null")
        void parse_shouldReturnNullWhenSortIsCrlf() {
            assertNull(SortField.parse("\r\n", "asc"));
        }

        @Test
        @DisplayName("parse sort=null + order=null → null（全 null）")
        void parse_shouldReturnNullWhenBothNull() {
            assertNull(SortField.parse(null, null));
        }

        @Test
        @DisplayName("parse sort=null + order=乱码 → null：sort 判定优先于 order")
        void parse_shouldReturnNullWhenSortIsNullRegardlessOfOrder() {
            assertNull(SortField.parse(null, "乱码"));
            assertNull(SortField.parse(null, ""));
            assertNull(SortField.parse(null, "DESC"));
        }
    }

    @Test
    @DisplayName("parse 字段名原样保留（不 trim 不大小写转换）")
    void parse_shouldPreserveFieldExactlyAsProvided() {
        // 字段含大小写、下划线、点号时应原样保留，校验由 SortFieldSpec 白名单决定
        assertEquals("CreatedAt", SortField.parse("CreatedAt", "asc").getField());
        assertEquals("created_at", SortField.parse("created_at", "asc").getField());
        assertEquals("a.b.c", SortField.parse("a.b.c", "asc").getField());
    }

    @Test
    @DisplayName("parse 两次对同一入参返回的 SortField 应语义相等（值对象不变性）")
    void parse_shouldReturnSemanticallyEqualInstances() {
        SortField a = SortField.parse(FIELD, "asc");
        SortField b = SortField.parse(FIELD, "ASC");
        // 值对象语义相等：field 与 direction 都相同
        assertEquals(a.getField(), b.getField());
        assertEquals(a.getDirection(), b.getDirection());
    }
}
