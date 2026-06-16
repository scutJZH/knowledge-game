package com.knowledgegame.admin.application.service;

import com.knowledgegame.admin.api.assembler.KnowledgeCategoryAssembler;
import com.knowledgegame.admin.api.dto.request.BatchSortItem;
import com.knowledgegame.admin.api.dto.response.KnowledgeCategoryResponse;
import com.knowledgegame.admin.api.dto.response.KnowledgeCategoryTreeResponse;
import com.knowledgegame.auth.security.SecurityUtils;
import com.knowledgegame.components.feign.client.FileServiceClient;
import com.knowledgegame.components.feign.dto.FileInfoResponse;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.common.result.Result;
import com.knowledgegame.core.common.util.EnumUtils;
import com.knowledgegame.core.domain.model.domainenum.KnowledgeCategoryStatus;
import com.knowledgegame.core.domain.model.entity.KnowledgeCategory;
import com.knowledgegame.core.domain.model.vo.FileRef;
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
    private final FileServiceClient fileServiceClient;

    public KnowledgeCategoryAppService(KnowledgeCategoryRepositoryPort categoryRepositoryPort,
                                       KnowledgeCategoryDomainService categoryDomainService,
                                       FileServiceClient fileServiceClient) {
        this.categoryRepositoryPort = categoryRepositoryPort;
        this.categoryDomainService = categoryDomainService;
        this.fileServiceClient = fileServiceClient;
    }

    /**
     * 创建知识点分类
     */
    @Transactional
    public KnowledgeCategoryResponse create(String name, String description, Long parentId,
                                            Long iconFileId, String color, Long coverImageFileId,
                                            Integer sortOrder) {
        FileRef icon = verifyFileRef(iconFileId, "CATEGORY_ICON");
        FileRef coverImage = verifyFileRef(coverImageFileId, "CATEGORY_COVER");
        KnowledgeCategory category = categoryDomainService.validateAndCreate(
                name, description, parentId, icon, color, coverImage, sortOrder);
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
                                            Long iconFileId, String color, Long coverImageFileId,
                                            Integer sortOrder) {
        KnowledgeCategory category = categoryRepositoryPort.findById(id)
                .orElseThrow(() -> new BusinessException("知识点分类不存在: " + id));
        if (name != null && !name.equals(category.getName())) {
            if (categoryRepositoryPort.existsByNameAndParentId(name, category.getParentId())) {
                throw new BusinessException("同一父级下已存在同名分类: " + name);
            }
        }
        FileRef icon = verifyFileRef(iconFileId, "CATEGORY_ICON");
        FileRef coverImage = verifyFileRef(coverImageFileId, "CATEGORY_COVER");
        category.update(name, description, icon, color, coverImage, sortOrder);
        KnowledgeCategory saved = categoryRepositoryPort.save(category);
        return KnowledgeCategoryAssembler.INSTANCE.toResponse(saved);
    }

    /**
     * 移动知识点分类
     */
    @Transactional
    public KnowledgeCategoryResponse move(Long id, Long newParentId) {
        KnowledgeCategory category = categoryRepositoryPort.findById(id)
                .orElseThrow(() -> new BusinessException("知识点分类不存在: " + id));
        categoryDomainService.validateMove(id, newParentId);
        category.moveTo(newParentId);
        Integer maxSortOrder = newParentId != null
                ? categoryRepositoryPort.findMaxSortOrderByParentId(newParentId)
                : categoryRepositoryPort.findMaxSortOrderForRoot();
        int newSortOrder = maxSortOrder != null ? maxSortOrder + 1 : 0;
        category.update(null, null, null, null, null, newSortOrder);
        KnowledgeCategory saved = categoryRepositoryPort.save(category);
        return KnowledgeCategoryAssembler.INSTANCE.toResponse(saved);
    }

    /**
     * 批量排序
     */
    @Transactional
    public void batchSort(List<BatchSortItem> items) {
        Map<Long, KnowledgeCategory> categoryMap = new LinkedHashMap<>();
        for (BatchSortItem item : items) {
            KnowledgeCategory category = categoryRepositoryPort.findById(item.getId())
                    .orElseThrow(() -> new BusinessException("知识点分类不存在: " + item.getId()));
            categoryMap.put(item.getId(), category);
        }
        Long expectedParentId = categoryMap.get(items.getFirst().getId()).getParentId();
        for (BatchSortItem item : items) {
            KnowledgeCategory category = categoryMap.get(item.getId());
            if (!Objects.equals(category.getParentId(), expectedParentId)) {
                throw new BusinessException("所有分类必须属于同一父级");
            }
        }
        for (BatchSortItem item : items) {
            KnowledgeCategory category = categoryMap.get(item.getId());
            category.update(null, null, null, null, null, item.getSortOrder());
            categoryRepositoryPort.save(category);
        }
    }

    /**
     * 软删除
     */
    @Transactional
    public void delete(Long id) {
        KnowledgeCategory category = categoryRepositoryPort.findById(id)
                .orElseThrow(() -> new BusinessException("知识点分类不存在: " + id));
        categoryDomainService.validateDelete(id);
        category.deactivate();
        categoryRepositoryPort.save(category);
    }

    private FileRef verifyFileRef(Long fileId, String expectedBizType) {
        if (fileId == null) {
            return null;
        }
        Result<FileInfoResponse> result = fileServiceClient.getFileInfo(fileId);
        FileInfoResponse info = result.getData();
        if (info == null) {
            throw new BusinessException(400, "文件不存在: " + fileId);
        }
        Map<String, Object> metadata = info.getMetadata();
        if (metadata == null || !expectedBizType.equals(metadata.get("bizType"))) {
            throw new BusinessException(400, "文件类型不匹配，期望 " + expectedBizType);
        }
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (!Objects.equals(currentUserId, metadata.get("userId"))) {
            throw new BusinessException(403, "无权使用该文件");
        }
        return FileRef.of(fileId, info.getUrl());
    }

    private List<KnowledgeCategoryTreeResponse> buildTree(List<KnowledgeCategory> all) {
        Map<Long, KnowledgeCategoryTreeResponse> nodeMap = new LinkedHashMap<>();
        List<KnowledgeCategoryTreeResponse> roots = new ArrayList<>();
        for (KnowledgeCategory category : all) {
            nodeMap.put(category.getId(), KnowledgeCategoryAssembler.INSTANCE.toTreeNode(category));
        }
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
        sortChildren(roots);
        return roots;
    }

    private void sortChildren(List<KnowledgeCategoryTreeResponse> nodes) {
        if (nodes == null) return;
        nodes.sort((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()));
        for (KnowledgeCategoryTreeResponse node : nodes) {
            sortChildren(node.getChildren());
        }
    }
}
