package com.knowledgegame.core.domain.service;

import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.domainenum.CardRarity;
import com.knowledgegame.core.domain.model.domainenum.CardTemplateStatus;
import com.knowledgegame.core.domain.model.domainenum.IpSeriesStatus;
import com.knowledgegame.core.domain.model.entity.CardTemplate;
import com.knowledgegame.core.domain.model.entity.IpSeries;
import com.knowledgegame.core.domain.model.vo.FileRef;
import com.knowledgegame.core.domain.port.outbound.CardTemplateRepositoryPort;
import com.knowledgegame.core.domain.port.outbound.IpSeriesRepositoryPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * IpSeriesDomainService 领域服务单元测试
 * <p>
 * 覆盖 validateDeactivatable、validateCardActivatable、validateCardsActivatable 的正常/异常路径
 */
@ExtendWith(MockitoExtension.class)
class IpSeriesDomainServiceTest {

    @Mock
    private CardTemplateRepositoryPort cardTemplateRepositoryPort;

    @Mock
    private IpSeriesRepositoryPort ipSeriesRepositoryPort;

    @InjectMocks
    private IpSeriesDomainService ipSeriesDomainService;

    // ======================== validateDeactivatable ========================

    /**
     * validateDeactivatable：IP 系列下无 ACTIVE 卡牌时通过
     */
    @Test
    @DisplayName("validateDeactivatable 无 ACTIVE 卡牌时应正常通过")
    void validateDeactivatable_shouldPassWhenNoActiveCards() {
        when(cardTemplateRepositoryPort.countActiveByIpSeriesId(10L)).thenReturn(0L);

        assertDoesNotThrow(() -> ipSeriesDomainService.validateDeactivatable(10L));
    }

    /**
     * validateDeactivatable：IP 系列下存在 ACTIVE 卡牌时抛异常，消息含数量
     */
    @Test
    @DisplayName("validateDeactivatable 存在 ACTIVE 卡牌时应抛异常（含数量）")
    void validateDeactivatable_shouldThrowWhenHasActiveCards() {
        when(cardTemplateRepositoryPort.countActiveByIpSeriesId(10L)).thenReturn(5L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> ipSeriesDomainService.validateDeactivatable(10L));
        assertEquals("IP 系列存在 5 张 ACTIVE 卡牌，无法停用", ex.getMessage());
    }

    // ======================== validateCardActivatable ========================

    /**
     * validateCardActivatable：关联 IP 系列为 ACTIVE 时通过
     */
    @Test
    @DisplayName("validateCardActivatable 关联 IP 系列为 ACTIVE 时应正常通过")
    void validateCardActivatable_shouldPassWhenIpSeriesIsActive() {
        CardTemplate card = buildCardTemplate(1L, 10L, "测试卡牌", CardTemplateStatus.INACTIVE);
        IpSeries activeIp = buildIpSeries(10L, "漫威宇宙", IpSeriesStatus.ACTIVE);
        when(ipSeriesRepositoryPort.findById(10L)).thenReturn(Optional.of(activeIp));

        assertDoesNotThrow(() -> ipSeriesDomainService.validateCardActivatable(card));
    }

    /**
     * validateCardActivatable：关联 IP 系列为 INACTIVE 时抛异常，消息含卡牌名 + IP 名
     */
    @Test
    @DisplayName("validateCardActivatable 关联 IP 系列为 INACTIVE 时应抛异常（含名称）")
    void validateCardActivatable_shouldThrowWhenIpSeriesIsInactive() {
        CardTemplate card = buildCardTemplate(1L, 10L, "钢铁侠卡牌", CardTemplateStatus.INACTIVE);
        IpSeries inactiveIp = buildIpSeries(10L, "漫威宇宙", IpSeriesStatus.INACTIVE);
        when(ipSeriesRepositoryPort.findById(10L)).thenReturn(Optional.of(inactiveIp));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> ipSeriesDomainService.validateCardActivatable(card));
        assertEquals("卡牌《钢铁侠卡牌》关联的 IP 系列《漫威宇宙》处于停用状态，请先启用 IP 系列再启用卡牌",
                ex.getMessage());
    }

    /**
     * validateCardActivatable：关联 IP 系列不存在时抛异常
     */
    @Test
    @DisplayName("validateCardActivatable 关联 IP 系列不存在时应抛异常")
    void validateCardActivatable_shouldThrowWhenIpSeriesNotFound() {
        CardTemplate card = buildCardTemplate(1L, 999L, "孤儿卡牌", CardTemplateStatus.INACTIVE);
        when(ipSeriesRepositoryPort.findById(999L)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> ipSeriesDomainService.validateCardActivatable(card));
        assertEquals("卡牌关联的 IP 系列不存在: 999", ex.getMessage());
    }

    // ======================== validateCardsActivatable ========================

    /**
     * validateCardsActivatable：全部通过时正常
     */
    @Test
    @DisplayName("validateCardsActivatable 所有卡牌关联 IP 均为 ACTIVE 时应正常通过")
    void validateCardsActivatable_shouldPassWhenAllIpSeriesActive() {
        CardTemplate card1 = buildCardTemplate(1L, 10L, "卡牌A", CardTemplateStatus.INACTIVE);
        CardTemplate card2 = buildCardTemplate(2L, 20L, "卡牌B", CardTemplateStatus.INACTIVE);
        IpSeries ip1 = buildIpSeries(10L, "漫威", IpSeriesStatus.ACTIVE);
        IpSeries ip2 = buildIpSeries(20L, "DC", IpSeriesStatus.ACTIVE);
        when(ipSeriesRepositoryPort.findAllByIdIn(List.of(10L, 20L))).thenReturn(List.of(ip1, ip2));

        assertDoesNotThrow(() ->
                ipSeriesDomainService.validateCardsActivatable(List.of(card1, card2)));
    }

    /**
     * validateCardsActivatable：首个失败即抛异常（含其 IP 名）
     */
    @Test
    @DisplayName("validateCardsActivatable 首个失败即抛异常（含 IP 名称）")
    void validateCardsActivatable_shouldThrowOnFirstFailure() {
        CardTemplate card1 = buildCardTemplate(1L, 10L, "卡牌A", CardTemplateStatus.INACTIVE);
        CardTemplate card2 = buildCardTemplate(2L, 20L, "卡牌B", CardTemplateStatus.INACTIVE);
        IpSeries inactiveIp = buildIpSeries(10L, "漫威宇宙", IpSeriesStatus.INACTIVE);
        when(ipSeriesRepositoryPort.findAllByIdIn(List.of(10L, 20L))).thenReturn(List.of(inactiveIp));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> ipSeriesDomainService.validateCardsActivatable(List.of(card1, card2)));
        assertEquals("卡牌《卡牌A》关联的 IP 系列《漫威宇宙》处于停用状态，请先启用 IP 系列再启用卡牌",
                ex.getMessage());
    }

    /**
     * validateCardsActivatable：IP 系列不存在时消息含 ID
     */
    @Test
    @DisplayName("validateCardsActivatable 批量中某 IP 系列不存在时应抛异常")
    void validateCardsActivatable_shouldThrowWhenIpSeriesNotFoundInBatch() {
        CardTemplate card = buildCardTemplate(1L, 999L, "孤儿卡牌", CardTemplateStatus.INACTIVE);
        // findAllByIdIn 查不到 999，返回空列表
        when(ipSeriesRepositoryPort.findAllByIdIn(List.of(999L))).thenReturn(List.of());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> ipSeriesDomainService.validateCardsActivatable(List.of(card)));
        assertEquals("卡牌《孤儿卡牌》关联的 IP 系列不存在（ID=999），请检查数据完整性",
                ex.getMessage());
    }

    // ======================== 辅助方法 ========================

    private CardTemplate buildCardTemplate(Long id, Long ipSeriesId, String name, CardTemplateStatus status) {
        return CardTemplate.reconstruct(id, ipSeriesId, "CODE", name,
                CardRarity.N, "描述", status, null,
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 1, 1, 0, 0));
    }

    private IpSeries buildIpSeries(Long id, String name, IpSeriesStatus status) {
        return IpSeries.reconstruct(id, "CODE", name, "描述",
                null, status,
                LocalDateTime.of(2025, 1, 1, 0, 0),
                LocalDateTime.of(2025, 1, 1, 0, 0));
    }
}
