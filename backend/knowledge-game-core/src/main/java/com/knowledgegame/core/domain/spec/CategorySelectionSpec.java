package com.knowledgegame.core.domain.spec;

import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.entity.KnowledgeCategory;
import com.knowledgegame.core.domain.port.outbound.KnowledgeCategoryRepositoryPort;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 分类选择校验规则（领域规则）
 * <p>
 * 被多个 AppService 的 validateCategoryIds 共用。
 * 规则：选择的分类列表中不允许同时存在祖先和后代。
 * <p>
 * 此类为通用领域规则，不依赖任何框架。
 */
public final class CategorySelectionSpec {

    private CategorySelectionSpec() {
    }

    /**
     * 校验所选分类列表中不存在祖先-后代冲突
     *
     * @param port        分类仓储端口
     * @param categoryIds 选中的分类 ID 列表
     * @throws BusinessException 存在祖先-后代冲突时抛 400
     */
    public static void validateNoAncestorDescendantConflict(
            KnowledgeCategoryRepositoryPort port, List<Long> categoryIds) {
        if (categoryIds == null || categoryIds.size() < 2) {
            return;
        }
        List<KnowledgeCategory> allCategories = port.findAll();
        Map<Long, Long> idToParentId = new HashMap<>();
        Map<Long, String> idToName = new HashMap<>();
        for (KnowledgeCategory cat : allCategories) {
            idToParentId.put(cat.getId(), cat.getParentId());
            idToName.put(cat.getId(), cat.getName());
        }
        Set<Long> selected = new HashSet<>(categoryIds);
        for (Long cid : categoryIds) {
            Long parentId = idToParentId.get(cid);
            while (parentId != null) {
                if (selected.contains(parentId)) {
                    throw new BusinessException(
                            "不能同时选择父分类和子分类: " + idToName.get(parentId) + " 和 " + idToName.get(cid));
                }
                parentId = idToParentId.get(parentId);
            }
        }
    }
}
