package com.knowledgegame.core.domain.service;

import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.domainenum.CardRarity;
import com.knowledgegame.core.domain.model.domainenum.CardTemplateStatus;
import com.knowledgegame.core.domain.model.domainenum.IpSeriesStatus;
import com.knowledgegame.core.domain.model.entity.CardTemplate;
import com.knowledgegame.core.domain.port.outbound.IpSeriesRepositoryPort;

/**
 * 卡牌模板领域服务（跨聚合校验，纯 POJO）
 */
public class CardTemplateDomainService {

    private final IpSeriesRepositoryPort ipSeriesRepositoryPort;

    public CardTemplateDomainService(IpSeriesRepositoryPort ipSeriesRepositoryPort) {
        this.ipSeriesRepositoryPort = ipSeriesRepositoryPort;
    }

    /**
     * 校验 IpSeries 存在且 ACTIVE，然后创建卡牌模板
     */
    public CardTemplate validateAndCreate(Long ipSeriesId, String code, String name,
                                          CardRarity rarity, String description,
                                          CardTemplateStatus status, String imageUrl) {
        // 校验 IP 系列存在
        var ipSeries = ipSeriesRepositoryPort.findById(ipSeriesId)
                .orElseThrow(() -> new BusinessException("IP 系列不存在: " + ipSeriesId));
        // 校验 IP 系列状态为 ACTIVE
        if (ipSeries.getStatus() != IpSeriesStatus.ACTIVE) {
            throw new BusinessException("IP 系列未启用: " + ipSeriesId);
        }
        return CardTemplate.create(ipSeriesId, code, name, rarity, description, status, imageUrl);
    }
}
