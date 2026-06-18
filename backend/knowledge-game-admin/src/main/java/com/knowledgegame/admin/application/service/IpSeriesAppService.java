package com.knowledgegame.admin.application.service;

import com.knowledgegame.admin.api.assembler.IpSeriesAssembler;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;

/**
 * IP 系列管理端应用服务（流程编排 + 事务，返回 DTO）
 */
@Service
public class IpSeriesAppService {

    private final IpSeriesRepositoryPort ipSeriesRepositoryPort;
    private final FileServiceClient fileServiceClient;


    public IpSeriesAppService(IpSeriesRepositoryPort ipSeriesRepositoryPort,
                               FileServiceClient fileServiceClient) {
        this.ipSeriesRepositoryPort = ipSeriesRepositoryPort;
        this.fileServiceClient = fileServiceClient;
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
     */
    @Transactional
    public IpSeriesResponse updateIpSeries(Long id, String code, String name, String description,
                                            Long coverImageFileId, IpSeriesStatus status) {
        IpSeries ipSeries = ipSeriesRepositoryPort.findById(id)
                .orElseThrow(() -> new BusinessException("IP 系列不存在: " + id));
        if (code != null && !code.equals(ipSeries.getCode())) {
            ipSeriesRepositoryPort.findByCode(code).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    throw new BusinessException("IP 系列编码已存在: " + code);
                }
            });
        }
        if (name != null && !name.equals(ipSeries.getName())) {
            ipSeriesRepositoryPort.findByName(name).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    throw new BusinessException("IP 系列名称已存在: " + name);
                }
            });
        }
        FileRef coverImage = verifyFileRef(coverImageFileId, "IP_SERIES");
        ipSeries.update(code, name, description, coverImage, status);
        IpSeries saved = ipSeriesRepositoryPort.save(ipSeries);
        return IpSeriesAssembler.INSTANCE.toResponse(saved);
    }

    /**
     * 软删除 IP 系列
     */
    @Transactional
    public void deleteIpSeries(Long id) {
        IpSeries ipSeries = ipSeriesRepositoryPort.findById(id)
                .orElseThrow(() -> new BusinessException("IP 系列不存在: " + id));
        ipSeries.deactivate();
        ipSeriesRepositoryPort.save(ipSeries);
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
