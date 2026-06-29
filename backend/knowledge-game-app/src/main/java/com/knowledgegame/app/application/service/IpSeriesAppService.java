package com.knowledgegame.app.application.service;

import com.knowledgegame.app.api.assembler.IpSeriesAssembler;
import com.knowledgegame.app.api.dto.response.ActiveIpSeriesResponse;
import com.knowledgegame.core.domain.port.outbound.IpSeriesRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * IP 系列用户端应用服务
 */
@Service
@Transactional(readOnly = true)
public class IpSeriesAppService {

    private final IpSeriesRepositoryPort ipSeriesRepositoryPort;

    public IpSeriesAppService(IpSeriesRepositoryPort ipSeriesRepositoryPort) {
        this.ipSeriesRepositoryPort = ipSeriesRepositoryPort;
    }

    /**
     * 查询全部 ACTIVE 状态的 IP 系列
     */
    public List<ActiveIpSeriesResponse> listActive() {
        return IpSeriesAssembler.INSTANCE.toActiveResponseList(
                ipSeriesRepositoryPort.findAllActive());
    }
}
