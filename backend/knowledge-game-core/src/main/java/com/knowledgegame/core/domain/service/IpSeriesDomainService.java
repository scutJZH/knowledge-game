package com.knowledgegame.core.domain.service;

import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.domainenum.IpSeriesStatus;
import com.knowledgegame.core.domain.model.entity.CardTemplate;
import com.knowledgegame.core.domain.model.entity.IpSeries;
import com.knowledgegame.core.domain.port.outbound.CardTemplateRepositoryPort;
import com.knowledgegame.core.domain.port.outbound.IpSeriesRepositoryPort;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * IP 系列领域服务（跨聚合校验，纯 POJO）
 */
public class IpSeriesDomainService {

    private final CardTemplateRepositoryPort cardTemplateRepositoryPort;
    private final IpSeriesRepositoryPort ipSeriesRepositoryPort;

    public IpSeriesDomainService(CardTemplateRepositoryPort cardTemplateRepositoryPort,
                                  IpSeriesRepositoryPort ipSeriesRepositoryPort) {
        this.cardTemplateRepositoryPort = cardTemplateRepositoryPort;
        this.ipSeriesRepositoryPort = ipSeriesRepositoryPort;
    }

    /**
     * 校验 IP 系列是否可停用：存在 ACTIVE 卡牌时拒绝
     */
    public void validateDeactivatable(Long ipSeriesId) {
        long activeCardCount = cardTemplateRepositoryPort.countActiveByIpSeriesId(ipSeriesId);
        if (activeCardCount > 0) {
            throw new BusinessException("IP 系列存在 " + activeCardCount + " 张 ACTIVE 卡牌，无法停用");
        }
    }

    /**
     * 校验卡牌可否启用：关联的 IP 系列必须 ACTIVE（单条场景）
     */
    public void validateCardActivatable(CardTemplate card) {
        IpSeries ip = ipSeriesRepositoryPort.findById(card.getIpSeriesId())
                .orElseThrow(() -> new BusinessException("卡牌关联的 IP 系列不存在: " + card.getIpSeriesId()));
        if (ip.getStatus() != IpSeriesStatus.ACTIVE) {
            throw new BusinessException(
                    "卡牌《" + card.getName() + "》关联的 IP 系列《" + ip.getName() + "》处于停用状态，"
                            + "请先启用 IP 系列再启用卡牌");
        }
    }

    /**
     * 校验批量卡牌可否启用：批量加载 IP 系列后内存匹配，首个失败即抛异常
     */
    public void validateCardsActivatable(List<CardTemplate> cards) {
        // 收集所有唯一 ipSeriesId，一次性批量加载
        List<Long> uniqueIpIds = cards.stream()
                .map(CardTemplate::getIpSeriesId)
                .distinct()
                .toList();
        Map<Long, IpSeries> ipMap = new HashMap<>();
        if (!uniqueIpIds.isEmpty()) {
            ipMap = ipSeriesRepositoryPort.findAllByIdIn(uniqueIpIds).stream()
                    .collect(Collectors.toMap(IpSeries::getId, ip -> ip));
        }
        // 内存中匹配校验
        for (CardTemplate card : cards) {
            IpSeries ip = ipMap.get(card.getIpSeriesId());
            if (ip == null) {
                throw new BusinessException(
                        "卡牌《" + card.getName() + "》关联的 IP 系列不存在（ID="
                                + card.getIpSeriesId() + "），请检查数据完整性");
            }
            if (ip.getStatus() != IpSeriesStatus.ACTIVE) {
                throw new BusinessException(
                        "卡牌《" + card.getName() + "》关联的 IP 系列《" + ip.getName() + "》处于停用状态，"
                                + "请先启用 IP 系列再启用卡牌");
            }
        }
    }
}
