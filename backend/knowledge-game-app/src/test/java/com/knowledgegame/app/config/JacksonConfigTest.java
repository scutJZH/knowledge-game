package com.knowledgegame.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;
import org.openapitools.jackson.nullable.JsonNullableModule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JacksonConfig 单元测试
 * <p>
 * 验证 JsonNullableModule 注册后，ObjectMapper 能正确反序列化 update 接口的三态 JSON 形态：
 * 1. 字段缺失（请求体不含字段）→ JsonNullable.undefined()（!isPresent()）
 * 2. 字段为 null（"field":null）→ JsonNullable.of(null)（isPresent()=true, get()=null）
 * 3. 字段有值（"field":100）→ JsonNullable.of(value)（isPresent()=true, get()=value）
 */
class JacksonConfigTest {

    /**
     * 测试用 DTO，模拟 update 接口的 Request
     */
    static class TestDto {
        private JsonNullable<Long> avatarFileId = JsonNullable.undefined();

        public JsonNullable<Long> getAvatarFileId() {
            return avatarFileId;
        }

        public void setAvatarFileId(JsonNullable<Long> avatarFileId) {
            this.avatarFileId = avatarFileId;
        }
    }

    private ObjectMapper buildConfiguredMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JsonNullableModule());
        return mapper;
    }

    @Test
    void shouldDeserializeAbsentFieldAsUndefined() throws Exception {
        ObjectMapper mapper = buildConfiguredMapper();

        TestDto dto = mapper.readValue("{}", TestDto.class);

        assertFalse(dto.getAvatarFileId().isPresent(), "缺失字段应为 undefined");
    }

    @Test
    void shouldDeserializeNullFieldAsNullableOfNull() throws Exception {
        ObjectMapper mapper = buildConfiguredMapper();

        TestDto dto = mapper.readValue("{\"avatarFileId\":null}", TestDto.class);

        assertTrue(dto.getAvatarFileId().isPresent(), "null 字段应为 present");
        assertNull(dto.getAvatarFileId().get(), "null 字段的 get() 应为 null");
    }

    @Test
    void shouldDeserializeValueAsNullableOfValue() throws Exception {
        ObjectMapper mapper = buildConfiguredMapper();

        TestDto dto = mapper.readValue("{\"avatarFileId\":100}", TestDto.class);

        assertTrue(dto.getAvatarFileId().isPresent());
        assertEquals(100L, dto.getAvatarFileId().get());
    }

    @Test
    void shouldSerializeJsonNullableAsRawValue() throws Exception {
        ObjectMapper mapper = buildConfiguredMapper();

        TestDto dto = new TestDto();
        dto.setAvatarFileId(JsonNullable.of(200L));

        String json = mapper.writeValueAsString(dto);

        assertTrue(json.contains("\"avatarFileId\":200"), "有值应序列化为数值");
    }

    @Test
    void shouldSerializeJsonNullableOfNullAsNull() throws Exception {
        ObjectMapper mapper = buildConfiguredMapper();

        TestDto dto = new TestDto();
        dto.setAvatarFileId(JsonNullable.of(null));

        String json = mapper.writeValueAsString(dto);

        assertTrue(json.contains("\"avatarFileId\":null"), "of(null) 应序列化为 null");
    }
}
