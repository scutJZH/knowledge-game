package com.knowledgegame.core.domain.model.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * FileRef 值对象单元测试
 */
class FileRefTest {

    @Nested
    @DisplayName("工厂方法 of(Long, String)")
    class OfTests {

        @Test
        @DisplayName("(null, null) 应返回空 FileRef")
        void shouldReturnEmptyFileRefWhenBothNull() {
            FileRef ref = FileRef.of(null, null);
            assertNotNull(ref);
            assertNull(ref.fileId());
            assertNull(ref.url());
        }

        @Test
        @DisplayName("(Long, String) 应返回完整 FileRef")
        void shouldReturnFullFileRef() {
            FileRef ref = FileRef.of(1L, "/static/test.png");
            assertNotNull(ref);
            assertEquals(1L, ref.fileId());
            assertEquals("/static/test.png", ref.url());
        }

        @Test
        @DisplayName("(Long, null) 应抛 IllegalArgumentException")
        void shouldThrowOnFileIdOnly() {
            assertThrows(IllegalArgumentException.class,
                    () -> FileRef.of(1L, null));
        }

        @Test
        @DisplayName("(null, String) 应抛 IllegalArgumentException")
        void shouldThrowOnUrlOnly() {
            assertThrows(IllegalArgumentException.class,
                    () -> FileRef.of(null, "/static/test.png"));
        }
    }

    @Nested
    @DisplayName("equals 与 hashCode")
    class EqualsAndHashCodeTests {

        @Test
        @DisplayName("相同 fileId 和 url 应相等")
        void shouldBeEqualWhenSameFileIdAndUrl() {
            FileRef ref1 = FileRef.of(1L, "/static/test.png");
            FileRef ref2 = FileRef.of(1L, "/static/test.png");
            assertEquals(ref1, ref2);
            assertEquals(ref1.hashCode(), ref2.hashCode());
        }

        @Test
        @DisplayName("不同 fileId 应不相等")
        void shouldNotBeEqualWhenDifferentFileId() {
            FileRef ref1 = FileRef.of(1L, "/static/test.png");
            FileRef ref2 = FileRef.of(2L, "/static/test.png");
            assertNotEquals(ref1, ref2);
        }

        @Test
        @DisplayName("不同 url 应不相等")
        void shouldNotBeEqualWhenDifferentUrl() {
            FileRef ref1 = FileRef.of(1L, "/static/test.png");
            FileRef ref2 = FileRef.of(1L, "/static/other.png");
            assertNotEquals(ref1, ref2);
        }

        @Test
        @DisplayName("两个空 FileRef 应相等")
        void shouldBeEqualWhenBothEmpty() {
            FileRef ref1 = FileRef.of(null, null);
            FileRef ref2 = FileRef.of(null, null);
            assertEquals(ref1, ref2);
            assertEquals(ref1.hashCode(), ref2.hashCode());
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("完整 FileRef 的 toString 应包含 fileId 和 url")
        void shouldContainFileIdAndUrl() {
            FileRef ref = FileRef.of(1L, "/static/test.png");
            String str = ref.toString();
            assertEquals("FileRef{fileId=1, url='/static/test.png'}", str);
        }

        @Test
        @DisplayName("空 FileRef 的 toString 应显示 null")
        void shouldShowNullForEmpty() {
            FileRef ref = FileRef.of(null, null);
            String str = ref.toString();
            assertEquals("FileRef{fileId=null, url='null'}", str);
        }
    }

    @Nested
    @DisplayName("边界值")
    class BoundaryTests {

        @Test
        @DisplayName("fileId=0 应抛 IllegalArgumentException")
        void shouldRejectZeroFileId() {
            assertThrows(IllegalArgumentException.class,
                    () -> FileRef.of(0L, "/static/test.png"));
        }

        @Test
        @DisplayName("url 空串应抛 IllegalArgumentException")
        void shouldRejectEmptyUrl() {
            assertThrows(IllegalArgumentException.class,
                    () -> FileRef.of(1L, ""));
        }
    }
}
