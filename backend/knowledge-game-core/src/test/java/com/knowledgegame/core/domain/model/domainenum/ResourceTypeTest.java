package com.knowledgegame.core.domain.model.domainenum;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * ResourceType 枚举单元测试
 * <p>
 * 覆盖 5 个枚举值的 toBizTypes() 映射和 displayName() 中文显示。
 */
class ResourceTypeTest {

    @Test
    @DisplayName("IP_SERIES toBizTypes 应返回 [IP_SERIES]")
    void ipSeries_shouldReturnCorrectBizTypes() {
        assertEquals(List.of("IP_SERIES"), ResourceType.IP_SERIES.toBizTypes());
    }

    @Test
    @DisplayName("CARD_TEMPLATE toBizTypes 应返回 [CARD_TEMPLATE]")
    void cardTemplate_shouldReturnCorrectBizTypes() {
        assertEquals(List.of("CARD_TEMPLATE"), ResourceType.CARD_TEMPLATE.toBizTypes());
    }

    @Test
    @DisplayName("QUESTION toBizTypes 应返回空列表（无图片）")
    void question_shouldReturnEmptyBizTypes() {
        assertEquals(List.of(), ResourceType.QUESTION.toBizTypes());
    }

    @Test
    @DisplayName("KNOWLEDGE_CATEGORY toBizTypes 应返回 [CATEGORY_ICON, CATEGORY_COVER]")
    void knowledgeCategory_shouldReturnCorrectBizTypes() {
        assertEquals(List.of("CATEGORY_ICON", "CATEGORY_COVER"), ResourceType.KNOWLEDGE_CATEGORY.toBizTypes());
    }

    @Test
    @DisplayName("KNOWLEDGE_ITEM toBizTypes 应返回 [KNOWLEDGE_ITEM_COVER]")
    void knowledgeItem_shouldReturnCorrectBizTypes() {
        assertEquals(List.of("KNOWLEDGE_ITEM_COVER"), ResourceType.KNOWLEDGE_ITEM.toBizTypes());
    }

    @Test
    @DisplayName("所有 5 个枚举值 displayName 应返回中文且非空")
    void allValues_shouldHaveNonEmptyDisplayName() {
        for (ResourceType type : ResourceType.values()) {
            assertNotNull(type.displayName());
            assertFalse(type.displayName().isBlank(),
                    type.name() + " displayName 不应为空");
        }
    }

    @Test
    @DisplayName("valueOf 应能解析所有合法枚举名")
    void valueOf_shouldResolveAllEnumNames() {
        assertEquals(ResourceType.IP_SERIES, ResourceType.valueOf("IP_SERIES"));
        assertEquals(ResourceType.CARD_TEMPLATE, ResourceType.valueOf("CARD_TEMPLATE"));
        assertEquals(ResourceType.QUESTION, ResourceType.valueOf("QUESTION"));
        assertEquals(ResourceType.KNOWLEDGE_CATEGORY, ResourceType.valueOf("KNOWLEDGE_CATEGORY"));
        assertEquals(ResourceType.KNOWLEDGE_ITEM, ResourceType.valueOf("KNOWLEDGE_ITEM"));
    }
}
