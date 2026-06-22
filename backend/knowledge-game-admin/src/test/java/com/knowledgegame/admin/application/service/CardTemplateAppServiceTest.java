package com.knowledgegame.admin.application.service;

import com.knowledgegame.admin.api.dto.request.UpdateCardTemplateRequest;
import com.knowledgegame.admin.api.dto.response.CardTemplateImportResult;
import com.knowledgegame.admin.api.dto.response.CardTemplateListResponse;
import com.knowledgegame.admin.api.dto.response.CardTemplateResponse;
import com.knowledgegame.admin.api.dto.response.ImportFailDetail;
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
import com.knowledgegame.auth.security.SecurityUtils;
import com.knowledgegame.core.domain.service.CardTemplateDomainService;
import com.knowledgegame.core.domain.service.recyclebin.RecycleBinItemStrategy;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.MockedStatic;

@ExtendWith(MockitoExtension.class)
class CardTemplateAppServiceTest {

    @Mock private CardTemplateDomainService cardTemplateDomainService;
    @Mock private CardTemplateRepositoryPort cardTemplateRepositoryPort;
    @Mock private IpSeriesRepositoryPort ipSeriesRepositoryPort;
    @Mock private FileServiceClient fileServiceClient;
    @Mock private RecycleBinItemStrategy<CardTemplate> recycleBinStrategy;

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
    void update_shouldSucceed() {
        Long id = 1L;
        CardTemplate existing = buildCardTemplate(id, "PIKACHU", "皮卡丘", CardRarity.SR, CardTemplateStatus.ACTIVE);
        when(cardTemplateRepositoryPort.findById(id)).thenReturn(Optional.of(existing));
        when(cardTemplateRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ipSeriesRepositoryPort.findById(1L)).thenReturn(Optional.of(buildIpSeries(1L, "火影忍者")));

        UpdateCardTemplateRequest req = new UpdateCardTemplateRequest();
        req.setName("皮卡丘-改");
        req.setRarity(CardRarity.SSR);

        CardTemplateResponse result = cardTemplateAppService.update(id, req);

        assertNotNull(result);
        assertEquals("皮卡丘-改", existing.getName());
        assertEquals(CardRarity.SSR, existing.getRarity());
    }

    /**
     * 三态场景 1：可清空字段 undefined → 保持原值
     */
    @Test
    @DisplayName("更新卡牌模板 - 可清空字段 undefined 时保持原值")
    void update_shouldSkipClearableFields_whenAllUndefined() {
        Long id = 1L;
        CardTemplate existing = CardTemplate.reconstruct(id, 1L, "CODE", "皮卡丘", CardRarity.N,
                "原描述", CardTemplateStatus.ACTIVE, FileRef.of(1L, "/static/old.png"),
                LocalDateTime.now(), LocalDateTime.now());
        when(cardTemplateRepositoryPort.findById(id)).thenReturn(Optional.of(existing));
        when(cardTemplateRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ipSeriesRepositoryPort.findById(1L)).thenReturn(Optional.of(buildIpSeries(1L, "火影")));

        UpdateCardTemplateRequest req = new UpdateCardTemplateRequest();
        // 所有 JsonNullable 字段未设置

        cardTemplateAppService.update(id, req);

        assertEquals("原描述", existing.getDescription());
        assertEquals(FileRef.of(1L, "/static/old.png"), existing.getImage());
    }

    /**
     * 三态场景 2：JsonNullable.of(null) → 调 clearXxx
     */
    @Test
    @DisplayName("更新卡牌模板 - of(null) 调 clearXxx")
    void update_shouldCallClear_whenFieldsAreNull() {
        Long id = 1L;
        CardTemplate existing = CardTemplate.reconstruct(id, 1L, "CODE", "皮卡丘", CardRarity.N,
                "原描述", CardTemplateStatus.ACTIVE, FileRef.of(1L, "/static/old.png"),
                LocalDateTime.now(), LocalDateTime.now());
        when(cardTemplateRepositoryPort.findById(id)).thenReturn(Optional.of(existing));
        when(cardTemplateRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ipSeriesRepositoryPort.findById(1L)).thenReturn(Optional.of(buildIpSeries(1L, "火影")));

        UpdateCardTemplateRequest req = new UpdateCardTemplateRequest();
        req.setDescription(JsonNullable.of(null));
        req.setImageFileId(JsonNullable.of(null));

        cardTemplateAppService.update(id, req);

        assertNull(existing.getDescription());
        assertNull(existing.getImage());
    }

    /**
     * 三态场景 3：JsonNullable.of(value) String 字段 → 调 updateXxx(value)
     */
    @Test
    @DisplayName("更新卡牌模板 - of(value) String 字段调 updateXxx")
    void update_shouldCallUpdate_whenStringFieldHasValue() {
        Long id = 1L;
        CardTemplate existing = buildCardTemplate(id, "CODE", "皮卡丘", CardRarity.N, CardTemplateStatus.ACTIVE);
        when(cardTemplateRepositoryPort.findById(id)).thenReturn(Optional.of(existing));
        when(cardTemplateRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ipSeriesRepositoryPort.findById(1L)).thenReturn(Optional.of(buildIpSeries(1L, "火影")));

        UpdateCardTemplateRequest req = new UpdateCardTemplateRequest();
        req.setDescription(JsonNullable.of("新描述"));

        cardTemplateAppService.update(id, req);

        assertEquals("新描述", existing.getDescription());
    }

    @Test
    @DisplayName("删除卡牌模板 — 移入回收站")
    void deleteCardTemplate_shouldMoveToRecycleBin() {
        Long id = 1L;
        when(cardTemplateRepositoryPort.existsById(id)).thenReturn(true);

        try (MockedStatic<SecurityUtils> mockedSecurity = mockStatic(SecurityUtils.class)) {
            mockedSecurity.when(SecurityUtils::getCurrentUsername).thenReturn("admin");

            cardTemplateAppService.deleteCardTemplate(id);
        }

        verify(recycleBinStrategy).validateDeletable(id);
        verify(recycleBinStrategy).moveToRecycleBin(eq(id), any());
    }

    @Test
    @DisplayName("删除卡牌模板 — 不存在抛异常")
    void deleteCardTemplate_shouldThrow_whenNotFound() {
        Long id = 999L;
        when(cardTemplateRepositoryPort.existsById(id)).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> cardTemplateAppService.deleteCardTemplate(id));
        assertEquals("卡牌模板不存在: " + id, exception.getMessage());
    }

    // ========== 批量导入测试 ==========

    @Test
    @DisplayName("导入 - 正常导入全部成功")
    void importCardTemplates_allValid_shouldSucceed() throws IOException {
        byte[] excel = createTestExcel(new String[][]{
                {"CARD-001", "测试卡牌1", "PKM", "SR", "描述1", "启用"},
                {"CARD-002", "测试卡牌2", "PKM", "R", "", ""},
        });
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excel);

        IpSeries pkSeries = IpSeries.reconstruct(1L, "PKM", "宝可梦", "描述",
                FileRef.of(1L, "https://example.com/cover.jpg"), IpSeriesStatus.ACTIVE,
                LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                LocalDateTime.of(2026, 1, 1, 0, 0, 0));
        when(ipSeriesRepositoryPort.findAll()).thenReturn(List.of(pkSeries));
        CardTemplate mockCard = buildCardTemplate(null, "CARD-001", "测试卡牌1", CardRarity.SR, CardTemplateStatus.ACTIVE);
        when(cardTemplateDomainService.validateAndCreate(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(mockCard);
        when(cardTemplateRepositoryPort.save(any())).thenReturn(mockCard);
        when(cardTemplateRepositoryPort.findByIpSeriesIdAndCode(any(), any()))
                .thenReturn(Optional.empty());

        CardTemplateImportResult result = cardTemplateAppService.importCardTemplates(file);

        assertEquals(2, result.getTotalCount());
        assertEquals(2, result.getSuccessCount());
        assertEquals(0, result.getFailCount());
    }

    @Test
    @DisplayName("导入 - 稀有度非法返回失败明细")
    void importCardTemplates_invalidRarity_shouldFailThatRow() throws IOException {
        byte[] excel = createTestExcel(new String[][]{
                {"CARD-001", "测试", "PKM", "LEGENDARY", "", ""},
        });
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excel);

        IpSeries pkSeries = IpSeries.reconstruct(1L, "PKM", "宝可梦", "描述",
                FileRef.of(1L, "https://example.com/cover.jpg"), IpSeriesStatus.ACTIVE,
                LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                LocalDateTime.of(2026, 1, 1, 0, 0, 0));
        when(ipSeriesRepositoryPort.findAll()).thenReturn(List.of(pkSeries));

        CardTemplateImportResult result = cardTemplateAppService.importCardTemplates(file);

        assertEquals(1, result.getTotalCount());
        assertEquals(0, result.getSuccessCount());
        assertEquals(1, result.getFailCount());
        assertTrue(result.getFailDetails().get(0).getReason().contains("稀有度格式错误"));
    }

    @Test
    @DisplayName("导入 - 编码必填缺失返回失败")
    void importCardTemplates_missingCode_shouldFail() throws IOException {
        byte[] excel = createTestExcel(new String[][]{
                {"", "测试", "PKM", "SR", "", ""},
        });
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excel);

        IpSeries pkSeries = IpSeries.reconstruct(1L, "PKM", "宝可梦", "描述",
                FileRef.of(1L, "https://example.com/cover.jpg"), IpSeriesStatus.ACTIVE,
                LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                LocalDateTime.of(2026, 1, 1, 0, 0, 0));
        when(ipSeriesRepositoryPort.findAll()).thenReturn(List.of(pkSeries));

        CardTemplateImportResult result = cardTemplateAppService.importCardTemplates(file);

        assertEquals(1, result.getFailCount());
        assertTrue(result.getFailDetails().get(0).getReason().contains("编码不能为空"));
    }

    @Test
    @DisplayName("导入 - IP 系列不存在返回失败")
    void importCardTemplates_ipSeriesNotExist_shouldFail() throws IOException {
        byte[] excel = createTestExcel(new String[][]{
                {"CARD-001", "测试", "UNKNOWN", "SR", "", ""},
        });
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excel);

        // findAll 返回空列表，IP 系列不存在
        when(ipSeriesRepositoryPort.findAll()).thenReturn(List.of());

        CardTemplateImportResult result = cardTemplateAppService.importCardTemplates(file);

        assertEquals(1, result.getFailCount());
        assertTrue(result.getFailDetails().get(0).getReason().contains("IP系列编码不存在或已停用"));
    }

    @Test
    @DisplayName("导入 - 空模板返回零统计")
    void importCardTemplates_emptyTemplate_shouldReturnZero() throws IOException {
        byte[] excel = createTestExcel(new String[][]{});
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excel);

        CardTemplateImportResult result = cardTemplateAppService.importCardTemplates(file);

        assertEquals(0, result.getTotalCount());
        assertEquals(0, result.getSuccessCount());
        assertEquals(0, result.getFailCount());
    }

    @Test
    @DisplayName("导入 - 超过 200 行抛异常")
    void importCardTemplates_exceeds200_shouldThrow() throws IOException {
        String[][] data = new String[201][];
        for (int i = 0; i < 201; i++) {
            data[i] = new String[]{"CARD-" + i, "测试" + i, "PKM", "R", "", ""};
        }
        byte[] excel = createTestExcel(data);
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excel);

        IpSeries pkSeries = IpSeries.reconstruct(1L, "PKM", "宝可梦", "描述",
                FileRef.of(1L, "https://example.com/cover.jpg"), IpSeriesStatus.ACTIVE,
                LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                LocalDateTime.of(2026, 1, 1, 0, 0, 0));
        when(ipSeriesRepositoryPort.findAll()).thenReturn(List.of(pkSeries));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> cardTemplateAppService.importCardTemplates(file));
        assertTrue(exception.getMessage().contains("单次导入上限 200 行"));
    }

    @Test
    @DisplayName("导入 - 非 xlsx 文件抛异常")
    void importCardTemplates_notXlsx_shouldThrow() {
        MockMultipartFile file = new MockMultipartFile("file", "test.txt",
                "text/plain", "not excel".getBytes());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> cardTemplateAppService.importCardTemplates(file));
        assertEquals("仅支持 .xlsx 格式文件", exception.getMessage());
    }

    @Test
    @DisplayName("导入 - Excel 内部编码重复返回失败")
    void importCardTemplates_duplicateCodeInExcel_shouldFail() throws IOException {
        byte[] excel = createTestExcel(new String[][]{
                {"CARD-001", "第一行", "PKM", "SR", "", ""},
                {"CARD-001", "第二行", "PKM", "R", "", ""},
        });
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excel);

        IpSeries pkSeries = IpSeries.reconstruct(1L, "PKM", "宝可梦", "描述",
                FileRef.of(1L, "https://example.com/cover.jpg"), IpSeriesStatus.ACTIVE,
                LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                LocalDateTime.of(2026, 1, 1, 0, 0, 0));
        when(ipSeriesRepositoryPort.findAll()).thenReturn(List.of(pkSeries));

        CardTemplateImportResult result = cardTemplateAppService.importCardTemplates(file);

        // 第一行成功或失败取决于单条逻辑，第二行必然因重复而失败
        assertTrue(result.getFailCount() >= 1);
    }

    @Test
    @DisplayName("导入 - DB 已有同编码返回失败")
    void importCardTemplates_duplicateCodeInDb_shouldFail() throws IOException {
        byte[] excel = createTestExcel(new String[][]{
                {"CARD-001", "测试", "PKM", "SR", "", ""},
        });
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excel);

        IpSeries pkSeries = IpSeries.reconstruct(1L, "PKM", "宝可梦", "描述",
                FileRef.of(1L, "https://example.com/cover.jpg"), IpSeriesStatus.ACTIVE,
                LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                LocalDateTime.of(2026, 1, 1, 0, 0, 0));
        when(ipSeriesRepositoryPort.findAll()).thenReturn(List.of(pkSeries));
        // DB 已有同编码
        when(cardTemplateRepositoryPort.findByIpSeriesIdAndCode(eq(1L), eq("CARD-001")))
                .thenReturn(Optional.of(buildCardTemplate(99L, "CARD-001", "已有", CardRarity.R, CardTemplateStatus.ACTIVE)));

        CardTemplateImportResult result = cardTemplateAppService.importCardTemplates(file);

        assertEquals(1, result.getFailCount());
        assertTrue(result.getFailDetails().get(0).getReason().contains("已存在"));
    }

    // ========== 辅助方法 ==========

    /**
     * 创建测试用 Excel byte[]
     */
    private byte[] createTestExcel(String[][] data) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            String[] headers = {"编码", "名称", "所属IP系列编码", "稀有度", "描述", "状态"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            for (int r = 0; r < data.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < data[r].length; c++) {
                    if (data[r][c] != null && !data[r][c].isEmpty()) {
                        row.createCell(c).setCellValue(data[r][c]);
                    }
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }
}
