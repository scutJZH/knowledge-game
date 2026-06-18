package com.knowledgegame.admin.application.service;

import com.knowledgegame.admin.api.assembler.KnowledgeItemAssembler;
import com.knowledgegame.admin.api.dto.request.BatchSortItem;
import com.knowledgegame.admin.api.dto.request.BatchSortRequest;
import com.knowledgegame.admin.api.dto.request.CreateKnowledgeItemRequest;
import com.knowledgegame.admin.api.dto.request.UpdateKnowledgeItemRequest;
import com.knowledgegame.admin.api.dto.response.KnowledgeItemResponse;
import com.knowledgegame.auth.security.SecurityUtils;
import com.knowledgegame.components.feign.client.FileServiceClient;
import com.knowledgegame.components.feign.dto.FileInfoResponse;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.common.result.Result;
import com.knowledgegame.core.common.util.EnumUtils;
import com.knowledgegame.core.domain.model.domainenum.KnowledgeCategoryStatus;
import com.knowledgegame.core.domain.model.domainenum.KnowledgeItemStatus;
import com.knowledgegame.core.domain.model.entity.KnowledgeCategory;
import com.knowledgegame.core.domain.model.entity.KnowledgeItem;
import com.knowledgegame.core.domain.model.vo.FileRef;
import com.knowledgegame.core.domain.model.vo.PageResult;
import com.knowledgegame.core.domain.model.vo.SortField;
import com.knowledgegame.core.domain.port.outbound.KnowledgeCategoryRepositoryPort;
import com.knowledgegame.core.domain.port.outbound.KnowledgeItemRepository;
import com.knowledgegame.core.domain.service.KnowledgeItemDomainService;
import com.knowledgegame.core.infrastructure.markdown.MarkdownRenderer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 知识条目管理端应用服务（流程编排 + 事务，返回 DTO）
 */
@Service
public class KnowledgeItemAppService {

    private static final String BIZ_TYPE = "KNOWLEDGE_ITEM_COVER";

    private final KnowledgeItemRepository itemRepository;
    private final KnowledgeItemDomainService itemDomainService;
    private final KnowledgeCategoryRepositoryPort categoryRepositoryPort;
    private final FileServiceClient fileServiceClient;
    private final MarkdownRenderer markdownRenderer;

    public KnowledgeItemAppService(KnowledgeItemRepository itemRepository,
                                    KnowledgeItemDomainService itemDomainService,
                                    KnowledgeCategoryRepositoryPort categoryRepositoryPort,
                                    FileServiceClient fileServiceClient,
                                    MarkdownRenderer markdownRenderer) {
        this.itemRepository = itemRepository;
        this.itemDomainService = itemDomainService;
        this.categoryRepositoryPort = categoryRepositoryPort;
        this.fileServiceClient = fileServiceClient;
        this.markdownRenderer = markdownRenderer;
    }

    /**
     * 创建知识条目
     */
    @Transactional
    public KnowledgeItemResponse create(CreateKnowledgeItemRequest request) {
        String contentHtml = markdownRenderer.render(request.getContent());
        FileRef coverImage = verifyFileRef(request.getCoverImageFileId(), BIZ_TYPE);
        int sortOrder = request.getSortOrder() != null ? request.getSortOrder() : 0;

        KnowledgeItem item = itemDomainService.validateAndCreate(
                request.getTitle(), request.getContent(), coverImage,
                request.getTags(), sortOrder, request.getCategoryIds()
        );
        item.updateContentHtml(contentHtml);
        KnowledgeItem saved = itemRepository.save(item);
        itemRepository.saveCategoryRelations(saved.getId(), request.getCategoryIds());

        return toResponseWithCategories(saved);
    }

    /**
     * 查询详情
     */
    public KnowledgeItemResponse getById(Long id) {
        KnowledgeItem item = itemRepository.findById(id)
                .orElseThrow(() -> new BusinessException("知识条目不存在: " + id));
        return toResponseWithCategories(item);
    }

    /**
     * 分页查询
     */
    public PageResult<KnowledgeItemResponse> list(String keyword, Long categoryId, String tag,
                                                   String status, String sort, String order,
                                                   int page, int size) {
        KnowledgeItemStatus statusEnum = EnumUtils.valueOfNullable(KnowledgeItemStatus.class, status);
        SortField sortField = buildSortField(sort, order);

        PageResult<KnowledgeItem> domainPage = itemRepository.findByConditions(
                keyword, categoryId, tag, statusEnum, sortField, page, size
        );

        List<Long> pageItemIds = domainPage.getContent().stream()
                .map(KnowledgeItem::getId).toList();
        Map<Long, List<Long>> categoryMap = pageItemIds.isEmpty()
                ? Map.of()
                : itemRepository.findActiveCategoryIdsByItemIds(pageItemIds);

        return PageResult.<KnowledgeItemResponse>builder()
                .content(domainPage.getContent().stream()
                        .map(item -> KnowledgeItemAssembler.INSTANCE.toResponse(
                                item, categoryMap.getOrDefault(item.getId(), List.of())))
                        .toList())
                .totalElements(domainPage.getTotalElements())
                .pageNumber(domainPage.getPageNumber())
                .pageSize(domainPage.getPageSize())
                .totalPages(domainPage.getTotalPages())
                .build();
    }

    /**
     * 更新知识条目
     */
    @Transactional
    public KnowledgeItemResponse update(Long id, UpdateKnowledgeItemRequest request) {
        KnowledgeItem item = itemRepository.findById(id)
                .orElseThrow(() -> new BusinessException("知识条目不存在: " + id));

        // 内容变更时重新渲染 HTML
        String contentHtml = null;
        if (request.getContent() != null) {
            contentHtml = markdownRenderer.render(request.getContent());
        }

        FileRef coverImage = verifyFileRef(request.getCoverImageFileId(), BIZ_TYPE);

        itemDomainService.validateUpdate(item, request.getTitle(), request.getContent(), request.getTags());

        item.update(request.getTitle(), request.getContent(), coverImage,
                request.getTags(), request.getSortOrder());
        if (contentHtml != null) {
            item.updateContentHtml(contentHtml);
        }
        KnowledgeItem saved = itemRepository.save(item);

        return toResponseWithCategories(saved);
    }

    /**
     * 删除知识条目（软删除，无前置校验）
     */
    @Transactional
    public void delete(Long id) {
        KnowledgeItem item = itemRepository.findById(id)
                .orElseThrow(() -> new BusinessException("知识条目不存在: " + id));
        item.deactivate();
        itemRepository.save(item);
    }

    /**
     * 查询条目关联的分类 ID 列表
     */
    public List<Long> getCategoryIds(Long itemId) {
        if (itemRepository.findById(itemId).isEmpty()) {
            throw new BusinessException("知识条目不存在: " + itemId);
        }
        return itemRepository.findActiveCategoryIdsByItemId(itemId);
    }

    /**
     * 更新条目的分类关联（全量替换）
     */
    @Transactional
    public void updateCategories(Long itemId, List<Long> categoryIds) {
        if (itemRepository.findById(itemId).isEmpty()) {
            throw new BusinessException("知识条目不存在: " + itemId);
        }
        validateCategoryIds(categoryIds);
        itemRepository.saveCategoryRelations(itemId, categoryIds);
    }

    /**
     * 批量启用（含分类状态前置校验）
     */
    @Transactional
    public void batchActivate(List<Long> ids) {
        List<Long> distinctIds = ids.stream().distinct().toList();
        List<KnowledgeItem> items = itemRepository.findByIds(distinctIds);
        if (items.size() != distinctIds.size()) {
            throw new BusinessException("部分知识条目 ID 不存在");
        }
        Map<Long, String> idToName = items.stream()
                .collect(Collectors.toMap(KnowledgeItem::getId, KnowledgeItem::getTitle));
        Map<Long, List<Long>> itemToCategoryIds = itemRepository.findCategoryIdsByItemIds(distinctIds);
        List<Long> allCategoryIds = itemToCategoryIds.values().stream()
                .flatMap(List::stream)
                .distinct()
                .toList();
        Map<Long, KnowledgeCategory> categoryMap = allCategoryIds.isEmpty()
                ? Map.of()
                : categoryRepositoryPort.findAllByIdIn(allCategoryIds).stream()
                        .collect(Collectors.toMap(KnowledgeCategory::getId, c -> c));
        for (Long id : distinctIds) {
            String itemName = idToName.getOrDefault(id, "(ID=" + id + ")");
            List<Long> categoryIds = itemToCategoryIds.getOrDefault(id, List.of());
            itemDomainService.validateActivatable(itemName, categoryIds, categoryMap);
        }
        itemRepository.batchUpdateStatus(distinctIds, KnowledgeItemStatus.ACTIVE);
    }

    /**
     * 批量禁用
     */
    @Transactional
    public void batchDeactivate(List<Long> ids) {
        List<Long> distinctIds = ids.stream().distinct().toList();
        itemRepository.batchUpdateStatus(distinctIds, KnowledgeItemStatus.INACTIVE);
    }

    /**
     * 批量排序（无父子层级，跳过同父级校验）
     */
    @Transactional
    public void batchSort(BatchSortRequest request) {
        List<BatchSortItem> items = request.getItems();
        Map<Long, KnowledgeItem> itemMap = new LinkedHashMap<>();
        for (BatchSortItem sortItem : items) {
            KnowledgeItem ki = itemRepository.findById(sortItem.getId())
                    .orElseThrow(() -> new BusinessException("知识条目不存在: " + sortItem.getId()));
            itemMap.put(sortItem.getId(), ki);
        }
        for (BatchSortItem sortItem : items) {
            KnowledgeItem ki = itemMap.get(sortItem.getId());
            ki.moveToSortOrder(sortItem.getSortOrder());
            itemRepository.save(ki);
        }
    }

    /**
     * 校验分类 ID 列表：必须存在且 ACTIVE
     */
    private void validateCategoryIds(List<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return;
        }
        for (Long categoryId : categoryIds) {
            categoryRepositoryPort.findById(categoryId)
                    .filter(c -> c.getStatus() == KnowledgeCategoryStatus.ACTIVE)
                    .orElseThrow(() -> new BusinessException("分类不存在或已停用: " + categoryId));
        }
    }

    /**
     * 构建排序字段
     */
    private SortField buildSortField(String sort, String order) {
        if (sort == null || sort.isBlank()) {
            return new SortField("sortOrder", SortField.Direction.ASC);
        }
        SortField.Direction direction = "asc".equalsIgnoreCase(order)
                ? SortField.Direction.ASC : SortField.Direction.DESC;
        return new SortField(sort, direction);
    }

    /**
     * 查询条目关联分类并组装响应
     */
    private KnowledgeItemResponse toResponseWithCategories(KnowledgeItem item) {
        List<Long> categoryIds = itemRepository.findActiveCategoryIdsByItemId(item.getId());
        return KnowledgeItemAssembler.INSTANCE.toResponse(item, categoryIds);
    }

    /**
     * 校验 fileId 对应文件的 metadata，用 file 服务返回的 url 组装 FileRef
     */
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
}
