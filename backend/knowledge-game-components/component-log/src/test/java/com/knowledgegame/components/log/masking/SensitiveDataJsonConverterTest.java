package com.knowledgegame.components.log.masking;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.knowledgegame.components.log.properties.LogProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SensitiveDataJsonConverter 单元测试（验证 JSON 转义行为）
 */
class SensitiveDataJsonConverterTest {

    private SensitiveDataJsonConverter converter;

    @BeforeEach
    void setUp() {
        converter = new SensitiveDataJsonConverter();

        LogProperties logProperties = new LogProperties();
        List<String> maskingFields = new ArrayList<>();
        maskingFields.add("password");
        maskingFields.add("token");
        logProperties.setMaskingFields(maskingFields);
        SensitiveDataConverter.setLogProperties(logProperties);
    }

    private ILoggingEvent createEvent(String message) {
        LoggingEvent event = new LoggingEvent();
        event.setMessage(message);
        event.setLevel(Level.INFO);
        event.setLoggerName("testLogger");
        return event;
    }

    @Nested
    @DisplayName("JSON 转义")
    class JsonEscapeTests {

        @Test
        @DisplayName("引号被转义为 \\\"")
        void shouldEscapeQuotes() {
            String result = converter.convert(createEvent("msg with \"quotes\""));
            assertThat(result).isEqualTo("msg with \\\"quotes\\\"");
        }

        @Test
        @DisplayName("换行被转义为 \\n")
        void shouldEscapeNewlines() {
            String result = converter.convert(createEvent("line1\nline2"));
            assertThat(result).isEqualTo("line1\\nline2");
        }

        @Test
        @DisplayName("反斜杠被转义为 \\\\")
        void shouldEscapeBackslashes() {
            String result = converter.convert(createEvent("path\\to\\file"));
            assertThat(result).isEqualTo("path\\\\to\\\\file");
        }

        @Test
        @DisplayName("制表符被转义为 \\t")
        void shouldEscapeTabs() {
            String result = converter.convert(createEvent("col1\tcol2"));
            assertThat(result).isEqualTo("col1\\tcol2");
        }
    }

    @Nested
    @DisplayName("脱敏 + 转义组合")
    class CombinedTests {

        @Test
        @DisplayName("JSON 格式的 password 同时被脱敏和转义")
        void shouldMaskAndEscape() {
            String result = converter.convert(createEvent("\"password\":\"secret123\""));
            // 先脱敏 "password":"***" 再转义引号
            assertThat(result).isEqualTo("\\\"password\\\":\\\"***\\\"");
        }

        @Test
        @DisplayName("key=value 格式脱敏后引号仍然转义")
        void shouldMaskKvAndEscapeQuotesInMessage() {
            String result = converter.convert(createEvent("password=secret123 msg=\"hello\""));
            assertThat(result).isEqualTo("password=*** msg=\\\"hello\\\"");
        }
    }
}
