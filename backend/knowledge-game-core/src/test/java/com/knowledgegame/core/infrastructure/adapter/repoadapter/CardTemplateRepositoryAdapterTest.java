package com.knowledgegame.core.infrastructure.adapter.repoadapter;

import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.domainenum.CardRarity;
import com.knowledgegame.core.domain.model.domainenum.CardTemplateStatus;
import com.knowledgegame.core.domain.model.entity.CardTemplate;
import com.knowledgegame.core.domain.model.vo.SortField;
import com.knowledgegame.core.infrastructure.db.converter.CardTemplateConverter;
import com.knowledgegame.core.infrastructure.db.entity.CardTemplatePO;
import com.knowledgegame.core.infrastructure.db.repository.CardTemplateJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    // --- findByConditions 排序白名单（REQ-86 ISSUE-4） ---
    //
    // Mock 策略：用 ArgumentCaptor<PageRequest> 捕获 adapter 内部调用
    // cardTemplateJpaRepository.findAll(spec, pageRequest) 时传入的 PageRequest，
    // 断言 captor.getValue().getSort() 的字段名与方向。

    /**
     * sort=null 走默认 createdAt DESC
     */
    @Test
    @DisplayName("findByConditions sort=null 时使用默认 createdAt DESC")
    void findByConditions_sortNull_fallbackToCreatedAtDesc() {
        when(cardTemplateJpaRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(emptyPage());

        adapter.findByConditions(null, null, null, null, null, null, 0, 20);

        PageRequest captured = capturePageRequest();
        Sort.Order order = captured.getSort().getOrderFor("createdAt");
        assertEquals(Sort.Direction.DESC, order.getDirection());
    }

    /**
     * sort=code&order=asc 走 ASC
     */
    @Test
    @DisplayName("findByConditions sort=code&order=asc 时使用 ASC")
    void findByConditions_sortCodeAsc_returnsCodeAscSort() {
        when(cardTemplateJpaRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(emptyPage());

        adapter.findByConditions(null, null, null, null, null,
                new SortField("code", SortField.Direction.ASC), 0, 20);

        PageRequest captured = capturePageRequest();
        Sort.Order order = captured.getSort().getOrderFor("code");
        assertEquals(Sort.Direction.ASC, order.getDirection());
    }

    /**
     * sort=rarity&order=desc 走 rarity DESC
     */
    @Test
    @DisplayName("findByConditions sort=rarity&order=desc 时使用 rarity DESC")
    void findByConditions_sortRarityDesc_returnsRarityDescSort() {
        when(cardTemplateJpaRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(emptyPage());

        adapter.findByConditions(null, null, null, null, null,
                new SortField("rarity", SortField.Direction.DESC), 0, 20);

        PageRequest captured = capturePageRequest();
        Sort.Order order = captured.getSort().getOrderFor("rarity");
        assertEquals(Sort.Direction.DESC, order.getDirection());
    }

    /**
     * sort=foo 非法字段抛 BusinessException(400)，不再静默回退
     */
    @Test
    @DisplayName("findByConditions sort=foo 时抛 BusinessException(400)")
    void findByConditions_sortInvalid_throws400() {
        SortField invalid = new SortField("foo", SortField.Direction.DESC);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                adapter.findByConditions(null, null, null, null, null, invalid, 0, 20));

        assertEquals(400, ex.getCode());
    }

    /**
     * 捕获 adapter 传给 findAll 的 PageRequest 参数
     */
    private PageRequest capturePageRequest() {
        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(cardTemplateJpaRepository).findAll(any(Specification.class), captor.capture());
        return captor.getValue();
    }

    /**
     * 构造空 Page，仅用于让 findAll 返回非 null 结果
     */
    @SuppressWarnings("unchecked")
    private Page<CardTemplatePO> emptyPage() {
        return (Page<CardTemplatePO>) org.mockito.Mockito.mock(Page.class);
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
