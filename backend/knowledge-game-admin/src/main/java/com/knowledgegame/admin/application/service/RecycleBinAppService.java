package com.knowledgegame.admin.application.service;

import com.knowledgegame.admin.api.assembler.RecycleBinItemAssembler;
import com.knowledgegame.admin.api.dto.request.RecycleBinListRequest;
import com.knowledgegame.admin.api.dto.response.RecycleBinItemResponse;
import com.knowledgegame.admin.api.dto.response.SupportedTypeResponse;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.common.result.ResultCode;
import com.knowledgegame.core.domain.model.domainenum.ResourceType;
import com.knowledgegame.core.domain.model.entity.RecycleBinItem;
import com.knowledgegame.core.domain.model.vo.PageResult;
import com.knowledgegame.core.domain.model.vo.SortField;
import com.knowledgegame.core.domain.port.outbound.RecycleBinItemRepositoryPort;
import com.knowledgegame.core.domain.service.recyclebin.RecycleBinItemStrategyRegistry;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 回收站管理端应用服务（流程编排，返回 DTO）
 * <p>
 * 本需求实现列表查询和 supportedTypes；
 * restore/batchRestore/purge/batchPurge 留后签名，REQ-102/103 实现。
 */
@Service
public class RecycleBinAppService {

    private final RecycleBinItemRepositoryPort recycleBinRepository;
    private final RecycleBinItemStrategyRegistry strategyRegistry;

    public RecycleBinAppService(RecycleBinItemRepositoryPort recycleBinRepository,
                                 RecycleBinItemStrategyRegistry strategyRegistry) {
        this.recycleBinRepository = recycleBinRepository;
        this.strategyRegistry = strategyRegistry;
    }

    /**
     * 分页查询回收站列表
     */
    public PageResult<RecycleBinItemResponse> list(RecycleBinListRequest request) {
        SortField sortField = SortField.parse(request.getSort(), request.getOrder());
        ResourceType type = parseResourceType(request.getResourceType());
        PageResult<RecycleBinItem> result = recycleBinRepository.findAll(
                type, request.getKeyword(),
                request.getPage() != null ? request.getPage() : 0,
                request.getSize() != null ? request.getSize() : 20,
                sortField);
        return PageResult.<RecycleBinItemResponse>builder()
                .content(result.getContent().stream()
                        .map(RecycleBinItemAssembler.INSTANCE::toResponse).toList())
                .totalElements(result.getTotalElements())
                .pageNumber(result.getPageNumber())
                .pageSize(result.getPageSize())
                .totalPages(result.getTotalPages())
                .build();
    }

    /**
     * 已接入回收站的资源类型（驱动前端目录树）
     */
    public List<SupportedTypeResponse> supportedTypes() {
        return strategyRegistry.supportedTypes().stream()
                .map(t -> new SupportedTypeResponse(t.name(), t.displayName()))
                .sorted(Comparator.comparing(SupportedTypeResponse::displayName))
                .toList();
    }

    /**
     * 解析 resourceType 参数
     * <p>
     * null / 空 / "ALL"（大小写不敏感）→ 返回 null（不过滤）；
     * 合法枚举字符串 → 返回 ResourceType；
     * 非法值 → 抛 BusinessException(400)。
     */
    private ResourceType parseResourceType(String raw) {
        if (raw == null || raw.isBlank() || "ALL".equalsIgnoreCase(raw)) {
            return null;
        }
        try {
            return ResourceType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "不支持的资源类型: " + raw
                    + "，允许的值: ALL, " + Arrays.stream(ResourceType.values())
                    .map(Enum::name).collect(Collectors.joining(", ")));
        }
    }

    // ===== 留后方法（REQ-102/103 实现）=====

    /**
     * 单条恢复（REQ-103 实现）
     */
    public void restore(Long recycleBinId) {
        RecycleBinItem item = recycleBinRepository.findById(recycleBinId)
                .orElseThrow(() -> new BusinessException(404, "回收站记录不存在: " + recycleBinId));
        strategyRegistry.get(item.getResourceType()).restore(recycleBinId);
    }

    /**
     * 批量恢复（REQ-103 实现）
     */
    public void batchRestore(List<Long> recycleBinIds) {
        Map<ResourceType, List<Long>> grouped = groupByResourceType(recycleBinIds);
        grouped.forEach((type, ids) -> strategyRegistry.get(type).batchRestore(ids));
    }

    /**
     * 单条永久删除（REQ-102 实现）
     */
    public void purge(Long recycleBinId) {
        RecycleBinItem item = recycleBinRepository.findById(recycleBinId)
                .orElseThrow(() -> new BusinessException(404, "回收站记录不存在: " + recycleBinId));
        strategyRegistry.get(item.getResourceType()).purge(recycleBinId);
    }

    /**
     * 批量永久删除（REQ-102 实现）
     */
    public void batchPurge(List<Long> recycleBinIds) {
        Map<ResourceType, List<Long>> grouped = groupByResourceType(recycleBinIds);
        grouped.forEach((type, ids) -> strategyRegistry.get(type).batchPurge(ids));
    }

    private Map<ResourceType, List<Long>> groupByResourceType(List<Long> recycleBinIds) {
        return recycleBinIds.stream()
                .collect(Collectors.groupingBy(
                        id -> recycleBinRepository.findById(id)
                                .orElseThrow(() -> new BusinessException(404, "回收站记录不存在: " + id))
                                .getResourceType()
                ));
    }
}
