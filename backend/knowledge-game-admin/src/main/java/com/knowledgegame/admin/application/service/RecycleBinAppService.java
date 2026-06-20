package com.knowledgegame.admin.application.service;

import com.knowledgegame.admin.api.assembler.RecycleBinItemAssembler;
import com.knowledgegame.admin.api.dto.request.RecycleBinListRequest;
import com.knowledgegame.admin.api.dto.response.BatchRestoreResult;
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
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 回收站管理端应用服务（流程编排，返回 DTO）
 * <p>
 * restore / batchRestore（REQ-103 已实现）；purge / batchPurge 留后签名（REQ-102 实现）。
 */
@Service
public class RecycleBinAppService {

    private final RecycleBinItemRepositoryPort recycleBinRepository;
    private final RecycleBinItemStrategyRegistry strategyRegistry;
    private final RecycleBinAppService self;

    public RecycleBinAppService(RecycleBinItemRepositoryPort recycleBinRepository,
                                 RecycleBinItemStrategyRegistry strategyRegistry,
                                 @Lazy RecycleBinAppService self) {
        this.recycleBinRepository = recycleBinRepository;
        this.strategyRegistry = strategyRegistry;
        this.self = self;
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

    // ===== 恢复（REQ-103 实现）=====

    /**
     * 单条恢复（REQ-103 实现）
     * <p>
     * 自身不加 {@code @Transactional}，委托 {@link #restoreInNewTransaction(RecycleBinItem)} 在新事务中执行。
     */
    public void restore(Long recycleBinId) {
        RecycleBinItem item = recycleBinRepository.findById(recycleBinId)
                .orElseThrow(() -> new BusinessException(404, "回收站记录不存在: " + recycleBinId));
        self.restoreInNewTransaction(item);
    }

    /**
     * 在新事务中执行单条恢复（仅内部用，由 {@link #restore(Long)} 和 {@link #batchRestore(List)} 调用）
     * <p>
     * 独立新事务保证单条恢复失败时只回滚当前条目，不影响已在其他新事务中成功的条目（批量场景）。
     * {@code public} 是 Spring CGLIB 代理的硬性要求（代理只能拦截 public 方法），
     * 语义上不应从 Controller 直接调用。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void restoreInNewTransaction(RecycleBinItem item) {
        strategyRegistry.get(item.getResourceType()).restore(item.getId());
    }

    /**
     * 批量恢复（REQ-103 实现，方案B：阶段 1 预校验 + 阶段 2 逐条独立事务）
     * <p>
     * 重复 id 自动去重（findAllById 天然去重）。HTTP 总是返回 200，成败由响应体描述。
     */
    public BatchRestoreResult batchRestore(List<Long> recycleBinIds) {
        // 去重
        List<Long> distinctIds = recycleBinIds.stream().distinct().toList();

        // 阶段 1：批量查回所有存在的条目
        List<RecycleBinItem> items = recycleBinRepository.findAllById(distinctIds);
        Map<Long, RecycleBinItem> itemMap = new HashMap<>();
        for (RecycleBinItem item : items) {
            itemMap.put(item.getId(), item);
        }

        // 预校验：不存在的 ID 记入失败
        List<Long> successIds = new ArrayList<>();
        List<BatchRestoreResult.Failure> failures = new ArrayList<>();
        for (Long id : distinctIds) {
            if (!itemMap.containsKey(id)) {
                failures.add(new BatchRestoreResult.Failure(id, "回收站记录不存在: " + id));
            }
        }

        // 阶段 2：逐条独立事务恢复
        for (RecycleBinItem item : items) {
            try {
                self.restoreInNewTransaction(item);
                successIds.add(item.getId());
            } catch (BusinessException e) {
                failures.add(new BatchRestoreResult.Failure(item.getId(), e.getMessage()));
            } catch (Exception e) {
                failures.add(new BatchRestoreResult.Failure(item.getId(), "恢复失败，请联系管理员"));
            }
        }

        return new BatchRestoreResult(successIds, failures);
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
