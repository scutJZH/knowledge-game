package com.knowledgegame.core.domain.service;

import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.domainenum.CardRarity;
import com.knowledgegame.core.domain.model.domainenum.CardTemplateStatus;
import com.knowledgegame.core.domain.model.domainenum.IpSeriesStatus;
import com.knowledgegame.core.domain.model.entity.CardTemplate;
import com.knowledgegame.core.domain.model.entity.IpSeries;
import com.knowledgegame.core.domain.model.vo.FileRef;
import com.knowledgegame.core.domain.port.outbound.IpSeriesRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * CardTemplateDomainService 领域服务单元测试
 * <p>
 * 覆盖 validateAndCreate() 的成功路径和异常路径（IpSeries 不存在、未启用）。
 * REQ-92 简化后：imageUrl 替代 starImages 列表。
 */
@ExtendWith(MockitoExtension.class)
class CardTemplateDomainServiceTest {

    @Mock
    private IpSeriesRepositoryPort ipSeriesRepositoryPort;

    @InjectMocks
    private CardTemplateDomainService cardTemplateDomainService;

    /**
     * 验证 validateAndCreate() 成功时返回正确聚合根（IpSeries 存在且 ACTIVE）
     */
    @Test
    @DisplayName("validateAndCreate() IpSeries 存在且 ACTIVE 时应返回正确聚合根")
    void validateAndCreate_shouldReturnTemplateWhenIpSeriesIsActive() {
        // 准备：构造一个 ACTIVE 的 IpSeries
        IpSeries activeIpSeries = IpSeries.reconstruct(
                10L, "MARVEL", "漫威宇宙", "超级英雄",
                FileRef.of(1L, "https://cover.jpg"), IpSeriesStatus.ACTIVE,
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 1, 1, 0, 0)
        );
        when(ipSeriesRepositoryPort.findById(10L)).thenReturn(Optional.of(activeIpSeries));

        // 执行
        CardTemplate result = cardTemplateDomainService.validateAndCreate(
                10L, "CARD_001", "测试卡牌", CardRarity.SR,
                "测试描述", CardTemplateStatus.ACTIVE, FileRef.of(1L, "https://example.com/card.png")
        );

        assertNotNull(result);
        assertNull(result.getId());
        assertEquals(10L, result.getIpSeriesId());
        assertEquals("CARD_001", result.getCode());
        assertEquals(CardRarity.SR, result.getRarity());
        assertEquals(CardTemplateStatus.ACTIVE, result.getStatus());
        assertEquals(FileRef.of(1L, "https://example.com/card.png"), result.getImage());
    }

    /**
     * 验证 validateAndCreate() 传入 null imageUrl 也能成功创建
     */
    @Test
    @DisplayName("validateAndCreate() 传入 null imageUrl 也应成功创建")
    void validateAndCreate_shouldAllowNullImageUrl() {
        // 准备
        IpSeries activeIpSeries = IpSeries.reconstruct(
                10L, "MARVEL", "漫威宇宙", "描述",
                FileRef.of(1L, "https://cover.jpg"), IpSeriesStatus.ACTIVE,
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 1, 1, 0, 0)
        );
        when(ipSeriesRepositoryPort.findById(10L)).thenReturn(Optional.of(activeIpSeries));

        // 执行
        CardTemplate result = cardTemplateDomainService.validateAndCreate(
                10L, "CODE", "名称", CardRarity.N, "描述",
                CardTemplateStatus.ACTIVE, null
        );

        // 断言
        assertNotNull(result);
        assertNull(result.getImage());
    }

    /**
     * 验证 validateAndCreate() IpSeries 不存在时抛 BusinessException
     */
    @Test
    @DisplayName("validateAndCreate() IpSeries 不存在时应抛出 BusinessException")
    void validateAndCreate_shouldThrowWhenIpSeriesNotFound() {
        // 准备：模拟 findById 返回空
        when(ipSeriesRepositoryPort.findById(999L)).thenReturn(Optional.empty());

        // 执行并断言
        BusinessException exception = assertThrows(BusinessException.class, () ->
                cardTemplateDomainService.validateAndCreate(
                        999L, "CODE", "名称", CardRarity.N,
                        "描述", CardTemplateStatus.ACTIVE, null
                ),
                "IpSeries 不存在时应抛出 BusinessException"
        );

        // 断言异常消息包含关键字
        assertNotNull(exception.getMessage(), "异常消息不应为 null");
        assertEquals(true, exception.getMessage().contains("IP 系列不存在"),
                "异常消息应包含 'IP 系列不存在'，实际为: " + exception.getMessage());
    }

    /**
     * 验证 validateAndCreate() IpSeries 为 INACTIVE 时抛 BusinessException
     */
    @Test
    @DisplayName("validateAndCreate() IpSeries 为 INACTIVE 时应抛出 BusinessException")
    void validateAndCreate_shouldThrowWhenIpSeriesIsInactive() {
        // 准备：构造一个 INACTIVE 的 IpSeries
        IpSeries inactiveIpSeries = IpSeries.reconstruct(
                20L, "DC", "DC宇宙", "超级英雄",
                FileRef.of(1L, "https://cover.jpg"), IpSeriesStatus.INACTIVE,
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 1, 1, 0, 0)
        );
        when(ipSeriesRepositoryPort.findById(20L)).thenReturn(Optional.of(inactiveIpSeries));

        // 执行并断言
        BusinessException exception = assertThrows(BusinessException.class, () ->
                cardTemplateDomainService.validateAndCreate(
                        20L, "CODE", "名称", CardRarity.R,
                        "描述", CardTemplateStatus.ACTIVE, FileRef.of(1L, "https://example.com/img.png")
                ),
                "IpSeries 为 INACTIVE 时应抛出 BusinessException"
        );

        // 断言异常消息包含关键字
        assertNotNull(exception.getMessage(), "异常消息不应为 null");
        assertEquals(true, exception.getMessage().contains("IP 系列未启用"),
                "异常消息应包含 'IP 系列未启用'，实际为: " + exception.getMessage());
    }
}
