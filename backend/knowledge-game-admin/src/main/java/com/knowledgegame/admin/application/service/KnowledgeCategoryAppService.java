package com.knowledgegame.admin.application.service;

import com.knowledgegame.admin.api.assembler.KnowledgeCategoryAssembler;
import com.knowledgegame.admin.api.dto.request.BatchSortItem;
import com.knowledgegame.admin.api.dto.response.KnowledgeCategoryResponse;
import com.knowledgegame.admin.api.dto.response.KnowledgeCategoryTreeResponse;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.common.util.EnumUtils;
import com.knowledgegame.core.domain.model.domainenum.KnowledgeCategoryStatus;
import com.knowledgegame.core.domain.model.entity.KnowledgeCategory;
import com.knowledgegame.core.domain.model.vo.PageResult;
import com.knowledgegame.core.domain.port.outbound.KnowledgeCategoryRepositoryPort;
import com.knowledgegame.core.domain.service.KnowledgeCategoryDomainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 知识点分类管理端应用服务（流程编排 + 事务，返回 DTO）
 */
@Service
public class KnowledgeCategoryAppService {

    private final KnowledgeCategoryRepositoryPort categoryRepositoryPort;
    private final KnowledgeCategoryDomainService categoryDomainService;

    public KnowledgeCategoryAppService(KnowledgeCategoryRepositoryPort categoryRepositoryPort,
                                       KnowledgeCategoryDomainService categoryDomainService) {
        this.categoryRepositoryPort = categoryRepositoryPort;
        this.categoryDomainService = categoryDomainService;
    }

    /**
     * 创建知识点分类
     */
    @Transactional
    public KnowledgeCategoryResponse create(String name, String description, Long parentId,
                                            String iconUrl, String color, String coverImageUrl,
                                            Integer sortOrder) {
        KnowledgeCategory category = categoryDomainService.validateAndCreate(
                name, description, parentId, iconUrl, color, coverImageUrl, sortOrder);
        KnowledgeCategory saved = categoryRepositoryPort.save(category);
        return KnowledgeCategoryAssembler.INSTANCE.toResponse(saved);
    }

    /**
     * 根据 ID 查询
     */
    public KnowledgeCategoryResponse getById(Long id) {
        KnowledgeCategory category = categoryRepositoryPort.findById(id)
                .orElseThrow(() -> new BusinessException("知识点分类不存在: " + id));
        return KnowledgeCategoryAssembler.INSTANCE.toResponse(category);
    }

    /**
     * 分页查询
     */
    public PageResult<KnowledgeCategoryResponse> list(String keyword, String status,
                                                       Long parentId, int pageNumber, int pageSize) {
        KnowledgeCategoryStatus statusEnum = EnumUtils.valueOfNullable(KnowledgeCategoryStatus.class, status);
        PageResult<KnowledgeCategory> domainPage = categoryRepositoryPort.findByConditions(
                keyword, statusEnum, parentId, pageNumber, pageSize);
        return PageResult.<KnowledgeCategoryResponse>builder()
                .content(domainPage.getContent().stream()
                        .map(KnowledgeCategoryAssembler.INSTANCE::toResponse).toList())
                .totalElements(domainPage.getTotalElements())
                .pageNumber(domainPage.getPageNumber())
                .pageSize(domainPage.getPageSize())
                .totalPages(domainPage.getTotalPages())
                .build();
    }

    /**
     * 查询完整分类树
     */
    public List<KnowledgeCategoryTreeResponse> tree() {
        List<KnowledgeCategory> all = categoryRepositoryPort.findAll();
        return buildTree(all);
    }

    /**
     * 更新知识点分类
     */
    @Transactional
    public KnowledgeCategoryResponse update(Long id, String name, String description,
                                            String iconUrl, String color, String coverImageUrl,
                                            Integer sortOrder) {
        KnowledgeCategory category = categoryRepositoryPort.findById(id)
                .orElseThrow(() -> new BusinessException("知识点分类不存在: " + id));
        // 如果名称变更，校验同级唯一性
        if (name != null && !name.equals(category.getName())) {
            if (categoryRepositoryPort.existsByNameAndParentId(name, category.getParentId())) {
                throw new BusinessException("同一父级下已存在同名分类: " + name);
            }
        }
        category.update(name, description, iconUrl, color, coverImageUrl, sortOrder);
        KnowledgeCategory saved = categoryRepositoryPort.save(category);
        return KnowledgeCategoryAssembler.INSTANCE.toResponse(saved);
    }

    /**
     * 移动知识点分类（自动设置目标父级下的 sortOrder）
     */
    @Transactional
    public KnowledgeCategoryResponse move(Long id, Long newParentId) {
        KnowledgeCategory category = categoryRepositoryPort.findById(id)
                .orElseThrow(() -> new BusinessException("知识点分类不存在: " + id));
        categoryDomainService.validateMove(id, newParentId);
        category.moveTo(newParentId);
        // 自动设置 sortOrder 为目标父级下最大值 + 1
        Integer maxSortOrder = newParentId != null
                ? categoryRepositoryPort.findMaxSortOrderByParentId(newParentId)
                : categoryRepositoryPort.findMaxSortOrderForRoot();
        int newSortOrder = maxSortOrder != null ? maxSortOrder + 1 : 0;
        category.update(null, null, null, null, null, newSortOrder);
        KnowledgeCategory saved = categoryRepositoryPort.save(category);
        return KnowledgeCategoryAssembler.INSTANCE.toResponse(saved);
    }

    /**
     * 批量排序知识点分类
     * 校验所有 ID 存在且属于同一父级，逐个更新 sortOrder 并保存
     */
    @Transactional
    public void batchSort(List<BatchSortItem> items) {
        // 查询所有分类并校验 ID 存在
        Map<Long, KnowledgeCategory> categoryMap = new LinkedHashMap<>();
        for (BatchSortItem item : items) {
            KnowledgeCategory category = categoryRepositoryPort.findById(item.getId())
                    .orElseThrow(() -> new BusinessException("知识点分类不存在: " + item.getId()));
            categoryMap.put(item.getId(), category);
        }
        // 校验所有分类属于同一父级
        Long expectedParentId = categoryMap.get(items.getFirst().getId()).getParentId();
        for (BatchSortItem item : items) {
            KnowledgeCategory category = categoryMap.get(item.getId());
            if (!Objects.equals(category.getParentId(), expectedParentId)) {
                throw new BusinessException("所有分类必须属于同一父级");
            }
        }
        // 逐个更新 sortOrder 并保存
        for (BatchSortItem item : items) {
            KnowledgeCategory category = categoryMap.get(item.getId());
            category.update(null, null, null, null, null, item.getSortOrder());
            categoryRepositoryPort.save(category);
        }
    }

    /**
     * 软删除知识点分类
     */
    @Transactional
    public void delete(Long id) {
        KnowledgeCategory category = categoryRepositoryPort.findById(id)
                .orElseThrow(() -> new BusinessException("知识点分类不存在: " + id));
        categoryDomainService.validateDelete(id);
        category.deactivate();
        categoryRepositoryPort.save(category);
    }

    /**
     * 构建分类树（内存组装，分类数量可控，无需数据库递归）
     */
    private List<KnowledgeCategoryTreeResponse> buildTree(List<KnowledgeCategory> all) {
        Map<Long, KnowledgeCategoryTreeResponse> nodeMap = new LinkedHashMap<>();
        List<KnowledgeCategoryTreeResponse> roots = new ArrayList<>();

        // 第一遍：创建所有节点
        for (KnowledgeCategory category : all) {
            nodeMap.put(category.getId(), KnowledgeCategoryAssembler.INSTANCE.toTreeNode(category));
        }

        // 第二遍：建立父子关系
        for (KnowledgeCategory category : all) {
            KnowledgeCategoryTreeResponse node = nodeMap.get(category.getId());
            if (category.getParentId() == null) {
                roots.add(node);
            } else {
                KnowledgeCategoryTreeResponse parent = nodeMap.get(category.getParentId());
                if (parent != null) {
                    if (parent.getChildren() == null) {
                        parent.setChildren(new ArrayList<>());
                    }
                    parent.getChildren().add(node);
                } else {
                    roots.add(node);
                }
            }
        }

        // 按 sortOrder 排序
        sortChildren(roots);
        return roots;
    }

    /**
     * 递归排序子节点
     */
    private void sortChildren(List<KnowledgeCategoryTreeResponse> nodes) {
        if (nodes == null) {
            return;
        }
        nodes.sort((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()));
        for (KnowledgeCategoryTreeResponse node : nodes) {
            sortChildren(node.getChildren());
        }
    }
}
