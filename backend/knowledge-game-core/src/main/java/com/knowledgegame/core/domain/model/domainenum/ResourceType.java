package com.knowledgegame.core.domain.model.domainenum;

import java.util.List;

/**
 * 回收站资源类型枚举
 * <p>
 * 每个枚举值对应一种可删除的管理端资源，提供 bizType 映射（用于永久删除时清理关联文件）
 * 和中文显示名（用于前端目录树和管理端展示）。
 */
public enum ResourceType {

    IP_SERIES,
    CARD_TEMPLATE,
    QUESTION,
    KNOWLEDGE_CATEGORY,
    KNOWLEDGE_ITEM;

    /**
     * 该资源类型对应的文件 bizType 列表（用于永久删除时清理关联文件）
     * <p>
     * 与 admin 模块的 FilePathMapping.MAPPING 的 key 对齐。
     */
    public List<String> toBizTypes() {
        return switch (this) {
            case IP_SERIES -> List.of("IP_SERIES");
            case CARD_TEMPLATE -> List.of("CARD_TEMPLATE");
            case QUESTION -> List.of();
            case KNOWLEDGE_CATEGORY -> List.of("CATEGORY_ICON", "CATEGORY_COVER");
            case KNOWLEDGE_ITEM -> List.of("KNOWLEDGE_ITEM_COVER");
        };
    }

    /**
     * 中文显示名（用于前端目录树和管理端展示）
     */
    public String displayName() {
        return switch (this) {
            case IP_SERIES -> "IP 系列";
            case CARD_TEMPLATE -> "卡牌模板";
            case QUESTION -> "题库";
            case KNOWLEDGE_CATEGORY -> "知识分类";
            case KNOWLEDGE_ITEM -> "知识条目";
        };
    }
}
