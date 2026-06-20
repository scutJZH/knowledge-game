package com.knowledgegame.core.domain.model.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InviteCodeTest {

    @Nested
    @DisplayName("generate（随机生成）")
    class GenerateTests {

        @Test
        @DisplayName("应生成 8 位全 Crockford 字符集的邀请码")
        void generate_returns8CharsCrockford() {
            // 多次生成验证
            for (int i = 0; i < 100; i++) {
                InviteCode code = InviteCode.generate();
                assertNotNull(code);
                String value = code.getValue();
                assertEquals(8, value.length(), "长度应为 8，实际: " + value);
                String crockfordChars = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
                for (char c : value.toCharArray()) {
                    assertTrue(crockfordChars.indexOf(c) >= 0,
                            "非法 Crockford 字符 '" + c + "' in " + value);
                }
            }
        }
    }

    @Nested
    @DisplayName("of（格式校验）")
    class OfTests {

        @Test
        @DisplayName("合法 8 位 Crockford 邀请码应构造成功")
        void of_validCode_succeeds() {
            InviteCode code = InviteCode.of("ABC12345");
            assertEquals("ABC12345", code.getValue());
        }

        @Test
        @DisplayName("少于 8 位应抛 IllegalArgumentException")
        void of_tooShort_throwsIllegalArgument() {
            assertThrows(IllegalArgumentException.class, () -> InviteCode.of("ABC123"));
        }

        @Test
        @DisplayName("多于 8 位应抛 IllegalArgumentException")
        void of_tooLong_throwsIllegalArgument() {
            assertThrows(IllegalArgumentException.class, () -> InviteCode.of("ABC123456"));
        }

        @Test
        @DisplayName("含 I（排除字符）应抛 IllegalArgumentException")
        void of_containsExcludedCharI_throwsIllegalArgument() {
            assertThrows(IllegalArgumentException.class, () -> InviteCode.of("ABCI1234"));
        }

        @Test
        @DisplayName("含 L（排除字符）应抛 IllegalArgumentException")
        void of_containsExcludedCharL_throwsIllegalArgument() {
            assertThrows(IllegalArgumentException.class, () -> InviteCode.of("ABCL1234"));
        }

        @Test
        @DisplayName("含 O（排除字符）应抛 IllegalArgumentException")
        void of_containsExcludedCharO_throwsIllegalArgument() {
            assertThrows(IllegalArgumentException.class, () -> InviteCode.of("ABCO1234"));
        }

        @Test
        @DisplayName("含 U（排除字符）应抛 IllegalArgumentException")
        void of_containsExcludedCharU_throwsIllegalArgument() {
            assertThrows(IllegalArgumentException.class, () -> InviteCode.of("ABCU1234"));
        }

        @Test
        @DisplayName("null 应抛 IllegalArgumentException")
        void of_null_throwsIllegalArgument() {
            assertThrows(IllegalArgumentException.class, () -> InviteCode.of(null));
        }
    }

    @Nested
    @DisplayName("matches（匹配判断）")
    class MatchesTests {

        @Test
        @DisplayName("相同值应返回 true")
        void matches_sameValue_true() {
            InviteCode code = InviteCode.of("ABC12345");
            assertTrue(code.matches("ABC12345"));
        }

        @Test
        @DisplayName("不同值应返回 false")
        void matches_differentValue_false() {
            InviteCode code = InviteCode.of("ABC12345");
            assertFalse(code.matches("XYZ67890"));
        }
    }

    @Test
    @DisplayName("equals/hashCode：相同值应相等")
    void equalsAndHashCode_sameValue_equal() {
        InviteCode code1 = InviteCode.of("ABC12345");
        InviteCode code2 = InviteCode.of("ABC12345");
        assertEquals(code1, code2);
        assertEquals(code1.hashCode(), code2.hashCode());
    }
}
