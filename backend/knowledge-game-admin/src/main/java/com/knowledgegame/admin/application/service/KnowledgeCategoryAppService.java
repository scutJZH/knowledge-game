package com.knowledgegame.admin.application.service;

import com.knowledgegame.admin.api.assembler.KnowledgeCategoryAssembler;
import com.knowledgegame.admin.api.dto.request.BatchSortItem;
import com.knowledgegame.admin.api.dto.request.UpdateKnowledgeCategoryRequest;
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
import com.knowledgegame.core.domain.model.vo.SortField;
import com.knowledgegame.core.domain.port.outbound.KnowledgeCategoryRepositoryPort;
import com.knowledgegame.core.domain.service.KnowledgeCategoryDomainService;
import com.knowledgegame.core.domain.service.recyclebin.RecycleBinItemStrategy;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 知识点分类管理端应用服务（流程编排 + 事务，返回 DTO）
 */
@Service
public class KnowledgeCategoryAppService {

    private final KnowledgeCategoryRepositoryPort categoryRepositoryPort;
    private final KnowledgeCategoryDomainService categoryDomainService;
    private final FileServiceClient fileServiceClient;
    private final RecycleBinItemStrategy<KnowledgeCategory> recycleBinStrategy;


    public KnowledgeCategoryAppService(KnowledgeCategoryRepositoryPort categoryRepositoryPort,
                                       KnowledgeCategoryDomainService categoryDomainService,
                                       FileServiceClient fileServiceClient,
                                       RecycleBinItemStrategy<KnowledgeCategory> recycleBinStrategy) {
        this.categoryRepositoryPort = categoryRepositoryPort;
        this.categoryDomainService = categoryDomainService;
        this.fileServiceClient = fileServiceClient;
        this.recycleBinStrategy = recycleBinStrategy;
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
                                                       Long parentId, String sort, String order,
                                                       int pageNumber, int pageSize) {
        KnowledgeCategoryStatus statusEnum = EnumUtils.valueOfNullable(KnowledgeCategoryStatus.class, status);
        SortField sortField = SortField.parse(sort, order);
        PageResult<KnowledgeCategory> domainPage = categoryRepositoryPort.findByConditions(
                keyword, statusEnum, parentId, sortField, pageNumber, pageSize);
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
     * <p>
     * 接收整个 Request DTO（不逐字段拆包），以便解析 JsonNullable 三态：
     * - undefined：不更新
     * - of(null)：清空
     * - of(value)：更新为新值
     */
    @Transactional
    public KnowledgeCategoryResponse update(Long id, UpdateKnowledgeCategoryRequest req) {
        KnowledgeCategory category = categoryRepositoryPort.findById(id)
                .orElseThrow(() -> new BusinessException("知识点分类不存在: " + id));
        if (req.getName() != null && !req.getName().equals(category.getName())) {
            if (categoryRepositoryPort.existsByNameAndParentId(req.getName(), category.getParentId())) {
                throw new BusinessException("同一父级下已存在同名分类: " + req.getName());
            }
        }

        // 必填字段：null=不更新
        category.update(req.getName(), req.getSortOrder());

        // 可清空 String：description / color
        applyField(req.getDescription(), category::clearDescription, category::updateDescription);
        applyField(req.getColor(), category::clearColor, category::updateColor);

        // 可清空 FileRef：icon / coverImage
        applyFileRefField(req.getIconFileId(), "CATEGORY_ICON",
                category::clearIcon, category::updateIcon);
        applyFileRefField(req.getCoverImageFileId(), "CATEGORY_COVER",
                category::clearCoverImage, category::updateCoverImage);

        // 处理 status 变更
        if (req.getStatus() != null && req.getStatus() != category.getStatus()) {
            if (req.getStatus() == KnowledgeCategoryStatus.INACTIVE) {
                categoryDomainService.validateDelete(id);
            } else {
                categoryDomainService.validateActivate(category);
            }
            category.updateStatus(req.getStatus());
        }

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
        category.update(null, newSortOrder);
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
            category.update(null, item.getSortOrder());
            categoryRepositoryPort.save(category);
        }
    }

    /**
     * 删除知识点分类（递归移入回收站）
     */
    @Transactional
    public void delete(Long id) {
        if (!categoryRepositoryPort.findById(id).isPresent()) {
            throw new BusinessException("知识点分类不存在: " + id);
        }
        recycleBinStrategy.validateDeletable(id);
        recycleBinStrategy.moveToRecycleBin(id, SecurityUtils.getCurrentUsername());
    }

    /**
     * 三态分派工具：处理 JsonNullable<String> 字段
     * <p>
     * - undefined：跳过（不更新）
     * - of(null)：调用 clear
     * - of(value)：调用 update
     */
    private static <T> void applyField(JsonNullable<T> field, Runnable clear, Consumer<T> update) {
        if (field == null || !field.isPresent()) {
            return;
        }
        T value = field.get();
        if (value == null) {
            clear.run();
        } else {
            update.accept(value);
        }
    }

    /**
     * 三态分派工具：处理 JsonNullable<Long>（FileRef fileId）字段
     * <p>
     * - undefined：跳过（不更新）
     * - of(null)：调用 clear（清空图片）
     * - of(value)：调 verifyFileRef 校验后调用 update
     */
    private void applyFileRefField(JsonNullable<Long> fileIdField, String bizType,
                                   Runnable clear, Consumer<FileRef> update) {
        if (fileIdField == null || !fileIdField.isPresent()) {
            return;
        }
        Long fileId = fileIdField.get();
        if (fileId == null) {
            clear.run();
        } else {
            FileRef verified = verifyFileRef(fileId, bizType);
            update.accept(verified);
        }
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
        Object metaUserId = metadata.get("userId");
        Long metaUserIdLong = metaUserId instanceof Number ? ((Number) metaUserId).longValue() : null;
        if (!Objects.equals(currentUserId, metaUserIdLong)) {
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
