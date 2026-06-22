package com.knowledgegame.admin.application.service;

import com.knowledgegame.admin.api.assembler.CardTemplateAssembler;
import com.knowledgegame.admin.api.dto.request.UpdateCardTemplateRequest;
import com.knowledgegame.admin.api.dto.response.CardTemplateImportResult;
import com.knowledgegame.admin.api.dto.response.CardTemplateListResponse;
import com.knowledgegame.admin.api.dto.response.CardTemplateResponse;
import com.knowledgegame.admin.api.dto.response.ImportFailDetail;
import com.knowledgegame.auth.security.SecurityUtils;
import com.knowledgegame.components.feign.client.FileServiceClient;
import com.knowledgegame.components.feign.dto.FileInfoResponse;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.common.result.Result;
import com.knowledgegame.core.common.util.EnumUtils;
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
import com.knowledgegame.core.domain.service.IpSeriesDomainService;
import com.knowledgegame.core.domain.service.recyclebin.RecycleBinItemStrategy;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFDataValidationHelper;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

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
    private final RecycleBinItemStrategy<CardTemplate> recycleBinStrategy;


    public CardTemplateAppService(CardTemplateDomainService cardTemplateDomainService,
                                  CardTemplateRepositoryPort cardTemplateRepositoryPort,
                                  IpSeriesRepositoryPort ipSeriesRepositoryPort,
                                  IpSeriesDomainService ipSeriesDomainService,
                                  FileServiceClient fileServiceClient,
                                  RecycleBinItemStrategy<CardTemplate> recycleBinStrategy) {
        this.cardTemplateDomainService = cardTemplateDomainService;
        this.cardTemplateRepositoryPort = cardTemplateRepositoryPort;
        this.ipSeriesRepositoryPort = ipSeriesRepositoryPort;
        this.ipSeriesDomainService = ipSeriesDomainService;
        this.fileServiceClient = fileServiceClient;
        this.recycleBinStrategy = recycleBinStrategy;
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
     * 更新卡牌模板基础信息（支持 JsonNullable 三态）
     */
    @Transactional
    public CardTemplateResponse update(Long id, UpdateCardTemplateRequest req) {
        CardTemplate template = cardTemplateRepositoryPort.findById(id)
                .orElseThrow(() -> new BusinessException("卡牌模板不存在: " + id));
        // 编码在同一 IP 系列下唯一（排除自身）
        if (req.getCode() != null && !req.getCode().equals(template.getCode())) {
            cardTemplateRepositoryPort.findByIpSeriesIdAndCode(template.getIpSeriesId(), req.getCode())
                    .ifPresent(existing -> {
                        throw new BusinessException("卡牌编码已存在: " + req.getCode());
                    });
        }

        // 必填字段：null=不更新
        template.update(req.getCode(), req.getName(), req.getRarity(), req.getStatus());

        // 可清空 String：description
        applyField(req.getDescription(), template::clearDescription, template::updateDescription);

        // 可清空 FileRef：image
        applyFileRefField(req.getImageFileId(), "CARD_TEMPLATE",
                template::clearImage, template::updateImage);

        CardTemplate saved = cardTemplateRepositoryPort.save(template);
        return assembleDetailResponse(saved);
    }

    /**
     * 删除卡牌模板（移入回收站）
     */
    @Transactional
    public void deleteCardTemplate(Long id) {
        if (!cardTemplateRepositoryPort.existsById(id)) {
            throw new BusinessException("卡牌模板不存在: " + id);
        }
        recycleBinStrategy.validateDeletable(id);
        recycleBinStrategy.moveToRecycleBin(id, SecurityUtils.getCurrentUsername());
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

    /**
     * 下载卡牌模板导入模板
     */
    public void downloadImportTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=card_template_import_template.xlsx");

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("卡牌模板导入");

            // 编码列（A 列）设为文本格式，防止 Excel 将 001 转为数字 1
            CellStyle textStyle = workbook.createCellStyle();
            DataFormat dataFormat = workbook.createDataFormat();
            textStyle.setDataFormat(dataFormat.getFormat("@"));

            String[] headers = {"编码", "名称", "所属IP系列编码", "稀有度", "描述", "状态"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            // 示例行 1
            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("PIKACHU");
            row1.getCell(0).setCellStyle(textStyle);
            row1.createCell(1).setCellValue("皮卡丘");
            row1.createCell(2).setCellValue("PKM");
            row1.createCell(3).setCellValue("SR");
            row1.createCell(4).setCellValue("电气鼠宝可梦");
            row1.createCell(5).setCellValue("启用");

            // 示例行 2
            Row row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue("CHARIZARD");
            row2.getCell(0).setCellStyle(textStyle);
            row2.createCell(1).setCellValue("喷火龙");
            row2.createCell(2).setCellValue("PKM");
            row2.createCell(3).setCellValue("SSR");
            row2.createCell(4).setCellValue("");
            row2.createCell(5).setCellValue("启用");

            // A 列默认文本格式（新单元格继承）
            sheet.setDefaultColumnStyle(0, textStyle);

            // 填写说明
            Row tipRow = sheet.createRow(3);
            tipRow.createCell(0).setCellValue("【填写说明】");
            tipRow.createCell(1).setCellValue("编码：2~50字符，同一IP系列下唯一；名称：1~50字符；所属IP系列编码：必须存在且为启用状态；稀有度：N/R/SR/SSR/SP；状态：启用/停用，留空默认启用；描述：最大500字符，可选");

            sheet.addMergedRegion(new CellRangeAddress(3, 3, 1, 5));

            // 稀有度下拉（D 列，index=3）
            DataValidationHelper dvHelper = new XSSFDataValidationHelper((XSSFSheet) sheet);
            DataValidationConstraint rarityConstraint = dvHelper.createExplicitListConstraint(
                    new String[]{"N", "R", "SR", "SSR", "SP"});
            CellRangeAddressList rarityRange = new CellRangeAddressList(1, 201, 3, 3);
            DataValidation rarityValidation = dvHelper.createValidation(rarityConstraint, rarityRange);
            rarityValidation.setShowErrorBox(true);
            rarityValidation.createErrorBox("稀有度格式错误", "请选择 N / R / SR / SSR / SP");
            sheet.addValidationData(rarityValidation);

            // 状态下拉（F 列，index=5）
            DataValidationConstraint statusConstraint = dvHelper.createExplicitListConstraint(
                    new String[]{"启用", "停用"});
            CellRangeAddressList statusRange = new CellRangeAddressList(1, 201, 5, 5);
            DataValidation statusValidation = dvHelper.createValidation(statusConstraint, statusRange);
            statusValidation.setShowErrorBox(true);
            statusValidation.createErrorBox("状态格式错误", "请选择 启用 或 停用");
            sheet.addValidationData(statusValidation);

            workbook.write(response.getOutputStream());
        }
    }

    /**
     * 导入卡牌模板
     */
    @Transactional
    public CardTemplateImportResult importCardTemplates(MultipartFile file) throws IOException {
        // 文件校验
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.endsWith(".xlsx")) {
            throw new BusinessException("仅支持 .xlsx 格式文件");
        }
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new BusinessException("文件大小不能超过 10MB");
        }

        List<ImportFailDetail> failDetails = new ArrayList<>();
        int successCount = 0;

        // 预加载 IP 系列 code→id 映射
        Map<String, Long> ipSeriesCodeToId = buildIpSeriesCodeMap();

        // Excel 内部去重（同一 IP 系列下 code 唯一），key = ipSeriesCode::code
        Map<String, Integer> excelCodeRowMap = new HashMap<>();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            int lastRowNum = sheet.getLastRowNum();
            // 统计数据行数（跳过表头行0、跳过说明行、跳过空行）
            int dataRowCount = 0;
            for (int i = 1; i <= lastRowNum; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String firstCell = getCellStringValue(row, 0);
                if (firstCell != null && firstCell.startsWith("【")) continue;
                if (isRowEmpty(row)) continue;
                dataRowCount++;
            }

            if (dataRowCount == 0) {
                return CardTemplateImportResult.builder()
                        .totalCount(0).successCount(0).failCount(0)
                        .failDetails(List.of()).build();
            }
            if (dataRowCount > 200) {
                throw new BusinessException("单次导入上限 200 行，当前 " + dataRowCount + " 行");
            }

            // 逐行解析 + 导入
            for (int i = 1; i <= lastRowNum; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String firstCell = getCellStringValue(row, 0);
                if (firstCell != null && firstCell.startsWith("【")) continue;
                if (isRowEmpty(row)) continue;

                try {
                    // Excel 内部去重（先于 parseRow，用原始值构建 key）
                    String rawCode = getCellStringValue(row, 0);
                    String rawIpCode = getCellStringValue(row, 2);
                    if (rawCode != null && rawIpCode != null) {
                        String dedupKey = rawIpCode.trim() + "::" + rawCode.trim();
                        Integer prevRow = excelCodeRowMap.putIfAbsent(dedupKey, i + 1);
                        if (prevRow != null) {
                            throw new BusinessException("编码 " + rawCode.trim() + " 与第" + prevRow + "行重复");
                        }
                    }
                    ImportRowData data = parseRow(row, ipSeriesCodeToId);
                    importOne(data);
                    successCount++;
                } catch (BusinessException e) {
                    failDetails.add(ImportFailDetail.builder()
                            .row(i + 1).reason(e.getMessage()).build());
                }
            }

            return CardTemplateImportResult.builder()
                    .totalCount(dataRowCount)
                    .successCount(successCount)
                    .failCount(failDetails.size())
                    .failDetails(failDetails)
                    .build();
        }
    }

    /**
     * 构建 IP 系列 code → id 映射（仅 ACTIVE）
     */
    private Map<String, Long> buildIpSeriesCodeMap() {
        List<IpSeries> allSeries = ipSeriesRepositoryPort.findAll();
        Map<String, Long> map = new HashMap<>();
        for (IpSeries s : allSeries) {
            if (s.getStatus() == IpSeriesStatus.ACTIVE) {
                map.put(s.getCode(), s.getId());
            }
        }
        return map;
    }

    /**
     * 解析一行导入数据
     */
    private ImportRowData parseRow(Row row, Map<String, Long> ipSeriesCodeToId) {
        String code = getCellStringValue(row, 0);
        String name = getCellStringValue(row, 1);
        String ipSeriesCode = getCellStringValue(row, 2);
        String rarityStr = getCellStringValue(row, 3);
        String description = getCellStringValue(row, 4);
        String statusStr = getCellStringValue(row, 5);

        // 必填校验
        if (code == null || code.isBlank()) throw new BusinessException("编码不能为空");
        if (name == null || name.isBlank()) throw new BusinessException("名称不能为空");
        if (rarityStr == null || rarityStr.isBlank()) throw new BusinessException("稀有度不能为空");

        code = code.trim();
        name = name.trim();
        rarityStr = rarityStr.trim();

        // 稀有度枚举校验（用 JDK Enum.valueOf，不用 EnumUtils，后者内部吞掉异常并重抛通用消息）
        CardRarity rarity;
        try {
            rarity = Enum.valueOf(CardRarity.class, rarityStr);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("稀有度格式错误：" + rarityStr + "，可选值 N/R/SR/SSR/SP");
        }

        // 状态解析（缺失默认 ACTIVE）
        CardTemplateStatus status = CardTemplateStatus.ACTIVE;
        if (statusStr != null && !statusStr.isBlank()) {
            statusStr = statusStr.trim();
            if ("启用".equals(statusStr)) {
                status = CardTemplateStatus.ACTIVE;
            } else if ("停用".equals(statusStr)) {
                status = CardTemplateStatus.INACTIVE;
            } else {
                throw new BusinessException("状态格式错误：" + statusStr + "，可选值 启用/停用");
            }
        }

        // IP 系列校验
        if (ipSeriesCode == null || ipSeriesCode.isBlank()) {
            throw new BusinessException("所属IP系列编码不能为空");
        }
        ipSeriesCode = ipSeriesCode.trim();
        Long ipSeriesId = ipSeriesCodeToId.get(ipSeriesCode);
        if (ipSeriesId == null) {
            throw new BusinessException("IP系列编码不存在或已停用：" + ipSeriesCode);
        }

        if (description != null) {
            description = description.trim();
            if (description.isEmpty()) description = null;
        }

        return new ImportRowData(code, name, ipSeriesId, rarity, description, status);
    }

    /**
     * 导入单条卡牌模板
     */
    private void importOne(ImportRowData data) {
        // 编码同一 IP 系列下唯一（校验 DB 已有数据）
        cardTemplateRepositoryPort.findByIpSeriesIdAndCode(data.ipSeriesId, data.code)
                .ifPresent(existing -> {
                    throw new BusinessException("编码 " + data.code + " 在 IP 系列内已存在");
                });

        CardTemplate template = cardTemplateDomainService.validateAndCreate(
                data.ipSeriesId, data.code, data.name,
                data.rarity, data.description, data.status, null);
        cardTemplateRepositoryPort.save(template);
    }

    /**
     * 获取单元格字符串值
     */
    private String getCellStringValue(Row row, int colIndex) {
        Cell cell = row.getCell(colIndex);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                yield switch (cell.getCachedFormulaResultType()) {
                    case STRING -> cell.getStringCellValue();
                    case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
                    case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
                    default -> null;
                };
            }
            default -> null;
        };
    }

    /**
     * 判断行是否为空（所有列均无值）
     */
    private boolean isRowEmpty(Row row) {
        for (int i = 0; i < 6; i++) {
            String val = getCellStringValue(row, i);
            if (val != null && !val.isBlank()) return false;
        }
        return true;
    }

    /**
     * 导入行数据内部记录
     */
    private record ImportRowData(
            String code,
            String name,
            Long ipSeriesId,
            CardRarity rarity,
            String description,
            CardTemplateStatus status) {
    }

    /**
     * 三态分派工具：处理 JsonNullable<T> 字段
     */
    private static <T> void applyField(JsonNullable<T> field, Runnable clear, Consumer<T> update) {
        if (field == null || !field.isPresent()) {
            return;
        }
        T value = field.get();
        if (value == null) {
            clear.run();
        } else {
            update.accept(value);
        }
    }

    /**
     * 三态分派工具：处理 JsonNullable<Long>（FileRef fileId）字段
     */
    private void applyFileRefField(JsonNullable<Long> fileIdField, String bizType,
                                   Runnable clear, Consumer<FileRef> update) {
        if (fileIdField == null || !fileIdField.isPresent()) {
            return;
        }
        Long fileId = fileIdField.get();
        if (fileId == null) {
            clear.run();
        } else {
            FileRef verified = verifyFileRef(fileId, bizType);
            update.accept(verified);
        }
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
