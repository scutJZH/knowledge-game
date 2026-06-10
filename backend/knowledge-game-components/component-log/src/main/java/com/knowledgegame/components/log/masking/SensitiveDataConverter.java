package com.knowledgegame.components.log.masking;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.knowledgegame.components.log.properties.LogProperties;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Logback 自定义 MessageConverter，在日志输出层自动对敏感字段脱敏
 * 脱敏规则通过 application.yml 配置，零侵入业务代码
 * 使用静态持有者模式，因为 Logback 通过 conversionRule 自建实例而非 Spring Bean
 * 不做 JSON 转义，用于 FILE appender 的人类可读格式（%maskedMsg）
 * 如需 JSON 安全输出，使用 SensitiveDataJsonConverter（%maskedJsonMsg）
 */
public class SensitiveDataConverter extends MessageConverter {

    /**
     * 匹配 key=value 格式（如 password=123456）
     */
    private static final Pattern KV_PATTERN = Pattern.compile(
            "(\\w+)\\s*=\\s*['\"]?([^\\s,;\\]\"'}]+)"
    );

    /**
     * 匹配 JSON "key":"value" 格式（如 "password":"secret123"）
     */
    private static final Pattern JSON_PATTERN = Pattern.compile(
            "\"(\\w+)\"\\s*:\\s*\"([^\"]*?)\""
    );

    /**
     * 部分遮掩的最小长度阈值，低于此值全遮掩
     */
    private static final int PARTIAL_MASK_MIN_LENGTH = 9;

    /**
     * 静态持有者：由 LogAutoConfiguration 注入配置
     */
    private static volatile LogProperties logProperties;

    /**
     * 设置配置属性（由 LogAutoConfiguration 调用）
     */
    public static void setLogProperties(LogProperties properties) {
        logProperties = properties;
    }

    @Override
    public String convert(ILoggingEvent event) {
        String message = super.convert(event);
        if (message == null || logProperties == null) {
            return message;
        }
        return maskMessage(message);
    }

    /**
     * 对消息中的敏感字段进行脱敏
     */
    private String maskMessage(String message) {
        List<String> maskingFields = logProperties.getMaskingFields();
        if (maskingFields == null || maskingFields.isEmpty()) {
            return message;
        }

        List<String> fullMaskKeywords = logProperties.getFullMaskKeywords();

        // 先处理 JSON 格式 "key":"value"
        String result = maskByPattern(message, JSON_PATTERN, maskingFields, fullMaskKeywords);
        // 再处理 key=value 格式
        result = maskByPattern(result, KV_PATTERN, maskingFields, fullMaskKeywords);

        return result;
    }

    /**
     * 使用指定正则模式进行脱敏
     */
    private String maskByPattern(String message, Pattern pattern,
                                  List<String> maskingFields, List<String> fullMaskKeywords) {
        Matcher matcher = pattern.matcher(message);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String fieldName = matcher.group(1);
            String fieldValue = matcher.group(2);

            if (isSensitiveField(fieldName, maskingFields)) {
                String maskedValue = maskValue(fieldName, fieldValue, fullMaskKeywords);
                matcher.appendReplacement(sb, Matcher.quoteReplacement(
                        matcher.group(0).replace(fieldValue, maskedValue)));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 判断字段名是否在脱敏列表中（不区分大小写）
     */
    private boolean isSensitiveField(String fieldName, List<String> maskingFields) {
        String lowerFieldName = fieldName.toLowerCase();
        return maskingFields.stream()
                .anyMatch(f -> f.equalsIgnoreCase(lowerFieldName));
    }

    /**
     * 根据字段类型选择脱敏策略
     */
    private String maskValue(String fieldName, String value, List<String> fullMaskKeywords) {
        // 全遮掩：password、secret 类字段
        if (fullMaskKeywords != null && fullMaskKeywords.stream()
                .anyMatch(k -> fieldName.toLowerCase().contains(k.toLowerCase()))) {
            return "***";
        }
        // 长度不足则全遮掩
        if (value.length() < PARTIAL_MASK_MIN_LENGTH) {
            return "***";
        }
        // 部分遮掩：保留前 3 后 2
        return value.substring(0, 3) + "***" + value.substring(value.length() - 2);
    }
}
