package com.knowledgegame.admin.api.dto.response;

/**
 * 已接入回收站的资源类型响应
 *
 * @param type        资源类型枚举字符串值
 * @param displayName 中文显示名
 */
public record SupportedTypeResponse(String type, String displayName) {
}
