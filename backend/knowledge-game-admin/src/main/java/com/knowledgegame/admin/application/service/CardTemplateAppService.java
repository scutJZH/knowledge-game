package com.knowledgegame.admin.application.service;

import com.knowledgegame.admin.api.assembler.CardTemplateAssembler;
import com.knowledgegame.admin.api.dto.response.CardTemplateListResponse;
import com.knowledgegame.admin.api.dto.response.CardTemplateResponse;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.common.util.EnumUtils;
import com.knowledgegame.core.domain.model.domainenum.CardRarity;
import com.knowledgegame.core.domain.model.domainenum.CardTemplateStatus;
import com.knowledgegame.core.domain.model.entity.CardTemplate;
import com.knowledgegame.core.domain.model.vo.PageResult;
import com.knowledgegame.core.domain.port.outbound.CardTemplateRepositoryPort;
import com.knowledgegame.core.domain.port.outbound.IpSeriesRepositoryPort;
import com.knowledgegame.core.domain.service.CardTemplateDomainService;
import com.knowledgegame.core.domain.service.IpSeriesDomainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 卡牌模板管理端应用服务（流程编排 + 事务，返回 DTO）
 */
@Service
public class CardTemplateAppService {

    private final CardTemplateDomainService cardTemplateDomainService;
    private final CardTemplateRepositoryPort cardTemplateRepositoryPort;
    private final IpSeriesRepositoryPort ipSeriesRepositoryPort;
    private final IpSeriesDomainService ipSeriesDomainService;

    public CardTemplateAppService(CardTemplateDomainService cardTemplateDomainService,
                                  CardTemplateRepositoryPort cardTemplateRepositoryPort,
                                  IpSeriesRepositoryPort ipSeriesRepositoryPort,
                                  IpSeriesDomainService ipSeriesDomainService) {
        this.cardTemplateDomainService = cardTemplateDomainService;
        this.cardTemplateRepositoryPort = cardTemplateRepositoryPort;
        this.ipSeriesRepositoryPort = ipSeriesRepositoryPort;
        this.ipSeriesDomainService = ipSeriesDomainService;
    }

    /**
     * 创建卡牌模板
     */
    @Transactional
    public CardTemplateResponse createCardTemplate(Long ipSeriesId, String code, String name,
                                                   CardRarity rarity, String description,
                                                   CardTemplateStatus status, String imageUrl) {
        // 编码在同一 IP 系列下唯一
        cardTemplateRepositoryPort.findByIpSeriesIdAndCode(ipSeriesId, code).ifPresent(existing -> {
            throw new BusinessException("卡牌编码已存在: " + code);
        });
        // 领域服务校验 IpSeries + 创建聚合根
        CardTemplate template = cardTemplateDomainService.validateAndCreate(
                ipSeriesId, code, name, rarity, description, status, imageUrl);
        CardTemplate saved = cardTemplateRepositoryPort.save(template);
        return assembleDetailResponse(saved);
    }

    /**
     * 根据 ID 查询详情（含 IP 系列名称）
     */
    @Transactional(readOnly = true)
    public CardTemplateResponse getCardTemplateById(Long id) {
        CardTemplate template = cardTemplateRepositoryPort.findById(id)
                .orElseThrow(() -> new BusinessException("卡牌模板不存在: " + id));
        return assembleDetailResponse(template);
    }

    /**
     * 分页查询（列表不含图片）
     */
    @Transactional(readOnly = true)
    public PageResult<CardTemplateListResponse> listCardTemplates(String name, Long ipSeriesId,
                                                                   String rarity, String status,
                                                                   int pageNumber, int pageSize) {
        CardRarity rarityEnum = EnumUtils.valueOfNullable(CardRarity.class, rarity);
        CardTemplateStatus statusEnum = EnumUtils.valueOfNullable(CardTemplateStatus.class, status);
        PageResult<CardTemplate> domainPage = cardTemplateRepositoryPort.findByConditions(
                name, ipSeriesId, rarityEnum, statusEnum, pageNumber, pageSize);
        return PageResult.<CardTemplateListResponse>builder()
                .content(domainPage.getContent().stream()
                        .map(template -> {
                            String ipSeriesName = resolveIpSeriesName(template.getIpSeriesId());
                            return CardTemplateAssembler.INSTANCE.toListResponse(template, ipSeriesName);
                        }).toList())
                .totalElements(domainPage.getTotalElements())
                .pageNumber(domainPage.getPageNumber())
                .pageSize(domainPage.getPageSize())
                .totalPages(domainPage.getTotalPages())
                .build();
    }

    /**
     * 更新卡牌模板基础信息
     */
    @Transactional
    public CardTemplateResponse updateCardTemplate(Long id, String code, String name,
                                                   CardRarity rarity, String description,
                                                   CardTemplateStatus status, String imageUrl) {
        CardTemplate template = cardTemplateRepositoryPort.findById(id)
                .orElseThrow(() -> new BusinessException("卡牌模板不存在: " + id));
        // 编码在同一 IP 系列下唯一（排除自身）
        if (code != null && !code.equals(template.getCode())) {
            cardTemplateRepositoryPort.findByIpSeriesIdAndCode(template.getIpSeriesId(), code)
                    .ifPresent(existing -> {
                        throw new BusinessException("卡牌编码已存在: " + code);
                    });
        }
        // 状态切换 INACTIVE → ACTIVE 时校验 IP 系列状态
        boolean activating = status == CardTemplateStatus.ACTIVE
                && template.getStatus() != CardTemplateStatus.ACTIVE;
        if (activating) {
            ipSeriesDomainService.validateCardActivatable(template);
        }
        template.update(code, name, rarity, description, status, imageUrl);
        CardTemplate saved = cardTemplateRepositoryPort.save(template);
        return assembleDetailResponse(saved);
    }

    /**
     * 软删除卡牌模板
     */
    @Transactional
    public void deleteCardTemplate(Long id) {
        CardTemplate template = cardTemplateRepositoryPort.findById(id)
                .orElseThrow(() -> new BusinessException("卡牌模板不存在: " + id));
        // TODO: 检查是否有关联用户收集，有则不允许删除
        template.deactivate();
        cardTemplateRepositoryPort.save(template);
    }

    /**
     * 批量启用卡牌模板
     */
    @Transactional
    public void batchActivate(List<Long> ids) {
        // ids 非空由 BatchStatusRequest.@NotEmpty @Size 在 DTO 层保证
        // 去重避免 JPA findAllById 返回列表大小与输入不一致导致误报
        List<Long> distinctIds = ids.stream().distinct().toList();
        List<CardTemplate> cards = cardTemplateRepositoryPort.findAllByIdIn(distinctIds);
        if (cards.size() != distinctIds.size()) {
            throw new BusinessException("部分卡牌 ID 不存在");
        }
        // 仅对当前 INACTIVE 的卡牌校验（已 ACTIVE 跳过）
        List<CardTemplate> toActivate = cards.stream()
                .filter(c -> c.getStatus() != CardTemplateStatus.ACTIVE)
                .toList();
        ipSeriesDomainService.validateCardsActivatable(toActivate);
        cardTemplateRepositoryPort.batchUpdateStatus(distinctIds, CardTemplateStatus.ACTIVE);
    }

    /**
     * 批量停用卡牌模板
     */
    @Transactional
    public void batchDeactivate(List<Long> ids) {
        // ids 非空由 DTO 层保证；去重与 batchActivate 对称
        List<Long> distinctIds = ids.stream().distinct().toList();
        // 存在性校验：与 batchActivate 对称，避免静默成功
        List<CardTemplate> cards = cardTemplateRepositoryPort.findAllByIdIn(distinctIds);
        if (cards.size() != distinctIds.size()) {
            throw new BusinessException("部分卡牌 ID 不存在");
        }
        cardTemplateRepositoryPort.batchUpdateStatus(distinctIds, CardTemplateStatus.INACTIVE);
    }

    /**
     * 组装详情响应（含 ipSeriesName）
     */
    private CardTemplateResponse assembleDetailResponse(CardTemplate template) {
        String ipSeriesName = resolveIpSeriesName(template.getIpSeriesId());
        return CardTemplateAssembler.INSTANCE.toResponse(template, ipSeriesName);
    }

    /**
     * 解析 IP 系列名称
     */
    private String resolveIpSeriesName(Long ipSeriesId) {
        return ipSeriesRepositoryPort.findById(ipSeriesId)
                .map(ip -> ip.getName())
                .orElse("未知");
    }
}
