package com.knowledgegame.core.domain.service;

import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.domainenum.KnowledgeCategoryStatus;
import com.knowledgegame.core.domain.model.entity.KnowledgeCategory;
import com.knowledgegame.core.domain.port.outbound.KnowledgeCategoryRepositoryPort;

import java.util.List;

/**
 * 知识点分类领域服务（跨聚合校验，纯 POJO）
 */
public class KnowledgeCategoryDomainService {

    private final KnowledgeCategoryRepositoryPort categoryRepositoryPort;

    public KnowledgeCategoryDomainService(KnowledgeCategoryRepositoryPort categoryRepositoryPort) {
        this.categoryRepositoryPort = categoryRepositoryPort;
    }

    /**
     * 校验并创建知识点分类
     * 校验规则：同名唯一性（含 INACTIVE）、父级必须 ACTIVE
     */
    public KnowledgeCategory validateAndCreate(String name, String description, Long parentId,
                                               String iconUrl, String color, String coverImageUrl,
                                               int sortOrder) {
        // 同一父级下名称唯一（包含 INACTIVE）
        if (categoryRepositoryPort.existsByNameAndParentId(name, parentId)) {
            throw new BusinessException("同一父级下已存在同名分类: " + name);
        }
        // parentId 不为 null 时，校验父级存在且 ACTIVE
        if (parentId != null) {
            KnowledgeCategory parent = categoryRepositoryPort.findById(parentId)
                    .orElseThrow(() -> new BusinessException("父级分类不存在: " + parentId));
            if (parent.getStatus() != KnowledgeCategoryStatus.ACTIVE) {
                throw new BusinessException("父级分类未启用: " + parentId);
            }
        }
        return KnowledgeCategory.create(name, description, parentId, iconUrl, color, coverImageUrl, sortOrder);
    }

    /**
     * 校验移动目标合法性
     * 规则：不能移到自己、不能移到自己的后代下、目标必须 ACTIVE
     */
    public void validateMove(Long categoryId, Long newParentId) {
        // 不能移到自己
        if (categoryId.equals(newParentId)) {
            throw new BusinessException("不能将分类移动到自身下");
        }
        if (newParentId != null) {
            // 目标分类必须存在且 ACTIVE
            KnowledgeCategory target = categoryRepositoryPort.findById(newParentId)
                    .orElseThrow(() -> new BusinessException("目标分类不存在: " + newParentId));
            if (target.getStatus() != KnowledgeCategoryStatus.ACTIVE) {
                throw new BusinessException("目标分类未启用: " + newParentId);
            }
            // 不能移到自己的后代下
            List<Long> descendantIds = categoryRepositoryPort.findDescendantIds(categoryId);
            if (descendantIds.contains(newParentId)) {
                throw new BusinessException("不能将分类移动到自己的后代分类下");
            }
        }
    }
}
