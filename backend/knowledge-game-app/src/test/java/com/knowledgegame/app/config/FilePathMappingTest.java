package com.knowledgegame.app.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * FilePathMapping 单元测试（用户端）
 */
class FilePathMappingTest {

    @Nested
    @DisplayName("toBasePath")
    class ToBasePathTests {

        @Test
        @DisplayName("任意 bizType 均返回 null（当前映射为空）")
        void shouldReturnNull_forAnyBizType() {
            assertNull(FilePathMapping.toBasePath("ip-series"));
            assertNull(FilePathMapping.toBasePath("avatar"));
            assertNull(FilePathMapping.toBasePath("card-star-image"));
        }

        @Test
        @DisplayName("null 入参抛出 NullPointerException")
        void shouldThrowNPE_forNullBizType() {
            org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
                    () -> FilePathMapping.toBasePath(null));
        }

        @Test
        @DisplayName("空字符串返回 null")
        void shouldReturnNull_forEmptyBizType() {
            assertNull(FilePathMapping.toBasePath(""));
        }
    }
}
