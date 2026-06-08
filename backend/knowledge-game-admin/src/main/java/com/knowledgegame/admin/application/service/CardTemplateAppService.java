package com.knowledgegame.admin.application.service;

import com.knowledgegame.admin.api.assembler.CardTemplateAssembler;
import com.knowledgegame.admin.api.dto.response.CardTemplateListResponse;
import com.knowledgegame.admin.api.dto.response.CardTemplateResponse;
import com.knowledgegame.admin.application.command.StarImageCommand;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.domainenum.CardRarity;
import com.knowledgegame.core.domain.model.domainenum.CardTemplateStatus;
import com.knowledgegame.core.domain.model.entity.CardTemplate;
import com.knowledgegame.core.domain.model.vo.CardStarImage;
import com.knowledgegame.core.domain.model.vo.PageResult;
import com.knowledgegame.core.domain.port.outbound.CardTemplateRepositoryPort;
import com.knowledgegame.core.domain.port.outbound.IpSeriesRepositoryPort;
import com.knowledgegame.core.domain.service.CardTemplateDomainService;
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

    public CardTemplateAppService(CardTemplateDomainService cardTemplateDomainService,
                                  CardTemplateRepositoryPort cardTemplateRepositoryPort,
                                  IpSeriesRepositoryPort ipSeriesRepositoryPort) {
        this.cardTemplateDomainService = cardTemplateDomainService;
        this.cardTemplateRepositoryPort = cardTemplateRepositoryPort;
        this.ipSeriesRepositoryPort = ipSeriesRepositoryPort;
    }

    /**
     * 创建卡牌模板
     */
    @Transactional
    public CardTemplateResponse createCardTemplate(Long ipSeriesId, String code, String name,
                                                   CardRarity rarity, String description,
                                                   CardTemplateStatus status,
                                                   List<StarImageCommand> starImageCommands) {
        // code 唯一性校验
        cardTemplateRepositoryPort.findByCode(code).ifPresent(existing -> {
            throw new BusinessException("卡牌编码已存在: " + code);
        });
        // 将应用层命令转换为领域值对象
        List<CardStarImage> starImages = starImageCommands.stream()
                .map(cmd -> CardStarImage.create(cmd.getStarLevel(), cmd.getImageUrl()))
                .toList();
        // 领域服务校验 IpSeries + 创建聚合根
        CardTemplate template = cardTemplateDomainService.validateAndCreate(
                ipSeriesId, code, name, rarity, description, status, starImages);
        CardTemplate saved = cardTemplateRepositoryPort.save(template);
        return assembleDetailResponse(saved);
    }

    /**
     * 根据 ID 查询详情（含星级图片 + IP 系列名称）
     */
    public CardTemplateResponse getCardTemplateById(Long id) {
        CardTemplate template = cardTemplateRepositoryPort.findById(id)
                .orElseThrow(() -> new BusinessException("卡牌模板不存在: " + id));
        return assembleDetailResponse(template);
    }

    /**
     * 分页查询（列表不含星级图片）
     */
    public PageResult<CardTemplateListResponse> listCardTemplates(String name, Long ipSeriesId,
                                                                   String rarity, String status,
                                                                   int pageNumber, int pageSize) {
        CardRarity rarityEnum = rarity != null ? CardRarity.valueOf(rarity) : null;
        CardTemplateStatus statusEnum = status != null ? CardTemplateStatus.valueOf(status) : null;
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
                                                   CardTemplateStatus status) {
        CardTemplate template = cardTemplateRepositoryPort.findById(id)
                .orElseThrow(() -> new BusinessException("卡牌模板不存在: " + id));
        // code 唯一性校验（排除自身）
        if (code != null && !code.equals(template.getCode())) {
            cardTemplateRepositoryPort.findByCode(code).ifPresent(existing -> {
                throw new BusinessException("卡牌编码已存在: " + code);
            });
        }
        template.update(code, name, rarity, description, status);
        CardTemplate saved = cardTemplateRepositoryPort.save(template);
        return assembleDetailResponse(saved);
    }

    /**
     * 添加/替换单张星级图片
     */
    @Transactional
    public CardTemplateResponse addOrUpdateStarImage(Long id, int starLevel, String imageUrl) {
        CardTemplate template = cardTemplateRepositoryPort.findById(id)
                .orElseThrow(() -> new BusinessException("卡牌模板不存在: " + id));
        template.addOrUpdateStarImage(CardStarImage.create(starLevel, imageUrl));
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
