package com.knowledgegame.core.domain.spec;

import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.vo.SortField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * SortFieldSpec 单元测试
 * <p>
 * 覆盖 null 入参、白名单内字段、白名单外字段（含错误消息契约）
 */
class SortFieldSpecTest {

    private static final Map<String, String> ALLOWED_FIELDS = new LinkedHashMap<>() {{
        put("code", "编码");
        put("name", "名称");
        put("status", "状态");
        put("createdAt", "创建时间");
        put("updatedAt", "更新时间");
    }};

    @Test
    @DisplayName("validate sortField 为 null 时返回 null")
    void validate_shouldReturnNullWhenSortFieldIsNull() {
        assertNull(SortFieldSpec.validate(null, ALLOWED_FIELDS));
    }

    @Test
    @DisplayName("validate 字段在白名单内时返回原 SortField")
    void validate_shouldReturnOriginalWhenFieldIsInWhitelist() {
        SortField input = new SortField("createdAt", SortField.Direction.DESC);
        SortField result = SortFieldSpec.validate(input, ALLOWED_FIELDS);
        assertEquals(input, result);
        assertEquals("createdAt", result.getField());
        assertEquals(SortField.Direction.DESC, result.getDirection());
    }

    @Test
    @DisplayName("validate 字段不在白名单时抛 BusinessException(400)")
    void validate_shouldThrowWhenFieldIsNotInWhitelist() {
        SortField input = new SortField("hacker_field", SortField.Direction.ASC);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> SortFieldSpec.validate(input, ALLOWED_FIELDS));

        assertEquals(400, ex.getCode());
        String message = ex.getMessage();
        assertEquals(true, message.contains("hacker_field"),
                "消息应包含非法字段名: " + message);
        assertEquals(true, message.contains("允许的字段"),
                "消息应包含允许字段提示: " + message);
        // 错误消息应包含所有允许字段的中文名
        for (String chineseName : ALLOWED_FIELDS.values()) {
            assertEquals(true, message.contains(chineseName),
                    "消息应包含允许字段中文名 " + chineseName + ": " + message);
        }
    }

    @Test
    @DisplayName("validate 错误消息契约：用中文名而非 PO 字段名")
    void validate_errorMessageShouldUseChineseNames() {
        SortField input = new SortField("foo", SortField.Direction.DESC);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> SortFieldSpec.validate(input, ALLOWED_FIELDS));

        // 期望消息格式："不支持的排序字段: foo，允许的字段: [编码, 名称, 状态, 创建时间, 更新时间]"
        assertEquals("不支持的排序字段: foo，允许的字段: " + ALLOWED_FIELDS.values(), ex.getMessage());
    }
}
