package com.knowledgegame.core.domain.spec;

import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.vo.SortField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * SortFieldSpec.validate 黑盒契约测试。
 * <p>
 * 与既有 {@link SortFieldSpecTest} 互补：后者覆盖基本分支（null 返回 null / 白名单内返回原值 / 白名单外抛 400）。
 * 本测试聚焦：
 * <ul>
 *   <li>空白名单边界</li>
 *   <li>错误消息精确契约（PRD §4.3：消息必须包含非法字段名 + 允许字段集合的 toString 形式）</li>
 *   <li>字段名与白名单成员的相等性契约（不 trim、严格 equals）</li>
 *   <li>SortFieldSpec 不吞 SortField 构造器抛出的 NPE（值对象不变性由构造器保证）</li>
 * </ul>
 */
class SortFieldSpecBlackBoxTest {

    @Nested
    @DisplayName("空白名单 / 集合边界")
    class EmptyWhitelistContract {

        @Test
        @DisplayName("validate 字段非空但 allowedFields 为 emptySet → 抛 BusinessException(400)")
        void validate_shouldThrowWhenWhitelistIsEmpty() {
            SortField input = new SortField("createdAt", SortField.Direction.DESC);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> SortFieldSpec.validate(input, Collections.emptySet()));

            assertEquals(400, ex.getCode());
        }

        @Test
        @DisplayName("validate 字段非空但 allowedFields 为 null → 抛 NPE（不允许 null 白名单）")
        void validate_shouldThrowNpeWhenWhitelistIsNull() {
            SortField input = new SortField("createdAt", SortField.Direction.DESC);

            // 白名单为 null 是调用方编程错误，spec 不应静默吞掉
            assertThrows(RuntimeException.class,
                    () -> SortFieldSpec.validate(input, null));
        }
    }

    @Nested
    @DisplayName("PRD §4.3 错误消息精确契约")
    class ErrorMessageContract {

        @Test
        @DisplayName("消息格式：'不支持的排序字段: {field}，允许的字段: {allowedFields.toString()}'")
        void validate_errorMessageShouldContainFieldAndAllowedFields() {
            // 用 LinkedHashSet 保证 toString 顺序确定（Set.toString 默认 [a, b, c] 形式）
            Set<String> allowed = new LinkedHashSet<>();
            allowed.add("code");
            allowed.add("name");
            allowed.add("status");

            SortField input = new SortField("hacker_field", SortField.Direction.ASC);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> SortFieldSpec.validate(input, allowed));

            // PRD §4.3：消息必须含非法字段名
            assertEquals(true, ex.getMessage().contains("hacker_field"),
                    "消息应包含非法字段名: " + ex.getMessage());
            // PRD §4.3：消息必须含允许字段提示语
            assertEquals(true, ex.getMessage().contains("允许的字段"),
                    "消息应包含允许字段提示: " + ex.getMessage());
            // Set.toString 输出格式为 [a, b, c]
            assertEquals(true, ex.getMessage().contains("["),
                    "消息应使用 Set.toString 的 [..] 格式: " + ex.getMessage());
        }

        @Test
        @DisplayName("消息中应包含每一个允许字段（不论 Set 顺序）")
        void validate_errorMessageShouldContainEveryAllowedFieldRegardlessOfOrder() {
            Set<String> allowed = Set.of("code", "name", "status", "createdAt", "updatedAt");

            SortField input = new SortField("foo", SortField.Direction.DESC);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> SortFieldSpec.validate(input, allowed));

            for (String field : allowed) {
                assertEquals(true, ex.getMessage().contains(field),
                        "消息应包含允许字段 " + field + ": " + ex.getMessage());
            }
        }

        @Test
        @DisplayName("ex.getCode() 必须等于 400（复用 ResultCode.PARAM_ERROR）")
        void validate_errorCodeShouldBe400() {
            SortField input = new SortField("foo", SortField.Direction.DESC);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> SortFieldSpec.validate(input, Set.of("a")));

            assertEquals(400, ex.getCode());
        }
    }

    @Nested
    @DisplayName("字段相等性契约：严格 equals，不 trim、不忽略大小写")
    class FieldEqualityContract {

        @Test
        @DisplayName("白名单含 'createdAt'，入参 ' createdAt '（带空格）→ 抛 400：不 trim")
        void validate_shouldRejectWhenFieldHasWhitespacePadding() {
            SortField input = new SortField(" createdAt ", SortField.Direction.DESC);

            assertThrows(BusinessException.class,
                    () -> SortFieldSpec.validate(input, Set.of("createdAt")));
        }

        @Test
        @DisplayName("白名单含 'createdAt'，入参 'CREATEDAT'（大写）→ 抛 400：大小写敏感")
        void validate_shouldRejectWhenFieldCaseDiffers() {
            SortField input = new SortField("CREATEDAT", SortField.Direction.DESC);

            assertThrows(BusinessException.class,
                    () -> SortFieldSpec.validate(input, Set.of("createdAt")));
        }

        @Test
        @DisplayName("白名单含空字符串，入参空字符串 → 返回原 SortField（边界：空字段名也是合法字段）")
        void validate_shouldAcceptEmptyStringFieldWhenInWhitelist() {
            SortField input = new SortField("", SortField.Direction.ASC);

            SortField result = SortFieldSpec.validate(input, Set.of("", "createdAt"));

            // 返回原对象（同一实例）
            assertSame(input, result);
        }

        @Test
        @DisplayName("白名单含 'createdAt'，入参 'createdAt' → 返回同一实例（identity）")
        void validate_shouldReturnSameInstanceWhenFieldMatches() {
            SortField input = new SortField("createdAt", SortField.Direction.DESC);

            SortField result = SortFieldSpec.validate(input, Set.of("createdAt", "updatedAt"));

            assertSame(input, result);
        }
    }

    @Nested
    @DisplayName("SortFieldSpec 不应吞 SortField 构造器抛出的 NPE")
    class DoesNotSwallowConstructorNpeContract {

        @Test
        @DisplayName("Direction 为 null 的 SortField 在 validate 前就抛 NPE：spec 不会捕获")
        void validate_shouldPropagateConstructorNpeForNullDirection() {
            // 该 SortField 实例化时 Direction=null 会抛 NPE，无法构造，故 validate 永远拿不到此对象。
            // 这里断言构造器自身防御，而非 spec 的行为，确认 spec 不需要重复防御。
            assertThrows(NullPointerException.class,
                    () -> new SortField("createdAt", null));
        }
    }

    @Test
    @DisplayName("validate(sortField=null, 非空 allowedFields) → null：null 入参永远返回 null")
    void validate_shouldReturnNullWhenSortFieldIsNull() {
        assertNull(SortFieldSpec.validate(null, Set.of("createdAt", "updatedAt")));
    }

    @Test
    @DisplayName("validate(sortField=null, 空白名单) → null：null 入参不受白名单影响")
    void validate_shouldReturnNullWhenSortFieldIsNullAndWhitelistEmpty() {
        assertNull(SortFieldSpec.validate(null, Collections.emptySet()));
    }
}
