package com.knowledgegame.admin.application.service;

import com.knowledgegame.admin.api.dto.response.CardTemplateListResponse;
import com.knowledgegame.admin.api.dto.response.CardTemplateResponse;
import com.knowledgegame.components.feign.client.FileServiceClient;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.domainenum.CardRarity;
import com.knowledgegame.core.domain.model.domainenum.CardTemplateStatus;
import com.knowledgegame.core.domain.model.domainenum.IpSeriesStatus;
import com.knowledgegame.core.domain.model.entity.CardTemplate;
import com.knowledgegame.core.domain.model.entity.IpSeries;
import com.knowledgegame.core.domain.model.vo.FileRef;
import com.knowledgegame.core.domain.model.vo.PageResult;
import com.knowledgegame.core.domain.model.vo.SortField;
import com.knowledgegame.core.domain.port.outbound.CardTemplateRepositoryPort;
import com.knowledgegame.core.domain.port.outbound.IpSeriesRepositoryPort;
import com.knowledgegame.core.domain.service.CardTemplateDomainService;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardTemplateAppServiceTest {

    @Mock private CardTemplateDomainService cardTemplateDomainService;
    @Mock private CardTemplateRepositoryPort cardTemplateRepositoryPort;
    @Mock private IpSeriesRepositoryPort ipSeriesRepositoryPort;
    @Mock private FileServiceClient fileServiceClient;

    @InjectMocks
    private CardTemplateAppService cardTemplateAppService;

    private CardTemplate buildCardTemplate(Long id, String code, String name, CardRarity rarity,
                                            CardTemplateStatus status) {
        return CardTemplate.reconstruct(id, 1L, code, name, rarity, "测试描述",
                status, null,
                LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                LocalDateTime.of(2026, 1, 1, 0, 0, 0));
    }

    private IpSeries buildIpSeries(Long id, String name) {
        return IpSeries.reconstruct(id, "NARUTO", name, "描述",
                FileRef.of(1L, "https://example.com/cover.jpg"), IpSeriesStatus.ACTIVE,
                LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                LocalDateTime.of(2026, 1, 1, 0, 0, 0));
    }

    @Test
    @DisplayName("创建卡牌模板 - 正常创建成功")
    void createCardTemplate_shouldSucceed_whenCodeIsUnique() {
        Long ipSeriesId = 1L;
        String code = "PIKACHU", name = "皮卡丘", description = "电气鼠";
        CardRarity rarity = CardRarity.SR;
        CardTemplateStatus status = CardTemplateStatus.ACTIVE;

        when(cardTemplateRepositoryPort.findByIpSeriesIdAndCode(ipSeriesId, code)).thenReturn(Optional.empty());
        CardTemplate newTemplate = buildCardTemplate(null, code, name, rarity, status);
        when(cardTemplateDomainService.validateAndCreate(eq(ipSeriesId), eq(code), eq(name),
                eq(rarity), eq(description), eq(status), any())).thenReturn(newTemplate);
        CardTemplate saved = buildCardTemplate(1L, code, name, rarity, status);
        when(cardTemplateRepositoryPort.save(any())).thenReturn(saved);
        when(ipSeriesRepositoryPort.findById(ipSeriesId)).thenReturn(Optional.of(buildIpSeries(1L, "火影忍者")));

        CardTemplateResponse result = cardTemplateAppService.createCardTemplate(
                ipSeriesId, code, name, rarity, description, status, null);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(code, result.getCode());
        assertEquals("SR", result.getRarity());
        assertEquals("ACTIVE", result.getStatus());
        assertEquals("火影忍者", result.getIpSeriesName());
    }

    @Test
    @DisplayName("创建卡牌模板 - code 重复抛异常")
    void createCardTemplate_shouldThrow_whenCodeDuplicate() {
        CardTemplate existing = buildCardTemplate(2L, "PIKACHU", "其他卡牌", CardRarity.SR, CardTemplateStatus.ACTIVE);
        when(cardTemplateRepositoryPort.findByIpSeriesIdAndCode(1L, "PIKACHU")).thenReturn(Optional.of(existing));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> cardTemplateAppService.createCardTemplate(1L, "PIKACHU", "皮卡丘",
                        CardRarity.SR, "电气鼠", CardTemplateStatus.ACTIVE, null));
        assertEquals("卡牌编码已存在: PIKACHU", exception.getMessage());
    }

    @Test
    @DisplayName("查询卡牌模板详情 - 存在时返回 DTO")
    void getCardTemplateById_shouldReturn_whenExists() {
        Long id = 1L;
        CardTemplate template = buildCardTemplate(id, "PIKACHU", "皮卡丘", CardRarity.SR, CardTemplateStatus.ACTIVE);
        when(cardTemplateRepositoryPort.findById(id)).thenReturn(Optional.of(template));
        when(ipSeriesRepositoryPort.findById(1L)).thenReturn(Optional.of(buildIpSeries(1L, "火影忍者")));

        CardTemplateResponse result = cardTemplateAppService.getCardTemplateById(id);

        assertNotNull(result);
        assertEquals(id, result.getId());
        assertEquals("火影忍者", result.getIpSeriesName());
    }

    @Test
    @DisplayName("查询卡牌模板详情 - 不存在抛异常")
    void getCardTemplateById_shouldThrow_whenNotFound() {
        when(cardTemplateRepositoryPort.findById(999L)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> cardTemplateAppService.getCardTemplateById(999L));
        assertEquals("卡牌模板不存在: 999", exception.getMessage());
    }

    @Test
    @DisplayName("分页查询卡牌模板列表")
    void listCardTemplates_shouldReturnPagedResult() {
        CardTemplate t1 = buildCardTemplate(1L, "PIKACHU", "皮卡丘", CardRarity.SR, CardTemplateStatus.ACTIVE);
        CardTemplate t2 = buildCardTemplate(2L, "CHARIZARD", "喷火龙", CardRarity.SSR, CardTemplateStatus.ACTIVE);
        PageResult<CardTemplate> mockPage = PageResult.<CardTemplate>builder()
                .content(List.of(t1, t2)).totalElements(2).pageNumber(0).pageSize(20).totalPages(1).build();
        when(cardTemplateRepositoryPort.findByConditions(null, null, null, null, null, null, 0, 20)).thenReturn(mockPage);
        when(ipSeriesRepositoryPort.findById(1L)).thenReturn(Optional.of(buildIpSeries(1L, "火影忍者")));

        PageResult<CardTemplateListResponse> result = cardTemplateAppService.listCardTemplates(
                null, null, null, null, null, null, null, 0, 20);

        assertNotNull(result);
        assertEquals(2, result.getContent().size());
    }

    @Test
    @DisplayName("分页查询 - sort/order 经 SortField.parse 透传到 Port")
    void listCardTemplates_shouldPassSortFieldToPort() {
        CardTemplate t1 = buildCardTemplate(1L, "PIKACHU", "皮卡丘", CardRarity.SR, CardTemplateStatus.ACTIVE);
        PageResult<CardTemplate> mockPage = PageResult.<CardTemplate>builder()
                .content(List.of(t1)).totalElements(1).pageNumber(0).pageSize(20).totalPages(1).build();
        when(cardTemplateRepositoryPort.findByConditions(
                eq(null), eq(null), eq(null), eq(null), eq(null),
                argThat((SortField sf) -> sf != null && sf.getField().equals("code") && sf.getDirection() == SortField.Direction.ASC),
                eq(0), eq(20))).thenReturn(mockPage);
        when(ipSeriesRepositoryPort.findById(1L)).thenReturn(Optional.of(buildIpSeries(1L, "火影忍者")));

        PageResult<CardTemplateListResponse> result = cardTemplateAppService.listCardTemplates(
                null, null, null, null, null, "code", "asc", 0, 20);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
    }

    @Test
    @DisplayName("更新卡牌模板 - 正常更新")
    void updateCardTemplate_shouldSucceed() {
        Long id = 1L;
        CardTemplate existing = buildCardTemplate(id, "PIKACHU", "皮卡丘", CardRarity.SR, CardTemplateStatus.ACTIVE);
        when(cardTemplateRepositoryPort.findById(id)).thenReturn(Optional.of(existing));
        CardTemplate saved = buildCardTemplate(id, "PIKACHU", "皮卡丘-改", CardRarity.SSR, CardTemplateStatus.ACTIVE);
        when(cardTemplateRepositoryPort.save(any())).thenReturn(saved);
        when(ipSeriesRepositoryPort.findById(1L)).thenReturn(Optional.of(buildIpSeries(1L, "火影忍者")));

        CardTemplateResponse result = cardTemplateAppService.updateCardTemplate(
                id, null, "皮卡丘-改", CardRarity.SSR, null, CardTemplateStatus.ACTIVE, null);

        assertNotNull(result);
        assertEquals("皮卡丘-改", result.getName());
        assertEquals("SSR", result.getRarity());
    }

    @Test
    @DisplayName("软删除卡牌模板")
    void deleteCardTemplate_shouldDeactivate() {
        Long id = 1L;
        CardTemplate existing = buildCardTemplate(id, "PIKACHU", "皮卡丘", CardRarity.SR, CardTemplateStatus.ACTIVE);
        when(cardTemplateRepositoryPort.findById(id)).thenReturn(Optional.of(existing));
        when(cardTemplateRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        cardTemplateAppService.deleteCardTemplate(id);

        verify(cardTemplateRepositoryPort).save(argThat(t -> t.getStatus() == CardTemplateStatus.INACTIVE));
    }
}
