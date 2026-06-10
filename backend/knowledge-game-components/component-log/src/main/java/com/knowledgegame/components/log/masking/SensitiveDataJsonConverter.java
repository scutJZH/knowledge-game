package com.knowledgegame.components.log.masking;

import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * JSON 安全的脱敏 Converter，在脱敏基础上额外做 JSON 转义
 * 用于控制台 JSON 格式输出（%maskedJsonMsg）
 */
public class SensitiveDataJsonConverter extends SensitiveDataConverter {

    @Override
    public String convert(ILoggingEvent event) {
        String masked = super.convert(event);
        if (masked == null) {
            return null;
        }
        return escapeJson(masked);
    }

    /**
     * JSON 转义：防止日志消息中的特殊字符破坏 JSON 结构
     */
    private String escapeJson(String message) {
        return message
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
