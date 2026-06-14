package com.knowledgegame.admin.application.service;

import com.knowledgegame.admin.api.dto.response.CardTemplateListResponse;
import com.knowledgegame.admin.api.dto.response.CardTemplateResponse;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.domainenum.CardRarity;
import com.knowledgegame.core.domain.model.domainenum.CardTemplateStatus;
import com.knowledgegame.core.domain.model.domainenum.IpSeriesStatus;
import com.knowledgegame.core.domain.model.entity.CardTemplate;
import com.knowledgegame.core.domain.model.entity.IpSeries;
import com.knowledgegame.core.domain.model.vo.PageResult;
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

/**
 * CardTemplateAppService 单元测试
 * <p>
 * 使用 Mockito mock 三个依赖，验证应用服务的业务编排逻辑。
 * REQ-92 简化后：imageUrl 替代 starImages，移除 addOrUpdateStarImage。
 */
@ExtendWith(MockitoExtension.class)
class CardTemplateAppServiceTest {

    @Mock
    private CardTemplateDomainService cardTemplateDomainService;

    @Mock
    private CardTemplateRepositoryPort cardTemplateRepositoryPort;

    @Mock
    private IpSeriesRepositoryPort ipSeriesRepositoryPort;

    @InjectMocks
    private CardTemplateAppService cardTemplateAppService;

    /**
     * 构建测试用的 CardTemplate 领域对象
     */
    private CardTemplate buildCardTemplate(Long id, String code, String name, CardRarity rarity,
                                            CardTemplateStatus status) {
        return CardTemplate.reconstruct(id, 1L, code, name, rarity, "测试描述",
                status, "https://example.com/card.png",
                LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                LocalDateTime.of(2026, 1, 1, 0, 0, 0));
    }

    /**
     * 构建测试用的 CardTemplate（含自定义 imageUrl）
     */
    private CardTemplate buildCardTemplateWithImage(Long id, String code, String name, CardRarity rarity,
                                                     CardTemplateStatus status, String imageUrl) {
        return CardTemplate.reconstruct(id, 1L, code, name, rarity, "测试描述",
                status, imageUrl,
                LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                LocalDateTime.of(2026, 1, 1, 0, 0, 0));
    }

    /**
     * 构建测试用的 IpSeries 领域对象
     */
    private IpSeries buildIpSeries(Long id, String name) {
        return IpSeries.reconstruct(id, "NARUTO", name, "描述",
                "https://example.com/cover.jpg", IpSeriesStatus.ACTIVE,
                LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                LocalDateTime.of(2026, 1, 1, 0, 0, 0));
    }

    // ========== 创建卡牌模板测试 ==========

    /**
     * 创建卡牌模板 - code 唯一性校验通过，正常创建成功（含 imageUrl）
     */
    @Test
    @DisplayName("创建卡牌模板 - 正常创建成功（含 imageUrl）")
    void createCardTemplate_shouldSucceed_whenCodeIsUnique() {
        // 准备参数
        Long ipSeriesId = 1L;
        String code = "PIKACHU";
        String name = "皮卡丘";
        CardRarity rarity = CardRarity.SR;
        String description = "电气鼠";
        CardTemplateStatus status = CardTemplateStatus.ACTIVE;
        String imageUrl = "https://example.com/card.png";

        // 编码在当前 IP 系列下不存在，返回 empty
        when(cardTemplateRepositoryPort.findByIpSeriesIdAndCode(ipSeriesId, code))
                .thenReturn(Optional.empty());
        // 领域服务返回新创建的 CardTemplate（无 id）
        CardTemplate newTemplate = buildCardTemplateWithImage(null, code, name, rarity, status, imageUrl);
        when(cardTemplateDomainService.validateAndCreate(
                eq(ipSeriesId), eq(code), eq(name), eq(rarity), eq(description), eq(status), eq(imageUrl)))
                .thenReturn(newTemplate);
        // save 返回带 id 的领域对象
        CardTemplate saved = buildCardTemplateWithImage(1L, code, name, rarity, status, imageUrl);
        when(cardTemplateRepositoryPort.save(any(CardTemplate.class))).thenReturn(saved);
        // 查询 IpSeries 名称
        when(ipSeriesRepositoryPort.findById(ipSeriesId)).thenReturn(Optional.of(buildIpSeries(1L, "火影忍者")));

        // 执行
        CardTemplateResponse result = cardTemplateAppService.createCardTemplate(
                ipSeriesId, code, name, rarity, description, status, imageUrl);

        // 验证返回的 DTO 字段
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(code, result.getCode());
        assertEquals(name, result.getName());
        assertEquals("SR", result.getRarity());
        assertEquals("ACTIVE", result.getStatus());
        assertEquals("火影忍者", result.getIpSeriesName());
        assertEquals(imageUrl, result.getImageUrl());
        verify(cardTemplateRepositoryPort).findByIpSeriesIdAndCode(ipSeriesId, code);
        verify(cardTemplateDomainService).validateAndCreate(
                eq(ipSeriesId), eq(code), eq(name), eq(rarity), eq(description), eq(status), eq(imageUrl));
        verify(cardTemplateRepositoryPort).save(any(CardTemplate.class));
    }

    /**
     * 创建卡牌模板 - code 重复抛 BusinessException
     */
    @Test
    @DisplayName("创建卡牌模板 - code 重复抛异常")
    void createCardTemplate_shouldThrow_whenCodeDuplicate() {
        // 准备参数
        Long ipSeriesId = 1L;
        String code = "PIKACHU";
        String name = "皮卡丘";
        CardRarity rarity = CardRarity.SR;
        String description = "电气鼠";
        CardTemplateStatus status = CardTemplateStatus.ACTIVE;
        String imageUrl = "https://example.com/card.png";

        // 模拟同一 IP 系列下编码已存在
        CardTemplate existing = buildCardTemplate(2L, code, "其他卡牌", rarity, status);
        when(cardTemplateRepositoryPort.findByIpSeriesIdAndCode(ipSeriesId, code))
                .thenReturn(Optional.of(existing));

        // 执行并验证异常
        BusinessException exception = assertThrows(BusinessException.class,
                () -> cardTemplateAppService.createCardTemplate(
                        ipSeriesId, code, name, rarity, description, status, imageUrl));
        assertEquals("卡牌编码已存在: " + code, exception.getMessage());
    }

    // ========== 查询详情测试 ==========

    /**
     * 根据 ID 查询 - 存在时返回 DTO（含 ipSeriesName 和 imageUrl）
     */
    @Test
    @DisplayName("查询卡牌模板详情 - 存在时返回 DTO（含 imageUrl）")
    void getCardTemplateById_shouldReturn_whenExists() {
        // 准备数据
        Long id = 1L;
        CardTemplate template = buildCardTemplate(id, "PIKACHU", "皮卡丘", CardRarity.SR,
                CardTemplateStatus.ACTIVE);
        when(cardTemplateRepositoryPort.findById(id)).thenReturn(Optional.of(template));
        when(ipSeriesRepositoryPort.findById(1L)).thenReturn(Optional.of(buildIpSeries(1L, "火影忍者")));

        // 执行
        CardTemplateResponse result = cardTemplateAppService.getCardTemplateById(id);

        // 验证
        assertNotNull(result);
        assertEquals(id, result.getId());
        assertEquals("PIKACHU", result.getCode());
        assertEquals("皮卡丘", result.getName());
        assertNotNull(result.getIpSeriesName());
        assertEquals("火影忍者", result.getIpSeriesName());
        assertEquals("https://example.com/card.png", result.getImageUrl());
        verify(cardTemplateRepositoryPort).findById(id);
    }

    /**
     * 根据 ID 查询 - 不存在抛 BusinessException
     */
    @Test
    @DisplayName("查询卡牌模板详情 - 不存在抛异常")
    void getCardTemplateById_shouldThrow_whenNotFound() {
        // 准备数据
        Long id = 999L;
        when(cardTemplateRepositoryPort.findById(id)).thenReturn(Optional.empty());

        // 执行并验证异常
        BusinessException exception = assertThrows(BusinessException.class,
                () -> cardTemplateAppService.getCardTemplateById(id));
        assertEquals("卡牌模板不存在: " + id, exception.getMessage());
    }

    // ========== 分页查询测试 ==========

    /**
     * 分页查询 - 多条件分页查询返回列表 DTO
     */
    @Test
    @DisplayName("分页查询卡牌模板 - 多条件分页查询")
    void listCardTemplates_shouldReturnPagedResult() {
        // 准备数据
        String name = "皮卡";
        Long ipSeriesId = 1L;
        String rarity = "SR";
        String status = "ACTIVE";
        int pageNumber = 0;
        int pageSize = 20;

        // 构建领域分页结果
        CardTemplate template1 = buildCardTemplate(1L, "PIKACHU", "皮卡丘", CardRarity.SR,
                CardTemplateStatus.ACTIVE);
        PageResult<CardTemplate> mockPageResult = PageResult.<CardTemplate>builder()
                .content(List.of(template1))
                .totalElements(1)
                .pageNumber(0)
                .pageSize(20)
                .totalPages(1)
                .build();

        when(cardTemplateRepositoryPort.findByConditions(name, ipSeriesId, CardRarity.SR,
                CardTemplateStatus.ACTIVE, pageNumber, pageSize)).thenReturn(mockPageResult);
        when(ipSeriesRepositoryPort.findById(1L)).thenReturn(Optional.of(buildIpSeries(1L, "火影忍者")));

        // 执行
        PageResult<CardTemplateListResponse> result = cardTemplateAppService.listCardTemplates(
                name, ipSeriesId, rarity, status, pageNumber, pageSize);

        // 验证
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("PIKACHU", result.getContent().get(0).getCode());
        assertEquals("火影忍者", result.getContent().get(0).getIpSeriesName());
        assertEquals(1L, result.getTotalElements());
        assertEquals(1, result.getTotalPages());
        verify(cardTemplateRepositoryPort).findByConditions(name, ipSeriesId, CardRarity.SR,
                CardTemplateStatus.ACTIVE, pageNumber, pageSize);
    }

    // ========== 更新卡牌模板测试 ==========

    /**
     * 更新卡牌模板 - 正常更新（含 imageUrl）
     */
    @Test
    @DisplayName("更新卡牌模板 - 正常更新（含 imageUrl）")
    void updateCardTemplate_shouldSucceed_whenValidRequest() {
        // 准备数据
        Long id = 1L;
        String newCode = "PIKACHU";
        String newName = "皮卡丘-进化";
        CardRarity newRarity = CardRarity.SSR;
        String newDescription = "进化后的电气鼠";
        CardTemplateStatus newStatus = CardTemplateStatus.ACTIVE;
        String newImageUrl = "https://example.com/evo.png";

        CardTemplate existing = buildCardTemplate(id, "PIKACHU", "皮卡丘", CardRarity.SR,
                CardTemplateStatus.ACTIVE);
        when(cardTemplateRepositoryPort.findById(id)).thenReturn(Optional.of(existing));

        CardTemplate saved = buildCardTemplateWithImage(id, newCode, newName, newRarity, newStatus, newImageUrl);
        when(cardTemplateRepositoryPort.save(any(CardTemplate.class))).thenReturn(saved);
        when(ipSeriesRepositoryPort.findById(1L)).thenReturn(Optional.of(buildIpSeries(1L, "火影忍者")));

        // 执行
        CardTemplateResponse result = cardTemplateAppService.updateCardTemplate(
                id, newCode, newName, newRarity, newDescription, newStatus, newImageUrl);

        // 验证
        assertNotNull(result);
        assertEquals(id, result.getId());
        assertEquals(newImageUrl, result.getImageUrl());
        verify(cardTemplateRepositoryPort).findById(id);
        verify(cardTemplateRepositoryPort).save(any(CardTemplate.class));
    }

    /**
     * 更新卡牌模板 - code 重复（排除自身）抛异常
     */
    @Test
    @DisplayName("更新卡牌模板 - code 重复（排除自身）抛异常")
    void updateCardTemplate_shouldThrow_whenCodeDuplicateExcludingSelf() {
        // 准备数据：修改 code 为一个已被其他记录占用的值
        Long id = 1L;
        String newCode = "NEW_CODE";

        CardTemplate existing = buildCardTemplate(id, "OLD_CODE", "皮卡丘", CardRarity.SR,
                CardTemplateStatus.ACTIVE);
        when(cardTemplateRepositoryPort.findById(id)).thenReturn(Optional.of(existing));

        // 模拟同一 IP 系列下 newCode 已被其他记录占用
        CardTemplate conflict = buildCardTemplate(2L, newCode, "其他卡牌", CardRarity.SR,
                CardTemplateStatus.ACTIVE);
        when(cardTemplateRepositoryPort.findByIpSeriesIdAndCode(existing.getIpSeriesId(), newCode))
                .thenReturn(Optional.of(conflict));

        // 执行并验证异常
        BusinessException exception = assertThrows(BusinessException.class,
                () -> cardTemplateAppService.updateCardTemplate(
                        id, newCode, "皮卡丘", CardRarity.SR, "描述",
                        CardTemplateStatus.ACTIVE, null));
        assertEquals("卡牌编码已存在: " + newCode, exception.getMessage());
    }

    /**
     * 更新卡牌模板 - 模板不存在抛异常
     */
    @Test
    @DisplayName("更新卡牌模板 - 模板不存在抛异常")
    void updateCardTemplate_shouldThrow_whenNotFound() {
        // 准备数据
        Long id = 999L;
        when(cardTemplateRepositoryPort.findById(id)).thenReturn(Optional.empty());

        // 执行并验证异常
        BusinessException exception = assertThrows(BusinessException.class,
                () -> cardTemplateAppService.updateCardTemplate(
                        id, "CODE", "名称", CardRarity.N, "描述",
                        CardTemplateStatus.ACTIVE, null));
        assertEquals("卡牌模板不存在: " + id, exception.getMessage());
    }

    // ========== 软删除测试 ==========

    /**
     * 软删除 - status 变为 INACTIVE
     */
    @Test
    @DisplayName("软删除卡牌模板 - status 变为 INACTIVE")
    void deleteCardTemplate_shouldDeactivate() {
        // 准备数据
        Long id = 1L;
        CardTemplate existing = buildCardTemplate(id, "PIKACHU", "皮卡丘", CardRarity.SR,
                CardTemplateStatus.ACTIVE);
        when(cardTemplateRepositoryPort.findById(id)).thenReturn(Optional.of(existing));
        when(cardTemplateRepositoryPort.save(any(CardTemplate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // 执行
        cardTemplateAppService.deleteCardTemplate(id);

        // 验证 save 被调用，且传入的对象 status 已变为 INACTIVE
        verify(cardTemplateRepositoryPort).save(argThat(template ->
                template.getStatus() == CardTemplateStatus.INACTIVE
        ));
    }
}
