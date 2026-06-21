package com.knowledgegame.admin.application.service;

import com.knowledgegame.admin.api.assembler.KnowledgeItemAssembler;
import com.knowledgegame.admin.api.dto.request.BatchSortItem;
import com.knowledgegame.admin.api.dto.request.BatchSortRequest;
import com.knowledgegame.admin.api.dto.request.CreateKnowledgeItemRequest;
import com.knowledgegame.admin.api.dto.request.UpdateKnowledgeItemRequest;
import com.knowledgegame.admin.api.dto.response.ImportFailDetail;
import com.knowledgegame.admin.api.dto.response.KnowledgeItemImportResult;
import com.knowledgegame.admin.api.dto.response.KnowledgeItemListResponse;
import com.knowledgegame.admin.api.dto.response.KnowledgeItemResponse;
import com.knowledgegame.auth.security.SecurityUtils;
import com.knowledgegame.components.feign.client.FileServiceClient;
import com.knowledgegame.components.feign.dto.FileInfoResponse;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.common.result.Result;
import com.knowledgegame.core.common.util.EnumUtils;
import com.knowledgegame.core.domain.model.domainenum.KnowledgeCategoryStatus;
import com.knowledgegame.core.domain.model.domainenum.KnowledgeItemStatus;
import com.knowledgegame.core.domain.model.entity.KnowledgeCategory;
import com.knowledgegame.core.domain.model.entity.KnowledgeItem;
import com.knowledgegame.core.domain.model.vo.FileRef;
import com.knowledgegame.core.domain.model.vo.KnowledgeItemSummary;
import com.knowledgegame.core.domain.model.vo.PageResult;
import com.knowledgegame.core.domain.model.vo.SortField;
import com.knowledgegame.core.domain.port.outbound.KnowledgeCategoryRepositoryPort;
import com.knowledgegame.core.domain.port.outbound.KnowledgeItemRepository;
import com.knowledgegame.core.domain.service.KnowledgeItemDomainService;
import com.knowledgegame.core.infrastructure.markdown.MarkdownRenderer;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 知识条目管理端应用服务（流程编排 + 事务，返回 DTO）
 */
@Service
public class KnowledgeItemAppService {

    private static final String BIZ_TYPE = "KNOWLEDGE_ITEM_COVER";

    private final KnowledgeItemRepository itemRepository;
    private final KnowledgeItemDomainService itemDomainService;
    private final KnowledgeCategoryRepositoryPort categoryRepositoryPort;
    private final FileServiceClient fileServiceClient;
    private final MarkdownRenderer markdownRenderer;

    public KnowledgeItemAppService(KnowledgeItemRepository itemRepository,
                                    KnowledgeItemDomainService itemDomainService,
                                    KnowledgeCategoryRepositoryPort categoryRepositoryPort,
                                    FileServiceClient fileServiceClient,
                                    MarkdownRenderer markdownRenderer) {
        this.itemRepository = itemRepository;
        this.itemDomainService = itemDomainService;
        this.categoryRepositoryPort = categoryRepositoryPort;
        this.fileServiceClient = fileServiceClient;
        this.markdownRenderer = markdownRenderer;
    }

    /**
     * 创建知识条目
     */
    @Transactional
    public KnowledgeItemResponse create(CreateKnowledgeItemRequest request) {
        String contentHtml = markdownRenderer.render(request.getContent());
        FileRef coverImage = verifyFileRef(request.getCoverImageFileId(), BIZ_TYPE);
        int sortOrder = request.getSortOrder() != null ? request.getSortOrder() : 0;

        KnowledgeItem item = itemDomainService.validateAndCreate(
                request.getTitle(), request.getContent(), coverImage,
                request.getTags(), sortOrder, request.getCategoryIds()
        );
        item.updateContentHtml(contentHtml);
        KnowledgeItem saved = itemRepository.save(item);
        itemRepository.saveCategoryRelations(saved.getId(), request.getCategoryIds());

        return toResponseWithCategories(saved);
    }

    /**
     * 查询详情
     */
    public KnowledgeItemResponse getById(Long id) {
        KnowledgeItem item = itemRepository.findById(id)
                .orElseThrow(() -> new BusinessException("知识条目不存在: " + id));
        return toResponseWithCategories(item);
    }

    /**
     * 分页查询（列表使用摘要投影，不含正文 content/contentHtml）
     */
    public PageResult<KnowledgeItemListResponse> list(String keyword, Long categoryId, String tag,
                                                       String status, String sort, String order,
                                                       int page, int size) {
        KnowledgeItemStatus statusEnum = EnumUtils.valueOfNullable(KnowledgeItemStatus.class, status);
        SortField sortField = SortField.parse(sort, order);

        PageResult<KnowledgeItemSummary> summaryPage = itemRepository.findByConditionsSummary(
                keyword, categoryId, tag, statusEnum, sortField, page, size
        );

        List<Long> pageItemIds = summaryPage.getContent().stream()
                .map(KnowledgeItemSummary::getId).toList();
        Map<Long, List<Long>> categoryMap = pageItemIds.isEmpty()
                ? Map.of()
                : itemRepository.findActiveCategoryIdsByItemIds(pageItemIds);

        return PageResult.<KnowledgeItemListResponse>builder()
                .content(summaryPage.getContent().stream()
                        .map(summary -> KnowledgeItemAssembler.INSTANCE.toListResponse(
                                summary, categoryMap.getOrDefault(summary.getId(), List.of())))
                        .toList())
                .totalElements(summaryPage.getTotalElements())
                .pageNumber(summaryPage.getPageNumber())
                .pageSize(summaryPage.getPageSize())
                .totalPages(summaryPage.getTotalPages())
                .build();
    }

    /**
     * 更新知识条目
     */
    @Transactional
    public KnowledgeItemResponse update(Long id, UpdateKnowledgeItemRequest request) {
        KnowledgeItem item = itemRepository.findById(id)
                .orElseThrow(() -> new BusinessException("知识条目不存在: " + id));

        // 内容变更时重新渲染 HTML
        String contentHtml = null;
        if (request.getContent() != null) {
            contentHtml = markdownRenderer.render(request.getContent());
        }

        FileRef coverImage = verifyFileRef(request.getCoverImageFileId(), BIZ_TYPE);

        itemDomainService.validateUpdate(item, request.getTitle(), request.getContent(), request.getTags());

        item.update(request.getTitle(), request.getContent(), coverImage,
                request.getTags(), request.getSortOrder());
        if (contentHtml != null) {
            item.updateContentHtml(contentHtml);
        }
        KnowledgeItem saved = itemRepository.save(item);

        return toResponseWithCategories(saved);
    }

    /**
     * 删除知识条目（软删除，无前置校验）
     */
    @Transactional
    public void delete(Long id) {
        KnowledgeItem item = itemRepository.findById(id)
                .orElseThrow(() -> new BusinessException("知识条目不存在: " + id));
        item.deactivate();
        itemRepository.save(item);
    }

    /**
     * 查询条目关联的分类 ID 列表
     */
    public List<Long> getCategoryIds(Long itemId) {
        if (itemRepository.findById(itemId).isEmpty()) {
            throw new BusinessException("知识条目不存在: " + itemId);
        }
        return itemRepository.findActiveCategoryIdsByItemId(itemId);
    }

    /**
     * 更新条目的分类关联（全量替换）
     */
    @Transactional
    public void updateCategories(Long itemId, List<Long> categoryIds) {
        if (itemRepository.findById(itemId).isEmpty()) {
            throw new BusinessException("知识条目不存在: " + itemId);
        }
        validateCategoryIds(categoryIds);
        itemRepository.saveCategoryRelations(itemId, categoryIds);
    }

    /**
     * 批量启用（含分类状态前置校验）
     */
    @Transactional
    public void batchActivate(List<Long> ids) {
        List<Long> distinctIds = ids.stream().distinct().toList();
        List<KnowledgeItem> items = itemRepository.findByIds(distinctIds);
        if (items.size() != distinctIds.size()) {
            throw new BusinessException("部分知识条目 ID 不存在");
        }
        Map<Long, String> idToName = items.stream()
                .collect(Collectors.toMap(KnowledgeItem::getId, KnowledgeItem::getTitle));
        Map<Long, List<Long>> itemToCategoryIds = itemRepository.findCategoryIdsByItemIds(distinctIds);
        List<Long> allCategoryIds = itemToCategoryIds.values().stream()
                .flatMap(List::stream)
                .distinct()
                .toList();
        Map<Long, KnowledgeCategory> categoryMap = allCategoryIds.isEmpty()
                ? Map.of()
                : categoryRepositoryPort.findAllByIdIn(allCategoryIds).stream()
                        .collect(Collectors.toMap(KnowledgeCategory::getId, c -> c));
        for (Long id : distinctIds) {
            String itemName = idToName.getOrDefault(id, "(ID=" + id + ")");
            List<Long> categoryIds = itemToCategoryIds.getOrDefault(id, List.of());
            itemDomainService.validateActivatable(itemName, categoryIds, categoryMap);
        }
        itemRepository.batchUpdateStatus(distinctIds, KnowledgeItemStatus.ACTIVE);
    }

    /**
     * 批量禁用
     */
    @Transactional
    public void batchDeactivate(List<Long> ids) {
        List<Long> distinctIds = ids.stream().distinct().toList();
        itemRepository.batchUpdateStatus(distinctIds, KnowledgeItemStatus.INACTIVE);
    }

    /**
     * 批量排序（无父子层级，跳过同父级校验）
     */
    @Transactional
    public void batchSort(BatchSortRequest request) {
        List<BatchSortItem> items = request.getItems();
        Map<Long, KnowledgeItem> itemMap = new LinkedHashMap<>();
        for (BatchSortItem sortItem : items) {
            KnowledgeItem ki = itemRepository.findById(sortItem.getId())
                    .orElseThrow(() -> new BusinessException("知识条目不存在: " + sortItem.getId()));
            itemMap.put(sortItem.getId(), ki);
        }
        for (BatchSortItem sortItem : items) {
            KnowledgeItem ki = itemMap.get(sortItem.getId());
            ki.moveToSortOrder(sortItem.getSortOrder());
            itemRepository.save(ki);
        }
    }

    /**
     * 下载知识条目导入模板
     */
    public void downloadImportTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=knowledge_item_import_template.xlsx");

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("知识条目导入");

            // 标题列（A 列）和标签列（C 列）设为文本格式，防止 Excel 自动转换数字
            CellStyle textStyle = workbook.createCellStyle();
            DataFormat dataFormat = workbook.createDataFormat();
            textStyle.setDataFormat(dataFormat.getFormat("@"));

            String[] headers = {"标题", "Markdown正文", "标签", "分类名称", "状态", "排序"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            // 示例行 1
            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("Java 基础语法");
            row1.getCell(0).setCellStyle(textStyle);
            row1.createCell(1).setCellValue("# Java 基础\nJava 是一种面向对象的编程语言。");
            row1.createCell(2).setCellValue("Java,入门");
            row1.getCell(2).setCellStyle(textStyle);
            row1.createCell(3).setCellValue("Java 基础");
            row1.createCell(4).setCellValue("启用");
            row1.createCell(5).setCellValue("0");

            // 示例行 2
            Row row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue("Python 数据结构");
            row2.getCell(0).setCellStyle(textStyle);
            row2.createCell(1).setCellValue("# Python 数据结构\n列表、元组、字典的用法详解。");
            row2.createCell(2).setCellValue("Python,数据结构");
            row2.getCell(2).setCellStyle(textStyle);
            row2.createCell(3).setCellValue("Python 进阶");
            row2.createCell(4).setCellValue("停用");
            row2.createCell(5).setCellValue("1");

            // A 列和 C 列默认文本格式（新单元格继承）
            sheet.setDefaultColumnStyle(0, textStyle);
            sheet.setDefaultColumnStyle(2, textStyle);

            // 填写说明
            Row tipRow = sheet.createRow(3);
            tipRow.createCell(0).setCellValue("【填写说明】");
            tipRow.createCell(1).setCellValue("标题：1~200字符，必填；Markdown正文：1~50000字符，必填；标签：逗号分隔，最多10个，每个≤20字符，可选；分类名称：逗号分隔，必须存在且为启用状态，必填；状态：启用/停用，留空默认启用；排序：整数，留空默认0");
            sheet.addMergedRegion(new CellRangeAddress(3, 3, 1, 5));

            // 状态下拉（E 列，index=4）
            DataValidationHelper dvHelper = new XSSFDataValidationHelper((XSSFSheet) sheet);
            DataValidationConstraint statusConstraint = dvHelper.createExplicitListConstraint(
                    new String[]{"启用", "停用"});
            CellRangeAddressList statusRange = new CellRangeAddressList(1, 201, 4, 4);
            DataValidation statusValidation = dvHelper.createValidation(statusConstraint, statusRange);
            statusValidation.setShowErrorBox(true);
            statusValidation.createErrorBox("状态格式错误", "请选择 启用 或 停用");
            sheet.addValidationData(statusValidation);

            workbook.write(response.getOutputStream());
        }
    }

    /**
     * Excel 批量导入知识条目
     */
    @Transactional
    public KnowledgeItemImportResult importExcel(MultipartFile file) throws IOException {
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

        // 预加载分类 name→id 映射（仅 ACTIVE）
        Map<String, Long> categoryNameToId = buildCategoryNameMap();

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
                return KnowledgeItemImportResult.builder()
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
                    ImportRowData data = parseRow(row, categoryNameToId);
                    importOne(data);
                    successCount++;
                } catch (BusinessException e) {
                    failDetails.add(ImportFailDetail.builder()
                            .row(i + 1).reason(e.getMessage()).build());
                }
            }

            return KnowledgeItemImportResult.builder()
                    .totalCount(dataRowCount)
                    .successCount(successCount)
                    .failCount(failDetails.size())
                    .failDetails(failDetails)
                    .build();
        }
    }

    /**
     * Markdown zip 批量导入知识条目
     */
    @Transactional
    public KnowledgeItemImportResult importMarkdownZip(MultipartFile file) throws IOException {
        // 文件校验
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.endsWith(".zip")) {
            throw new BusinessException("仅支持 .zip 格式文件");
        }
        if (file.getSize() > 20 * 1024 * 1024) {
            throw new BusinessException("文件大小不能超过 20MB");
        }

        // 解压 zip：读取 index.xlsx 和所有 .md 文件
        byte[] indexXlsxBytes = null;
        Map<String, String> mdFileContents = new HashMap<>(); // 文件名 → 内容
        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int len;
                while ((len = zis.read(buf)) != -1) {
                    baos.write(buf, 0, len);
                }
                byte[] bytes = baos.toByteArray();
                if ("index.xlsx".equals(name)) {
                    indexXlsxBytes = bytes;
                } else if (name.endsWith(".md")) {
                    String simpleName = name.contains("/")
                            ? name.substring(name.lastIndexOf('/') + 1)
                            : name;
                    if (mdFileContents.containsKey(simpleName)) {
                        throw new BusinessException("zip 包中存在同名 .md 文件，请确保文件名唯一: " + simpleName);
                    }
                    mdFileContents.put(simpleName, new String(bytes, StandardCharsets.UTF_8));
                }
            }
        }

        if (indexXlsxBytes == null) {
            throw new BusinessException("zip 包中缺少 index.xlsx 文件");
        }

        List<ImportFailDetail> failDetails = new ArrayList<>();
        int successCount = 0;

        // 预加载分类 name→id 映射（仅 ACTIVE）
        Map<String, Long> categoryNameToId = buildCategoryNameMap();

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(indexXlsxBytes))) {
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
                return KnowledgeItemImportResult.builder()
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
                    String filename = getCellStringValue(row, 0);
                    if (filename != null) {
                        filename = filename.trim();
                    }
                    if (filename == null || filename.isBlank()) {
                        throw new BusinessException("文件名不能为空");
                    }
                    String mdContent = mdFileContents.get(filename);
                    if (mdContent == null) {
                        throw new BusinessException("Markdown 文件不存在: " + filename);
                    }
                    ImportRowData data = parseZipRow(row, categoryNameToId, mdContent);
                    importOne(data);
                    successCount++;
                } catch (BusinessException e) {
                    failDetails.add(ImportFailDetail.builder()
                            .row(i + 1).reason(e.getMessage()).build());
                }
            }

            return KnowledgeItemImportResult.builder()
                    .totalCount(dataRowCount)
                    .successCount(successCount)
                    .failCount(failDetails.size())
                    .failDetails(failDetails)
                    .build();
        }
    }

    /**
     * 解析 Markdown zip 一行导入数据（列偏移：0=文件名 1=标题 2=标签 3=分类名称 4=状态 5=排序，正文来自 .md 文件）
     */
    private ImportRowData parseZipRow(Row row, Map<String, Long> categoryNameToId, String content) {
        String title = getCellStringValue(row, 1);
        String tagsStr = getCellStringValue(row, 2);
        String categoryNamesStr = getCellStringValue(row, 3);
        String statusStr = getCellStringValue(row, 4);
        String sortOrderStr = getCellStringValue(row, 5);

        // 必填校验
        if (title == null || title.isBlank()) throw new BusinessException("标题不能为空");
        if (content == null || content.isBlank()) throw new BusinessException("Markdown正文不能为空");
        if (categoryNamesStr == null || categoryNamesStr.isBlank())
            throw new BusinessException("知识条目必须关联至少一个分类");

        title = title.trim();

        // 标签解析（逗号分隔）
        List<String> tags = List.of();
        if (tagsStr != null && !tagsStr.isBlank()) {
            tags = List.of(tagsStr.trim().split("\\s*,\\s*"));
            if (tags.size() == 1 && tags.get(0).isEmpty()) {
                tags = List.of();
            }
        }

        // 分类名称解析（逗号分隔 → 查 name→id 映射）
        List<Long> categoryIds = new ArrayList<>();
        for (String name : categoryNamesStr.trim().split("\\s*,\\s*")) {
            if (name.isBlank()) continue;
            Long id = categoryNameToId.get(name);
            if (id == null) {
                throw new BusinessException("分类名称不存在或已停用：" + name);
            }
            categoryIds.add(id);
        }
        if (categoryIds.isEmpty()) {
            throw new BusinessException("知识条目必须关联至少一个分类");
        }

        // 状态解析（缺失默认 ACTIVE）
        KnowledgeItemStatus status = KnowledgeItemStatus.ACTIVE;
        if (statusStr != null && !statusStr.isBlank()) {
            statusStr = statusStr.trim();
            if ("启用".equals(statusStr)) {
                status = KnowledgeItemStatus.ACTIVE;
            } else if ("停用".equals(statusStr)) {
                status = KnowledgeItemStatus.INACTIVE;
            } else {
                throw new BusinessException("状态格式错误：" + statusStr + "，可选值 启用/停用");
            }
        }

        // 排序解析（缺失默认 0）
        int sortOrder = 0;
        if (sortOrderStr != null && !sortOrderStr.isBlank()) {
            try {
                sortOrder = Integer.parseInt(sortOrderStr.trim());
            } catch (NumberFormatException e) {
                throw new BusinessException("排序值格式错误：" + sortOrderStr.trim() + "，必须为整数");
            }
        }

        return new ImportRowData(title, content, tags, categoryIds, status, sortOrder);
    }

    /**
     * 构建分类 name → id 映射（仅 ACTIVE）
     */
    private Map<String, Long> buildCategoryNameMap() {
        List<KnowledgeCategory> allCategories = categoryRepositoryPort.findAll();
        Map<String, Long> map = new HashMap<>();
        for (KnowledgeCategory c : allCategories) {
            if (c.getStatus() == KnowledgeCategoryStatus.ACTIVE) {
                map.put(c.getName(), c.getId());
            }
        }
        return map;
    }

    /**
     * 解析一行导入数据
     */
    private ImportRowData parseRow(Row row, Map<String, Long> categoryNameToId) {
        String title = getCellStringValue(row, 0);
        String content = getCellStringValue(row, 1);
        String tagsStr = getCellStringValue(row, 2);
        String categoryNamesStr = getCellStringValue(row, 3);
        String statusStr = getCellStringValue(row, 4);
        String sortOrderStr = getCellStringValue(row, 5);

        // 必填校验
        if (title == null || title.isBlank()) throw new BusinessException("标题不能为空");
        if (content == null || content.isBlank()) throw new BusinessException("Markdown正文不能为空");
        if (categoryNamesStr == null || categoryNamesStr.isBlank())
            throw new BusinessException("知识条目必须关联至少一个分类");

        title = title.trim();
        content = content.trim();

        // 标签解析（逗号分隔）
        List<String> tags = List.of();
        if (tagsStr != null && !tagsStr.isBlank()) {
            tags = List.of(tagsStr.trim().split("\\s*,\\s*"));
            if (tags.size() == 1 && tags.get(0).isEmpty()) {
                tags = List.of();
            }
        }

        // 分类名称解析（逗号分隔 → 查 name→id 映射）
        List<Long> categoryIds = new ArrayList<>();
        for (String name : categoryNamesStr.trim().split("\\s*,\\s*")) {
            if (name.isBlank()) continue;
            Long id = categoryNameToId.get(name);
            if (id == null) {
                throw new BusinessException("分类名称不存在或已停用：" + name);
            }
            categoryIds.add(id);
        }
        if (categoryIds.isEmpty()) {
            throw new BusinessException("知识条目必须关联至少一个分类");
        }

        // 状态解析（缺失默认 ACTIVE）
        KnowledgeItemStatus status = KnowledgeItemStatus.ACTIVE;
        if (statusStr != null && !statusStr.isBlank()) {
            statusStr = statusStr.trim();
            if ("启用".equals(statusStr)) {
                status = KnowledgeItemStatus.ACTIVE;
            } else if ("停用".equals(statusStr)) {
                status = KnowledgeItemStatus.INACTIVE;
            } else {
                throw new BusinessException("状态格式错误：" + statusStr + "，可选值 启用/停用");
            }
        }

        // 排序解析（缺失默认 0）
        int sortOrder = 0;
        if (sortOrderStr != null && !sortOrderStr.isBlank()) {
            try {
                sortOrder = Integer.parseInt(sortOrderStr.trim());
            } catch (NumberFormatException e) {
                throw new BusinessException("排序值格式错误：" + sortOrderStr.trim() + "，必须为整数");
            }
        }

        return new ImportRowData(title, content, tags, categoryIds, status, sortOrder);
    }

    /**
     * 导入单条知识条目
     */
    private void importOne(ImportRowData data) {
        String contentHtml = markdownRenderer.render(data.content);
        KnowledgeItem item = itemDomainService.validateAndCreate(
                data.title, data.content, null, data.tags, data.sortOrder, data.categoryIds);
        item.updateContentHtml(contentHtml);
        if (data.status == KnowledgeItemStatus.INACTIVE) {
            item.deactivate();
        }
        KnowledgeItem saved = itemRepository.save(item);
        itemRepository.saveCategoryRelations(saved.getId(), data.categoryIds);
    }

    /**
     * 获取单元格字符串值
     */
    private String getCellStringValue(Row row, int colIndex) {
        Cell cell = row.getCell(colIndex);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> numericCellToString(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                yield switch (cell.getCachedFormulaResultType()) {
                    case STRING -> cell.getStringCellValue();
                    case NUMERIC -> numericCellToString(cell.getNumericCellValue());
                    case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
                    default -> null;
                };
            }
            default -> null;
        };
    }

    /**
     * 将数值单元格转为字符串（整数去掉 .0 尾缀，小数保留原始值）
     */
    private String numericCellToString(double val) {
        if (val == Math.floor(val) && !Double.isInfinite(val)) {
            return String.valueOf((long) val);
        }
        return String.valueOf(val);
    }

    /**
     * 判断行是否为空（所有 6 列均无值）
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
            String title,
            String content,
            List<String> tags,
            List<Long> categoryIds,
            KnowledgeItemStatus status,
            int sortOrder) {
    }

    /**
     * 校验分类 ID 列表：必须存在且 ACTIVE
     */
    private void validateCategoryIds(List<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return;
        }
        for (Long categoryId : categoryIds) {
            categoryRepositoryPort.findById(categoryId)
                    .filter(c -> c.getStatus() == KnowledgeCategoryStatus.ACTIVE)
                    .orElseThrow(() -> new BusinessException("分类不存在或已停用: " + categoryId));
        }
    }


    /**
     * 查询条目关联分类并组装响应
     */
    private KnowledgeItemResponse toResponseWithCategories(KnowledgeItem item) {
        List<Long> categoryIds = itemRepository.findActiveCategoryIdsByItemId(item.getId());
        return KnowledgeItemAssembler.INSTANCE.toResponse(item, categoryIds);
    }

    /**
     * 校验 fileId 对应文件的 metadata，用 file 服务返回的 url 组装 FileRef
     */
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
