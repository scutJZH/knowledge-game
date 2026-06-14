package com.knowledgegame.admin.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * FilePathMapping 单元测试（管理端）
 */
class FilePathMappingTest {

    @Nested
    @DisplayName("toBasePath")
    class ToBasePathTests {

        @Test
        @DisplayName("已注册的 bizType 返回对应 basePath")
        void shouldReturnBasePath_forRegisteredBizType() {
            assertEquals("ip-series", FilePathMapping.toBasePath("IP_SERIES"));
            assertEquals("card-template", FilePathMapping.toBasePath("CARD_TEMPLATE"));
            assertEquals("category-icon", FilePathMapping.toBasePath("CATEGORY_ICON"));
            assertEquals("category-cover", FilePathMapping.toBasePath("CATEGORY_COVER"));
        }

        @Test
        @DisplayName("未注册的 bizType 返回 null")
        void shouldReturnNull_forUnknownBizType() {
            assertNull(FilePathMapping.toBasePath("avatar"));
            assertNull(FilePathMapping.toBasePath("card-star-image"));
        }

        @Test
        @DisplayName("null 入参抛出 NullPointerException")
        void shouldThrowNPE_forNullBizType() {
            assertThrows(NullPointerException.class,
                    () -> FilePathMapping.toBasePath(null));
        }

        @Test
        @DisplayName("空字符串返回 null")
        void shouldReturnNull_forEmptyBizType() {
            assertNull(FilePathMapping.toBasePath(""));
        }
    }
}
