package com.knowledgegame.admin.application.service;

import com.knowledgegame.admin.api.assembler.CardTemplateAssembler;
import com.knowledgegame.admin.api.dto.response.CardTemplateListResponse;
import com.knowledgegame.admin.api.dto.response.CardTemplateResponse;
import com.knowledgegame.auth.security.SecurityUtils;
import com.knowledgegame.components.feign.client.FileServiceClient;
import com.knowledgegame.components.feign.dto.FileInfoResponse;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.common.result.Result;
import com.knowledgegame.core.common.util.EnumUtils;
import com.knowledgegame.core.domain.model.domainenum.CardRarity;
import com.knowledgegame.core.domain.model.domainenum.CardTemplateStatus;
import com.knowledgegame.core.domain.model.entity.CardTemplate;
import com.knowledgegame.core.domain.model.vo.FileRef;
import com.knowledgegame.core.domain.model.vo.PageResult;
import com.knowledgegame.core.domain.model.vo.SortField;
import com.knowledgegame.core.domain.port.outbound.CardTemplateRepositoryPort;
import com.knowledgegame.core.domain.port.outbound.IpSeriesRepositoryPort;
import com.knowledgegame.core.domain.service.CardTemplateDomainService;
import com.knowledgegame.core.domain.service.IpSeriesDomainService;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 卡牌模板管理端应用服务（流程编排 + 事务，返回 DTO）
 */
@Service
public class CardTemplateAppService {

    private final CardTemplateDomainService cardTemplateDomainService;
    private final CardTemplateRepositoryPort cardTemplateRepositoryPort;
    private final IpSeriesRepositoryPort ipSeriesRepositoryPort;
    private final IpSeriesDomainService ipSeriesDomainService;
    private final FileServiceClient fileServiceClient;


    public CardTemplateAppService(CardTemplateDomainService cardTemplateDomainService,
                                  CardTemplateRepositoryPort cardTemplateRepositoryPort,
                                  IpSeriesRepositoryPort ipSeriesRepositoryPort,
                                  IpSeriesDomainService ipSeriesDomainService,
                                  FileServiceClient fileServiceClient) {
        this.cardTemplateDomainService = cardTemplateDomainService;
        this.cardTemplateRepositoryPort = cardTemplateRepositoryPort;
        this.ipSeriesRepositoryPort = ipSeriesRepositoryPort;
        this.ipSeriesDomainService = ipSeriesDomainService;
        this.fileServiceClient = fileServiceClient;
    }

    /**
     * 创建卡牌模板
     */
    @Transactional
    public CardTemplateResponse createCardTemplate(Long ipSeriesId, String code, String name,
                                                   CardRarity rarity, String description,
                                                   CardTemplateStatus status, Long imageFileId) {
        // 编码在同一 IP 系列下唯一
        cardTemplateRepositoryPort.findByIpSeriesIdAndCode(ipSeriesId, code).ifPresent(existing -> {
            throw new BusinessException("卡牌编码已存在: " + code);
        });
        // 领域服务校验 IpSeries + 创建聚合根
        FileRef image = verifyFileRef(imageFileId, "CARD_TEMPLATE");
        CardTemplate template = cardTemplateDomainService.validateAndCreate(
                ipSeriesId, code, name, rarity, description, status, image);
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
    public PageResult<CardTemplateListResponse> listCardTemplates(String name, String code, Long ipSeriesId,
                                                                   String rarity, String status,
                                                                   String sort, String order,
                                                                   int pageNumber, int pageSize) {
        CardRarity rarityEnum = EnumUtils.valueOfNullable(CardRarity.class, rarity);
        CardTemplateStatus statusEnum = EnumUtils.valueOfNullable(CardTemplateStatus.class, status);
        SortField sortField = SortField.parse(sort, order);
        PageResult<CardTemplate> domainPage = cardTemplateRepositoryPort.findByConditions(
                name, code, ipSeriesId, rarityEnum, statusEnum, sortField, pageNumber, pageSize);
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
                                                   CardTemplateStatus status, Long imageFileId) {
        CardTemplate template = cardTemplateRepositoryPort.findById(id)
                .orElseThrow(() -> new BusinessException("卡牌模板不存在: " + id));
        // 编码在同一 IP 系列下唯一（排除自身）
        if (code != null && !code.equals(template.getCode())) {
            cardTemplateRepositoryPort.findByIpSeriesIdAndCode(template.getIpSeriesId(), code)
                    .ifPresent(existing -> {
                        throw new BusinessException("卡牌编码已存在: " + code);
                    });
        }
        FileRef image = verifyFileRef(imageFileId, "CARD_TEMPLATE");
        template.update(code, name, rarity, description, status, image);
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

    private String resolveIpSeriesName(Long ipSeriesId) {
        return ipSeriesRepositoryPort.findById(ipSeriesId)
                .map(ip -> ip.getName())
                .orElse("未知");
    }

    /**
     * 批量启用卡牌模板
     */
    @Transactional
    public void batchActivate(List<Long> ids) {
        List<Long> distinctIds = ids.stream().distinct().toList();
        List<CardTemplate> cards = cardTemplateRepositoryPort.findAllByIdIn(distinctIds);
        if (cards.size() != distinctIds.size()) {
            throw new BusinessException("部分卡牌 ID 不存在");
        }
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
        List<Long> distinctIds = ids.stream().distinct().toList();
        List<CardTemplate> cards = cardTemplateRepositoryPort.findAllByIdIn(distinctIds);
        if (cards.size() != distinctIds.size()) {
            throw new BusinessException("部分卡牌 ID 不存在");
        }
        cardTemplateRepositoryPort.batchUpdateStatus(distinctIds, CardTemplateStatus.INACTIVE);
    }

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
