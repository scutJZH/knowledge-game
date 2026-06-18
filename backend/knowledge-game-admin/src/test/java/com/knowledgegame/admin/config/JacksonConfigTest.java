package com.knowledgegame.admin.config;

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
        private JsonNullable<Long> fileId = JsonNullable.undefined();
        private JsonNullable<String> name = JsonNullable.undefined();

        public JsonNullable<Long> getFileId() {
            return fileId;
        }

        public void setFileId(JsonNullable<Long> fileId) {
            this.fileId = fileId;
        }

        public JsonNullable<String> getName() {
            return name;
        }

        public void setName(JsonNullable<String> name) {
            this.name = name;
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

        assertFalse(dto.getFileId().isPresent(), "缺失字段应为 undefined");
        assertFalse(dto.getName().isPresent(), "缺失字段应为 undefined");
    }

    @Test
    void shouldDeserializeNullFieldAsNullableOfNull() throws Exception {
        ObjectMapper mapper = buildConfiguredMapper();

        TestDto dto = mapper.readValue("{\"fileId\":null,\"name\":null}", TestDto.class);

        assertTrue(dto.getFileId().isPresent(), "null 字段应为 present");
        assertNull(dto.getFileId().get(), "null 字段的 get() 应为 null");
        assertTrue(dto.getName().isPresent(), "null 字段应为 present");
        assertNull(dto.getName().get(), "null 字段的 get() 应为 null");
    }

    @Test
    void shouldDeserializeValueAsNullableOfValue() throws Exception {
        ObjectMapper mapper = buildConfiguredMapper();

        TestDto dto = mapper.readValue("{\"fileId\":100,\"name\":\"hello\"}", TestDto.class);

        assertTrue(dto.getFileId().isPresent());
        assertEquals(100L, dto.getFileId().get());
        assertTrue(dto.getName().isPresent());
        assertEquals("hello", dto.getName().get());
    }

    @Test
    void shouldDeserializeMixedJsonCorrectly() throws Exception {
        // 缺失 + null + 有值混合
        ObjectMapper mapper = buildConfiguredMapper();

        TestDto dto = mapper.readValue("{\"name\":null}", TestDto.class);

        assertFalse(dto.getFileId().isPresent(), "缺失字段 → undefined（不更新）");
        assertTrue(dto.getName().isPresent(), "null 字段 → present");
        assertNull(dto.getName().get(), "null 字段 → 清空");
    }

    @Test
    void shouldSerializeJsonNullableAsRawValue() throws Exception {
        ObjectMapper mapper = buildConfiguredMapper();

        TestDto dto = new TestDto();
        dto.setFileId(JsonNullable.of(200L));
        dto.setName(JsonNullable.of("test"));

        String json = mapper.writeValueAsString(dto);

        assertTrue(json.contains("\"fileId\":200"), "有值应序列化为数值");
        assertTrue(json.contains("\"name\":\"test\""), "有值应序列化为字符串");
    }

    @Test
    void shouldSerializeJsonNullableOfNullAsNull() throws Exception {
        ObjectMapper mapper = buildConfiguredMapper();

        TestDto dto = new TestDto();
        dto.setFileId(JsonNullable.of(null));

        String json = mapper.writeValueAsString(dto);

        assertTrue(json.contains("\"fileId\":null"), "of(null) 应序列化为 null");
    }
}
