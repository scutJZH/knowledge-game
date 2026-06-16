package com.knowledgegame.core.domain.service;

import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.domainenum.KnowledgeCategoryStatus;
import com.knowledgegame.core.domain.model.entity.KnowledgeCategory;
import com.knowledgegame.core.domain.port.outbound.KnowledgeCategoryRepositoryPort;
import com.knowledgegame.core.domain.port.outbound.QuestionRepository;

import java.util.List;

/**
 * 知识点分类领域服务（跨聚合校验，纯 POJO）
 */
public class KnowledgeCategoryDomainService {

    private final KnowledgeCategoryRepositoryPort categoryRepositoryPort;
    private final QuestionRepository questionRepository;

    public KnowledgeCategoryDomainService(KnowledgeCategoryRepositoryPort categoryRepositoryPort,
                                           QuestionRepository questionRepository) {
        this.categoryRepositoryPort = categoryRepositoryPort;
        this.questionRepository = questionRepository;
    }

    /**
     * 校验并创建知识点分类
     * 校验规则：同名唯一性（含 INACTIVE）、父级必须 ACTIVE
     * sortOrder 为 null 时自动计算（同级最大值 + 1，无同级时为 0）
     */
    public KnowledgeCategory validateAndCreate(String name, String description, Long parentId,
                                               String iconUrl, String color, String coverImageUrl,
                                               Integer sortOrder) {
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
        // 自动计算 sortOrder
        int resolvedSortOrder = resolveSortOrder(parentId, sortOrder);
        return KnowledgeCategory.create(name, description, parentId, iconUrl, color, coverImageUrl, resolvedSortOrder);
    }

    /**
     * 解析排序号：如果 sortOrder 不为 null 则直接使用，否则查询同级最大值 + 1
     */
    private int resolveSortOrder(Long parentId, Integer sortOrder) {
        if (sortOrder != null) {
            return sortOrder;
        }
        // 根据是否有 parentId 查询不同的最大值
        Integer maxSortOrder = parentId != null
                ? categoryRepositoryPort.findMaxSortOrderByParentId(parentId)
                : categoryRepositoryPort.findMaxSortOrderForRoot();
        return maxSortOrder != null ? maxSortOrder + 1 : 0;
    }

    /**
     * 校验移动目标合法性
     * 规则：不能移到自己、不能移到自己的后代下、目标必须 ACTIVE、目标父级下不能存在同名分类
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
        // 目标父级下不能存在同名分类（跨级移动时）
        KnowledgeCategory current = categoryRepositoryPort.findById(categoryId)
                .orElseThrow(() -> new BusinessException("知识点分类不存在: " + categoryId));
        if (!java.util.Objects.equals(current.getParentId(), newParentId)
                && categoryRepositoryPort.existsByNameAndParentId(current.getName(), newParentId)) {
            throw new BusinessException("目标父级下已存在同名分类: " + current.getName());
        }
    }

    /**
     * 校验停用合法性：子分类校验 → 题目关联校验，均通过才允许停用
     */
    public void validateDelete(Long categoryId) {
        // 子分类校验：仅统计 ACTIVE
        long activeChildCount = categoryRepositoryPort.countActiveByParentId(categoryId);
        if (activeChildCount > 0) {
            throw new BusinessException("知识点分类下存在 " + activeChildCount + " 个 ACTIVE 子分类，无法删除");
        }
        // 题目关联校验：仅统计 ACTIVE 题目
        long activeQuestionCount = questionRepository.countActiveByCategoryId(categoryId);
        if (activeQuestionCount > 0) {
            throw new BusinessException("知识点分类关联 " + activeQuestionCount + " 道 ACTIVE 题目，无法删除");
        }
    }
}
