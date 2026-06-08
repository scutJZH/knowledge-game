package com.knowledgegame.admin.application.service;

import com.knowledgegame.admin.api.assembler.IpSeriesAssembler;
import com.knowledgegame.admin.api.dto.response.IpSeriesResponse;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.domainenum.IpSeriesStatus;
import com.knowledgegame.core.domain.model.entity.IpSeries;
import com.knowledgegame.core.domain.model.vo.PageResult;
import com.knowledgegame.core.domain.port.outbound.IpSeriesRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * IP 系列管理端应用服务（流程编排 + 事务，返回 DTO）
 */
@Service
public class IpSeriesAppService {

    private final IpSeriesRepositoryPort ipSeriesRepositoryPort;

    public IpSeriesAppService(IpSeriesRepositoryPort ipSeriesRepositoryPort) {
        this.ipSeriesRepositoryPort = ipSeriesRepositoryPort;
    }

    /**
     * 创建 IP 系列
     */
    @Transactional
    public IpSeriesResponse createIpSeries(String code, String name, String description,
                                            String coverImageUrl, IpSeriesStatus status) {
        // code 唯一性校验
        ipSeriesRepositoryPort.findByCode(code).ifPresent(existing -> {
            throw new BusinessException("IP 系列编码已存在: " + code);
        });
        // name 唯一性校验
        ipSeriesRepositoryPort.findByName(name).ifPresent(existing -> {
            throw new BusinessException("IP 系列名称已存在: " + name);
        });
        IpSeries ipSeries = IpSeries.create(code, name, description, coverImageUrl, status);
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
     * 分页查询
     */
    public PageResult<IpSeriesResponse> listIpSeries(String name, String status,
                                                      int pageNumber, int pageSize) {
        IpSeriesStatus statusEnum = status != null ? IpSeriesStatus.valueOf(status) : null;
        PageResult<IpSeries> domainPage = ipSeriesRepositoryPort.findByConditions(
                name, statusEnum, pageNumber, pageSize);
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
                                            String coverImageUrl, IpSeriesStatus status) {
        IpSeries ipSeries = ipSeriesRepositoryPort.findById(id)
                .orElseThrow(() -> new BusinessException("IP 系列不存在: " + id));
        // code 唯一性校验（排除自身）
        if (code != null && !code.equals(ipSeries.getCode())) {
            ipSeriesRepositoryPort.findByCode(code).ifPresent(existing -> {
                throw new BusinessException("IP 系列编码已存在: " + code);
            });
        }
        // name 唯一性校验（排除自身）
        if (name != null && !name.equals(ipSeries.getName())) {
            ipSeriesRepositoryPort.findByName(name).ifPresent(existing -> {
                throw new BusinessException("IP 系列名称已存在: " + name);
            });
        }
        ipSeries.update(code, name, description, coverImageUrl, status);
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
        // TODO: 检查是否有关联卡牌，有则不允许删除
        ipSeries.deactivate();
        ipSeriesRepositoryPort.save(ipSeries);
    }
}
