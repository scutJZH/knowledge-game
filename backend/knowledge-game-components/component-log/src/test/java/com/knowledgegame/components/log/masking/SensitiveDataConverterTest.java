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
 * SensitiveDataConverter 单元测试
 */
class SensitiveDataConverterTest {

    private SensitiveDataConverter converter;
    private LogProperties logProperties;

    @BeforeEach
    void setUp() {
        converter = new SensitiveDataConverter();
        logProperties = new LogProperties();

        List<String> maskingFields = new ArrayList<>();
        maskingFields.add("password");
        maskingFields.add("secret");
        maskingFields.add("phone");
        maskingFields.add("email");
        maskingFields.add("name");
        maskingFields.add("clientsecret");
        maskingFields.add("userpassword");
        maskingFields.add("token");
        logProperties.setMaskingFields(maskingFields);

        // 使用静态方法注入配置
        SensitiveDataConverter.setLogProperties(logProperties);
    }

    /**
     * 构建模拟的 ILoggingEvent
     */
    private ILoggingEvent createEvent(String message) {
        LoggingEvent event = new LoggingEvent();
        event.setMessage(message);
        event.setLevel(Level.INFO);
        event.setLoggerName("testLogger");
        return event;
    }

    @Nested
    @DisplayName("全遮掩字段（password/secret）")
    class FullMaskTests {

        @Test
        @DisplayName("password 字段值全部替换为 ***（key=value 格式）")
        void shouldFullMaskPassword() {
            String result = converter.convert(createEvent("password=MySecret123"));
            assertThat(result).isEqualTo("password=***");
        }

        @Test
        @DisplayName("secret 字段值全部替换为 ***")
        void shouldFullMaskSecret() {
            String result = converter.convert(createEvent("secret=abcd1234efgh"));
            assertThat(result).isEqualTo("secret=***");
        }

        @Test
        @DisplayName("包含 secret 关键字的字段名也全遮掩")
        void shouldFullMaskFieldContainingSecret() {
            String result = converter.convert(createEvent("clientSecret=myClientSecretValue"));
            assertThat(result).isEqualTo("clientSecret=***");
        }

        @Test
        @DisplayName("包含 password 关键字的字段名也全遮掩")
        void shouldFullMaskFieldContainingPassword() {
            String result = converter.convert(createEvent("userPassword=someLongValue123"));
            assertThat(result).isEqualTo("userPassword=***");
        }
    }

    @Nested
    @DisplayName("部分遮掩字段（phone/email/name 等）")
    class PartialMaskTests {

        @Test
        @DisplayName("phone 字段保留前 3 后 2（key=value 格式）")
        void shouldPartialMaskPhone() {
            String result = converter.convert(createEvent("phone=13812345678"));
            assertThat(result).isEqualTo("phone=138***78");
        }

        @Test
        @DisplayName("email 字段保留前 3 后 2")
        void shouldPartialMaskEmail() {
            String result = converter.convert(createEvent("email=test@example.com"));
            assertThat(result).isEqualTo("email=tes***om");
        }

        @Test
        @DisplayName("值长度 < 9 时全遮掩")
        void shouldFullMaskWhenValueShort() {
            String result = converter.convert(createEvent("name=short"));
            assertThat(result).isEqualTo("name=***");
        }

        @Test
        @DisplayName("值长度刚好为 8 时全遮掩")
        void shouldFullMaskWhenValueLength8() {
            String result = converter.convert(createEvent("name=12345678"));
            assertThat(result).isEqualTo("name=***");
        }

        @Test
        @DisplayName("值长度为 9 时保留前 3 后 2")
        void shouldPartialMaskWhenValueLength9() {
            String result = converter.convert(createEvent("name=123456789"));
            assertThat(result).isEqualTo("name=123***89");
        }
    }

    @Nested
    @DisplayName("JSON 格式脱敏")
    class JsonFormatTests {

        @Test
        @DisplayName("JSON key:value 格式的 password 字段被脱敏")
        void shouldMaskJsonPassword() {
            String result = converter.convert(createEvent("\"password\":\"MySecret123\""));
            assertThat(result).isEqualTo("\"password\":\"***\"");
        }

        @Test
        @DisplayName("JSON key:value 格式的 token 字段被脱敏")
        void shouldMaskJsonToken() {
            String result = converter.convert(createEvent("\"token\":\"abc123def456\""));
            // token 不在 fullMaskKeywords 中，长度 12 >= 9，部分遮掩
            assertThat(result).isEqualTo("\"token\":\"abc***56\"");
        }

        @Test
        @DisplayName("JSON 格式中非敏感字段不脱敏")
        void shouldNotMaskJsonNonSensitiveField() {
            String result = converter.convert(createEvent("\"username\":\"john_doe\""));
            assertThat(result).isEqualTo("\"username\":\"john_doe\"");
        }

        @Test
        @DisplayName("混合 key=value 和 JSON 格式均被脱敏")
        void shouldMaskBothFormats() {
            String result = converter.convert(createEvent(
                    "password=secret123 and \"token\":\"abc123def456\""));
            assertThat(result).isEqualTo("password=*** and \"token\":\"abc***56\"");
        }
    }

    @Nested
    @DisplayName("不在脱敏列表的字段不处理")
    class NoMaskTests {

        @Test
        @DisplayName("不在 maskingFields 中的字段不脱敏")
        void shouldNotMaskNonSensitiveField() {
            String result = converter.convert(createEvent("username=john_doe"));
            assertThat(result).isEqualTo("username=john_doe");
        }

        @Test
        @DisplayName("普通文本不做任何修改")
        void shouldNotModifyPlainText() {
            String message = "用户登录成功";
            String result = converter.convert(createEvent(message));
            assertThat(result).isEqualTo(message);
        }
    }

    @Nested
    @DisplayName("边界条件")
    class EdgeCaseTests {

        @Test
        @DisplayName("logProperties 为 null 时原样返回")
        void shouldReturnOriginalWhenNoProperties() {
            SensitiveDataConverter.setLogProperties(null);
            String result = converter.convert(createEvent("password=secret123"));
            assertThat(result).isEqualTo("password=secret123");
            // 恢复配置
            SensitiveDataConverter.setLogProperties(logProperties);
        }

        @Test
        @DisplayName("maskingFields 为空列表时不脱敏")
        void shouldNotMaskWhenMaskingFieldsEmpty() {
            logProperties.setMaskingFields(new ArrayList<>());
            SensitiveDataConverter.setLogProperties(logProperties);
            String result = converter.convert(createEvent("password=secret123"));
            assertThat(result).isEqualTo("password=secret123");
        }

        @Test
        @DisplayName("字段名匹配不区分大小写")
        void shouldMatchCaseInsensitive() {
            String result = converter.convert(createEvent("Password=MySecret123"));
            assertThat(result).isEqualTo("Password=***");
        }

        @Test
        @DisplayName("同一条消息中多个敏感字段均被脱敏")
        void shouldMaskMultipleFieldsInSameMessage() {
            String result = converter.convert(createEvent(
                    "password=secret123456 phone=13812345678 username=john"));
            assertThat(result).isEqualTo("password=*** phone=138***78 username=john");
        }
    }

    @Nested
    @DisplayName("引号和特殊字符不转义（FILE 格式）")
    class NoEscapeTests {

        @Test
        @DisplayName("消息中的引号保持原样")
        void shouldNotEscapeQuotes() {
            String result = converter.convert(createEvent("msg with \"quotes\" here"));
            assertThat(result).isEqualTo("msg with \"quotes\" here");
        }

        @Test
        @DisplayName("消息中的换行保持原样")
        void shouldNotEscapeNewlines() {
            String result = converter.convert(createEvent("line1\nline2"));
            assertThat(result).isEqualTo("line1\nline2");
        }
    }
}
