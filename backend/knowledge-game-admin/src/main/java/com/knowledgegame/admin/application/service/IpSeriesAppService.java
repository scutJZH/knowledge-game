package com.knowledgegame.admin.application.service;

import com.knowledgegame.admin.api.assembler.IpSeriesAssembler;
import com.knowledgegame.admin.api.dto.request.UpdateIpSeriesRequest;
import com.knowledgegame.admin.api.dto.response.IpSeriesResponse;
import com.knowledgegame.auth.security.SecurityUtils;
import com.knowledgegame.components.feign.client.FileServiceClient;
import com.knowledgegame.components.feign.dto.FileInfoResponse;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.common.result.Result;
import com.knowledgegame.core.common.util.EnumUtils;
import com.knowledgegame.core.domain.model.domainenum.IpSeriesStatus;
import com.knowledgegame.core.domain.model.entity.IpSeries;
import com.knowledgegame.core.domain.model.vo.FileRef;
import com.knowledgegame.core.domain.model.vo.PageResult;
import com.knowledgegame.core.domain.model.vo.SortField;
import com.knowledgegame.core.domain.port.outbound.IpSeriesRepositoryPort;
import com.knowledgegame.core.domain.service.IpSeriesDomainService;
import com.knowledgegame.core.infrastructure.adapter.repoadapter.IpSeriesRecycleBinStrategy;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * IP 系列管理端应用服务（流程编排 + 事务，返回 DTO）
 */
@Service
public class IpSeriesAppService {

    private final IpSeriesRepositoryPort ipSeriesRepositoryPort;
    private final FileServiceClient fileServiceClient;
    private final IpSeriesDomainService ipSeriesDomainService;
    private final IpSeriesRecycleBinStrategy ipSeriesRecycleBinStrategy;


    public IpSeriesAppService(IpSeriesRepositoryPort ipSeriesRepositoryPort,
                               FileServiceClient fileServiceClient,
                               IpSeriesDomainService ipSeriesDomainService,
                               IpSeriesRecycleBinStrategy ipSeriesRecycleBinStrategy) {
        this.ipSeriesRepositoryPort = ipSeriesRepositoryPort;
        this.fileServiceClient = fileServiceClient;
        this.ipSeriesDomainService = ipSeriesDomainService;
        this.ipSeriesRecycleBinStrategy = ipSeriesRecycleBinStrategy;
    }

    /**
     * 创建 IP 系列
     */
    @Transactional
    public IpSeriesResponse createIpSeries(String code, String name, String description,
                                            Long coverImageFileId, IpSeriesStatus status) {
        ipSeriesRepositoryPort.findByCode(code).ifPresent(existing -> {
            throw new BusinessException("IP 系列编码已存在: " + code);
        });
        ipSeriesRepositoryPort.findByName(name).ifPresent(existing -> {
            throw new BusinessException("IP 系列名称已存在: " + name);
        });
        FileRef coverImage = verifyFileRef(coverImageFileId, "IP_SERIES");
        IpSeries ipSeries = IpSeries.create(code, name, description, coverImage, status);
        IpSeries saved = ipSeriesRepositoryPort.save(ipSeries);
        return IpSeriesAssembler.INSTANCE.toResponse(saved);
    }

    /**
     * 根据 ID 查询
     */
    public IpSeriesResponse getIpSeriesById(Long id) {
        IpSeries ipSeries = ipSeriesRepositoryPort.findById(id)
                .orElseThrow(() -> new BusinessException("IP 系列不存在: " + id));
        return IpSeriesAssembler.INSTANCE.toResponse(ipSeries);
    }

    /**
     * 分页查询（支持 name/code 模糊搜索、状态筛选、参数化排序）
     */
    public PageResult<IpSeriesResponse> listIpSeries(String name, String code, String status,
                                                      String sort, String order,
                                                      int pageNumber, int pageSize) {
        IpSeriesStatus statusEnum = EnumUtils.valueOfNullable(IpSeriesStatus.class, status);
        SortField sortField = SortField.parse(sort, order);
        PageResult<IpSeries> domainPage = ipSeriesRepositoryPort.findByConditions(
                name, code, statusEnum, sortField, pageNumber, pageSize);
        return PageResult.<IpSeriesResponse>builder()
                .content(domainPage.getContent().stream()
                        .map(IpSeriesAssembler.INSTANCE::toResponse).toList())
                .totalElements(domainPage.getTotalElements())
                .pageNumber(domainPage.getPageNumber())
                .pageSize(domainPage.getPageSize())
                .totalPages(domainPage.getTotalPages())
                .build();
    }

    /**
     * 更新 IP 系列
     * <p>
     * 接收整个 Request DTO（不逐字段拆包），以便解析 JsonNullable 三态。
     */
    @Transactional
    public IpSeriesResponse update(Long id, UpdateIpSeriesRequest req) {
        IpSeries ipSeries = ipSeriesRepositoryPort.findById(id)
                .orElseThrow(() -> new BusinessException("IP 系列不存在: " + id));
        if (req.getCode() != null && !req.getCode().equals(ipSeries.getCode())) {
            ipSeriesRepositoryPort.findByCode(req.getCode()).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    throw new BusinessException("IP 系列编码已存在: " + req.getCode());
                }
            });
        }
        if (req.getName() != null && !req.getName().equals(ipSeries.getName())) {
            ipSeriesRepositoryPort.findByName(req.getName()).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    throw new BusinessException("IP 系列名称已存在: " + req.getName());
                }
            });
        }

        // 停用前校验：ACTIVE → INACTIVE 时检查是否有 ACTIVE 卡牌引用
        if (req.getStatus() == IpSeriesStatus.INACTIVE
                && ipSeries.getStatus() == IpSeriesStatus.ACTIVE) {
            ipSeriesDomainService.validateDeactivatable(id);
        }

        // 必填字段：null=不更新
        ipSeries.update(req.getCode(), req.getName(), req.getStatus());

        // 可清空 String：description
        applyField(req.getDescription(), ipSeries::clearDescription, ipSeries::updateDescription);

        // 可清空 FileRef：coverImage
        applyFileRefField(req.getCoverImageFileId(), "IP_SERIES",
                ipSeries::clearCoverImage, ipSeries::updateCoverImage);

        IpSeries saved = ipSeriesRepositoryPort.save(ipSeries);
        return IpSeriesAssembler.INSTANCE.toResponse(saved);
    }

    /**
     * 删除 IP 系列（移入回收站）
     */
    @Transactional
    public void deleteIpSeries(Long id) {
        if (!ipSeriesRepositoryPort.existsById(id)) {
            throw new BusinessException("IP 系列不存在: " + id);
        }
        ipSeriesRecycleBinStrategy.validateDeletable(id);
        ipSeriesRecycleBinStrategy.moveToRecycleBin(id, SecurityUtils.getCurrentUsername());
    }

    /**
     * 三态分派工具：处理 JsonNullable<T> 字段
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
