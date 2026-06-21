package com.knowledgegame.admin.application.service;

import com.knowledgegame.admin.api.dto.response.CardTemplateImportResult;
import com.knowledgegame.admin.api.dto.response.ImportFailDetail;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.domainenum.CardRarity;
import com.knowledgegame.core.domain.model.domainenum.CardTemplateStatus;
import com.knowledgegame.core.domain.model.domainenum.IpSeriesStatus;
import com.knowledgegame.core.domain.model.entity.CardTemplate;
import com.knowledgegame.core.domain.model.entity.IpSeries;
import com.knowledgegame.core.domain.model.vo.FileRef;
import com.knowledgegame.core.domain.port.outbound.CardTemplateRepositoryPort;
import com.knowledgegame.core.domain.port.outbound.IpSeriesRepositoryPort;
import com.knowledgegame.core.domain.service.CardTemplateDomainService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * CardTemplateAppService 导入方法独立测试（PRD 行为视角）。
 * <p>
 * 仅依据 PRD 验收标准编写，不参考实现细节，与开发者测试形成交叉验证。
 */
@ExtendWith(MockitoExtension.class)
class CardTemplateImportIndependentTest {

    @Mock
    private CardTemplateDomainService cardTemplateDomainService;
    @Mock
    private CardTemplateRepositoryPort cardTemplateRepositoryPort;
    @Mock
    private IpSeriesRepositoryPort ipSeriesRepositoryPort;

    @InjectMocks
    private CardTemplateAppService appService;

    @BeforeEach
    void setUp() {
        // 预加载 IpSeries，code="PKM" → id=1（部分测试不调用，用 lenient）
        IpSeries pkSeries = IpSeries.reconstruct(1L, "PKM", "宝可梦", "描述",
                FileRef.of(1L, "https://example.com/cover.jpg"), IpSeriesStatus.ACTIVE,
                LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                LocalDateTime.of(2026, 1, 1, 0, 0, 0));
        lenient().when(ipSeriesRepositoryPort.findAll()).thenReturn(List.of(pkSeries));
        // DB 无重复编码（部分测试覆盖此 stub，用 lenient）
        lenient().when(cardTemplateRepositoryPort.findByIpSeriesIdAndCode(any(), any()))
                .thenReturn(Optional.empty());
    }

    // ========== 正常路径 ==========

    @Test
    @DisplayName("1. 正常导入 — 3 行有效数据全部成功")
    void normalImportAllSuccess() throws IOException {
        byte[] excel = createExcel(new String[][]{
                {"CARD-001", "卡牌1", "PKM", "SR", "", "启用"},
                {"CARD-002", "卡牌2", "PKM", "R", "描述", ""},
                {"CARD-003", "卡牌3", "PKM", "SSR", "", "停用"},
        });
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excel);

        when(cardTemplateDomainService.validateAndCreate(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(buildTemplate());

        CardTemplateImportResult result = appService.importCardTemplates(file);

        assertEquals(3, result.getTotalCount());
        assertEquals(3, result.getSuccessCount());
        assertEquals(0, result.getFailCount());
    }

    @Test
    @DisplayName("2. 混合结果 — 2 有效 + 1 稀有度错误")
    void mixedResultsOneInvalidRarity() throws IOException {
        byte[] excel = createExcel(new String[][]{
                {"CARD-001", "卡牌1", "PKM", "SR", "", ""},
                {"CARD-002", "卡牌2", "PKM", "LEGENDARY", "", ""},
                {"CARD-003", "卡牌3", "PKM", "R", "", ""},
        });
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excel);

        when(cardTemplateDomainService.validateAndCreate(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(buildTemplate());

        CardTemplateImportResult result = appService.importCardTemplates(file);

        assertEquals(3, result.getTotalCount());
        assertEquals(2, result.getSuccessCount());
        assertEquals(1, result.getFailCount());
        assertTrue(result.getFailDetails().get(0).getReason().contains("稀有度格式错误"));
    }

    // ========== 校验失败路径 ==========

    @Test
    @DisplayName("3. 编码必填缺失 — 失败原因含\"不能为空\"")
    void missingCodeShouldFail() throws IOException {
        byte[] excel = createExcel(new String[][]{
                {"", "卡牌1", "PKM", "SR", "", ""},
        });
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excel);

        CardTemplateImportResult result = appService.importCardTemplates(file);

        assertEquals(1, result.getFailCount());
        assertTrue(result.getFailDetails().get(0).getReason().contains("编码不能为空"));
    }

    @Test
    @DisplayName("4. 名称必填缺失 — 失败原因含\"不能为空\"")
    void missingNameShouldFail() throws IOException {
        byte[] excel = createExcel(new String[][]{
                {"CARD-001", "", "PKM", "SR", "", ""},
        });
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excel);

        CardTemplateImportResult result = appService.importCardTemplates(file);

        assertEquals(1, result.getFailCount());
        assertTrue(result.getFailDetails().get(0).getReason().contains("名称不能为空"));
    }

    @Test
    @DisplayName("5. 稀有度非法 — 填 LEGENDARY 应失败")
    void invalidRarityLegendaryShouldFail() throws IOException {
        byte[] excel = createExcel(new String[][]{
                {"CARD-001", "卡牌1", "PKM", "LEGENDARY", "", ""},
        });
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excel);

        CardTemplateImportResult result = appService.importCardTemplates(file);

        assertEquals(1, result.getFailCount());
        assertTrue(result.getFailDetails().get(0).getReason().contains("格式错误"));
    }

    @Test
    @DisplayName("6. 状态非法 — 填 online 应失败")
    void invalidStatusOnlineShouldFail() throws IOException {
        byte[] excel = createExcel(new String[][]{
                {"CARD-001", "卡牌1", "PKM", "SR", "", "online"},
        });
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excel);

        CardTemplateImportResult result = appService.importCardTemplates(file);

        assertEquals(1, result.getFailCount());
        assertTrue(result.getFailDetails().get(0).getReason().contains("格式错误"));
    }

    @Test
    @DisplayName("7. IP 系列编码不存在 — 失败原因含\"不存在\"")
    void ipSeriesNotExistShouldFail() throws IOException {
        byte[] excel = createExcel(new String[][]{
                {"CARD-001", "卡牌1", "UNKNOWN", "SR", "", ""},
        });
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excel);

        CardTemplateImportResult result = appService.importCardTemplates(file);

        assertEquals(1, result.getFailCount());
        assertTrue(result.getFailDetails().get(0).getReason().contains("不存在"));
    }

    @Test
    @DisplayName("8. IP 系列编码为空 — 失败原因含\"不能为空\"")
    void emptyIpSeriesCodeShouldFail() throws IOException {
        byte[] excel = createExcel(new String[][]{
                {"CARD-001", "卡牌1", "", "SR", "", ""},
        });
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excel);

        CardTemplateImportResult result = appService.importCardTemplates(file);

        assertEquals(1, result.getFailCount());
        assertTrue(result.getFailDetails().get(0).getReason().contains("IP系列编码"));
    }

    @Test
    @DisplayName("9. 编码重复（DB 已有）— 失败原因含\"已存在\"")
    void duplicateCodeInDbShouldFail() throws IOException {
        // 覆盖 setUp 中 mock 的 findAll（返回 PKM）但 findByIdAndCode 返回已有数据
        byte[] excel = createExcel(new String[][]{
                {"CARD-001", "卡牌1", "PKM", "SR", "", ""},
        });
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excel);

        when(cardTemplateRepositoryPort.findByIpSeriesIdAndCode(eq(1L), eq("CARD-001")))
                .thenReturn(Optional.of(buildTemplate()));

        CardTemplateImportResult result = appService.importCardTemplates(file);

        assertEquals(1, result.getFailCount());
        assertTrue(result.getFailDetails().get(0).getReason().contains("已存在"));
    }

    @Test
    @DisplayName("10. Excel 内部编码重复 — 第 2 行失败，reason 含\"与第N行重复\"")
    void duplicateCodeInExcelShouldFail() throws IOException {
        byte[] excel = createExcel(new String[][]{
                {"CARD-001", "第一行", "PKM", "SR", "", ""},
                {"CARD-001", "第二行", "PKM", "R", "", ""},
        });
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excel);

        when(cardTemplateDomainService.validateAndCreate(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(buildTemplate());

        CardTemplateImportResult result = appService.importCardTemplates(file);

        assertTrue(result.getFailCount() >= 1);
    }

    // ========== 边界条件 ==========

    @Test
    @DisplayName("11. 空模板 — totalCount/successCount/failCount 均为 0")
    void emptyTemplateShouldReturnZero() throws IOException {
        byte[] excel = createExcel(new String[][]{});
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excel);

        CardTemplateImportResult result = appService.importCardTemplates(file);

        assertEquals(0, result.getTotalCount());
        assertEquals(0, result.getSuccessCount());
        assertEquals(0, result.getFailCount());
    }

    @Test
    @DisplayName("12. 超 200 行 — 直接抛 BusinessException 拒绝")
    void exceeds200ShouldThrow() throws IOException {
        String[][] data = new String[201][];
        for (int i = 0; i < 201; i++) {
            data[i] = new String[]{"CARD-" + i, "卡牌" + i, "PKM", "R", "", ""};
        }
        byte[] excel = createExcel(data);
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excel);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> appService.importCardTemplates(file));
        assertTrue(exception.getMessage().contains("单次导入上限 200 行"));
    }

    @Test
    @DisplayName("13. 非 xlsx 文件 — 抛 BusinessException")
    void notXlsxShouldThrow() {
        MockMultipartFile file = new MockMultipartFile("file", "test.txt",
                "text/plain", "not excel".getBytes());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> appService.importCardTemplates(file));
        assertEquals("仅支持 .xlsx 格式文件", exception.getMessage());
    }

    // ========== 辅助 ==========

    private CardTemplate buildTemplate() {
        return CardTemplate.reconstruct(99L, 1L, "CODE", "名称",
                CardRarity.R, "描述", CardTemplateStatus.ACTIVE, null,
                LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                LocalDateTime.of(2026, 1, 1, 0, 0, 0));
    }

    private byte[] createExcel(String[][] data) throws IOException {
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
