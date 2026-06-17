package com.knowledgegame.core.infrastructure.adapter.repoadapter;

import com.knowledgegame.core.domain.model.domainenum.CardRarity;
import com.knowledgegame.core.domain.model.domainenum.CardTemplateStatus;
import com.knowledgegame.core.domain.model.entity.CardTemplate;
import com.knowledgegame.core.infrastructure.db.converter.CardTemplateConverter;
import com.knowledgegame.core.infrastructure.db.entity.CardTemplatePO;
import com.knowledgegame.core.infrastructure.db.repository.CardTemplateJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CardTemplateRepositoryAdapter 单元测试
 * <p>
 * 覆盖 REQ-94 新增的三个方法：countActiveByIpSeriesId、findAllByIdIn、batchUpdateStatus
 */
@ExtendWith(MockitoExtension.class)
class CardTemplateRepositoryAdapterTest {

    @Mock
    private CardTemplateJpaRepository cardTemplateJpaRepository;

    @InjectMocks
    private CardTemplateRepositoryAdapter adapter;

    /**
     * countActiveByIpSeriesId 正常返回 ACTIVE 卡牌数量
     */
    @Test
    @DisplayName("countActiveByIpSeriesId 应返回 JPA 查询的 ACTIVE 卡牌数量")
    void countActiveByIpSeriesId_shouldReturnActiveCount() {
        when(cardTemplateJpaRepository.countActiveByIpSeriesId(10L)).thenReturn(3L);

        long count = adapter.countActiveByIpSeriesId(10L);

        assertEquals(3L, count);
        verify(cardTemplateJpaRepository).countActiveByIpSeriesId(10L);
    }

    /**
     * countActiveByIpSeriesId IP 系列下无 ACTIVE 卡牌时返回 0
     */
    @Test
    @DisplayName("countActiveByIpSeriesId IP 系列下无 ACTIVE 卡牌时应返回 0")
    void countActiveByIpSeriesId_shouldReturnZeroWhenNoActiveCards() {
        when(cardTemplateJpaRepository.countActiveByIpSeriesId(99L)).thenReturn(0L);

        long count = adapter.countActiveByIpSeriesId(99L);

        assertEquals(0L, count);
    }

    /**
     * findAllByIdIn 正常返回卡牌列表
     */
    @Test
    @DisplayName("findAllByIdIn 应返回对应 ID 的卡牌列表")
    void findAllByIdIn_shouldReturnCardList() {
        CardTemplatePO po = buildCardTemplatePO(1L, 10L, "CARD_001", CardRarity.SR, CardTemplateStatus.ACTIVE);
        when(cardTemplateJpaRepository.findAllById(anyList())).thenReturn(List.of(po));

        List<CardTemplate> result = adapter.findAllByIdIn(List.of(1L));

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals("CARD_001", result.get(0).getCode());
    }

    /**
     * findAllByIdIn ID 列表为空时返回空列表
     */
    @Test
    @DisplayName("findAllByIdIn 传入空列表应返回空列表")
    void findAllByIdIn_shouldReturnEmptyListWhenIdsEmpty() {
        when(cardTemplateJpaRepository.findAllById(anyList())).thenReturn(List.of());

        List<CardTemplate> result = adapter.findAllByIdIn(List.of());

        assertTrue(result.isEmpty());
    }

    /**
     * batchUpdateStatus 正常批量更新
     */
    @Test
    @DisplayName("batchUpdateStatus 应调用 JPA 批量更新状态")
    void batchUpdateStatus_shouldCallJpaBatchUpdate() {
        List<Long> ids = List.of(1L, 2L, 3L);

        adapter.batchUpdateStatus(ids, CardTemplateStatus.ACTIVE);

        verify(cardTemplateJpaRepository).batchUpdateStatus(ids, CardTemplateStatus.ACTIVE);
    }

    /**
     * batchUpdateStatus 空列表也应正常调用 JPA（由 DTO 层保证非空，Adapter 不校验）
     */
    @Test
    @DisplayName("batchUpdateStatus 传入空列表也应委托给 JPA")
    void batchUpdateStatus_shouldDelegateEmptyListToJpa() {
        adapter.batchUpdateStatus(List.of(), CardTemplateStatus.INACTIVE);

        verify(cardTemplateJpaRepository).batchUpdateStatus(List.of(), CardTemplateStatus.INACTIVE);
    }

    // --- 现有方法回归测试 ---

    /**
     * findById 存在时返回领域对象
     */
    @Test
    @DisplayName("findById 存在时应返回领域对象")
    void findById_shouldReturnDomainWhenExists() {
        CardTemplatePO po = buildCardTemplatePO(1L, 10L, "CARD_001", CardRarity.N, CardTemplateStatus.ACTIVE);
        when(cardTemplateJpaRepository.findById(1L)).thenReturn(Optional.of(po));

        Optional<CardTemplate> result = adapter.findById(1L);

        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getId());
    }

    /**
     * existsById 应委托给 JPA
     */
    @Test
    @DisplayName("existsById 应委托给 JPA")
    void existsById_shouldDelegateToJpa() {
        when(cardTemplateJpaRepository.existsById(1L)).thenReturn(true);

        boolean result = adapter.existsById(1L);

        assertTrue(result);
    }

    // --- 辅助方法 ---

    private CardTemplatePO buildCardTemplatePO(Long id, Long ipSeriesId, String code,
                                                CardRarity rarity, CardTemplateStatus status) {
        CardTemplatePO po = new CardTemplatePO();
        po.setId(id);
        po.setIpSeriesId(ipSeriesId);
        po.setCode(code);
        po.setName("测试卡牌");
        po.setRarity(rarity);
        po.setDescription("测试描述");
        po.setStatus(status);
        po.setImageFileId(1L);
        po.setImageUrl("https://example.com/card.png");
        po.setCreatedAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        po.setUpdatedAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        return po;
    }
}
