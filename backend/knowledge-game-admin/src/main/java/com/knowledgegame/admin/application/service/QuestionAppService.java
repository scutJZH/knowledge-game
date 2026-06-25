package com.knowledgegame.admin.application.service;

import com.knowledgegame.admin.api.assembler.QuestionAssembler;
import com.knowledgegame.admin.api.dto.request.CreateQuestionRequest;
import com.knowledgegame.admin.api.dto.request.UpdateQuestionRequest;
import com.knowledgegame.admin.api.dto.response.ImportFailDetail;
import com.knowledgegame.admin.api.dto.response.QuestionImportResult;
import com.knowledgegame.admin.api.dto.response.QuestionResponse;
import com.knowledgegame.auth.security.SecurityUtils;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.common.util.EnumUtils;
import com.knowledgegame.core.domain.model.domainenum.Difficulty;
import com.knowledgegame.core.domain.model.domainenum.KnowledgeCategoryStatus;
import com.knowledgegame.core.domain.model.domainenum.QuestionStatus;
import com.knowledgegame.core.domain.model.domainenum.QuestionType;
import com.knowledgegame.core.domain.model.entity.KnowledgeCategory;
import com.knowledgegame.core.domain.model.entity.Question;
import com.knowledgegame.core.domain.model.vo.PageResult;
import com.knowledgegame.core.domain.model.vo.QuestionOption;
import com.knowledgegame.core.domain.model.vo.SortField;
import com.knowledgegame.core.domain.port.outbound.KnowledgeCategoryRepositoryPort;
import com.knowledgegame.core.domain.port.outbound.QuestionRepository;
import com.knowledgegame.core.domain.service.QuestionDomainService;
import com.knowledgegame.core.domain.service.recyclebin.RecycleBinItemStrategy;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 题目管理端应用服务（流程编排 + 事务，返回 DTO）
 */
@Service
public class QuestionAppService {

    private final QuestionRepository questionRepository;
    private final QuestionDomainService questionDomainService;
    private final KnowledgeCategoryRepositoryPort categoryRepositoryPort;
    private final RecycleBinItemStrategy<Question> recycleBinStrategy;

    public QuestionAppService(QuestionRepository questionRepository,
                               QuestionDomainService questionDomainService,
                               KnowledgeCategoryRepositoryPort categoryRepositoryPort,
                               RecycleBinItemStrategy<Question> recycleBinStrategy) {
        this.questionRepository = questionRepository;
        this.questionDomainService = questionDomainService;
        this.categoryRepositoryPort = categoryRepositoryPort;
        this.recycleBinStrategy = recycleBinStrategy;
    }

    /**
     * 创建题目
     */
    @Transactional
    public QuestionResponse create(CreateQuestionRequest request) {
        QuestionType type = EnumUtils.valueOf(QuestionType.class, request.getType());
        List<QuestionOption> options = toQuestionOptions(request.getOptions());
        Difficulty difficulty = Difficulty.fromLevel(request.getDifficulty());

        Question question = questionDomainService.validateAndCreate(
                type, request.getContent(), options, request.getAnswer(),
                difficulty, request.getExplanation(), request.getTags()
        );
        Question saved = questionRepository.save(question);

        if (request.getCategoryIds() != null && !request.getCategoryIds().isEmpty()) {
            validateCategoryIds(request.getCategoryIds());
            questionRepository.saveCategoryRelations(saved.getId(), request.getCategoryIds());
        }

        return toResponseWithCategories(saved);
    }

    /**
     * 查询详情
     */
    public QuestionResponse getById(Long id) {
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new BusinessException("题目不存在: " + id));
        return toResponseWithCategories(question);
    }

    /**
     * 分页查询
     */
    public PageResult<QuestionResponse> list(String keyword, String type, Integer difficulty,
                                               Long categoryId, String tag, String status,
                                               String sort, String order, int page, int size) {
        QuestionType typeEnum = EnumUtils.valueOfNullable(QuestionType.class, type);
        QuestionStatus statusEnum = EnumUtils.valueOfNullable(QuestionStatus.class, status);
        SortField sortField = SortField.parse(sort, order);

        PageResult<Question> domainPage = questionRepository.findByConditions(
                keyword, typeEnum, difficulty, categoryId, tag, statusEnum,
                sortField, page, size
        );

        return PageResult.<QuestionResponse>builder()
                .content(domainPage.getContent().stream()
                        .map(QuestionAssembler.INSTANCE::toResponse).toList())
                .totalElements(domainPage.getTotalElements())
                .pageNumber(domainPage.getPageNumber())
                .pageSize(domainPage.getPageSize())
                .totalPages(domainPage.getTotalPages())
                .build();
    }

    /**
     * 更新题目
     * <p>
     * 接收整个 Request DTO（不逐字段拆包），以便解析 JsonNullable 三态：
     * - undefined：不更新
     * - of(null)：清空
     * - of(value)：更新为新值
     * <p>
     * 注意：分类关联（categoryIds）通过独立的 PUT /questions/{id}/categories 接口更新，不在本方法处理。
     */
    @Transactional
    public QuestionResponse update(Long id, UpdateQuestionRequest request) {
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new BusinessException("题目不存在: " + id));

        List<QuestionOption> options = request.getOptions() != null
                ? toQuestionOptions(request.getOptions()) : null;
        Difficulty difficulty = request.getDifficulty() != null
                ? Difficulty.fromLevel(request.getDifficulty()) : null;

        // tags 仅在 present 时才需校验（undefined 跳过；null 表示清空，无需校验内容）
        List<String> tagsForValidation = request.getTags().isPresent() ? request.getTags().get() : null;
        questionDomainService.validateUpdate(question, request.getContent(),
                options, request.getAnswer(), tagsForValidation);

        // 必填字段：null=不更新
        question.update(request.getContent(), options, request.getAnswer(), difficulty);

        // 可清空字段：三态分派
        applyField(request.getExplanation(), question::clearExplanation, question::updateExplanation);
        applyField(request.getTags(), question::clearTags, question::updateTags);

        Question saved = questionRepository.save(question);

        return toResponseWithCategories(saved);
    }

    /**
     * 三态分派工具：处理 JsonNullable<T> 字段
     * <p>
     * - undefined：跳过（不更新）
     * - of(null)：调用 clear
     * - of(value)：调用 update
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
     * 删除题目（移入回收站）
     */
    @Transactional
    public void delete(Long id) {
        recycleBinStrategy.validateDeletable(id);
        recycleBinStrategy.moveToRecycleBin(id, SecurityUtils.getCurrentUsername());
    }

    /**
     * 查询题目关联的分类 ID 列表
     */
    public List<Long> getCategoryIds(Long questionId) {
        if (!questionRepository.findById(questionId).isPresent()) {
            throw new BusinessException("题目不存在: " + questionId);
        }
        return questionRepository.findActiveCategoryIdsByQuestionId(questionId);
    }

    /**
     * 更新题目的分类关联（全量替换）
     */
    @Transactional
    public void updateCategories(Long questionId, List<Long> categoryIds) {
        if (!questionRepository.findById(questionId).isPresent()) {
            throw new BusinessException("题目不存在: " + questionId);
        }
        validateCategoryIds(categoryIds);
        questionRepository.saveCategoryRelations(questionId, categoryIds);
    }

    /**
     * 批量启用（含分类状态前置校验）
     */
    @Transactional
    public void batchActivate(List<Long> ids) {
        // ids 非空由 BatchStatusRequest.@NotEmpty 在 DTO 层保证
        // 去重避免 size 校验误报
        List<Long> distinctIds = ids.stream().distinct().toList();
        // 存在性校验：与 CardTemplate 行为对称，避免静默成功
        List<Question> questions = questionRepository.findByIds(distinctIds);
        if (questions.size() != distinctIds.size()) {
            throw new BusinessException("部分题目 ID 不存在");
        }
        // 加载题目信息用于错误消息中的题目名
        Map<Long, String> idToName = questions.stream()
                .collect(Collectors.toMap(Question::getId, Question::getContent));
        // 查询所有题目的分类关联
        Map<Long, List<Long>> questionToCategoryIds = questionRepository.findCategoryIdsByQuestionIds(distinctIds);
        // 收集全部唯一分类 ID，一次性批量加载
        List<Long> allCategoryIds = questionToCategoryIds.values().stream()
                .flatMap(List::stream)
                .distinct()
                .toList();
        Map<Long, KnowledgeCategory> categoryMap = allCategoryIds.isEmpty()
                ? Map.of()
                : categoryRepositoryPort.findAllByIdIn(allCategoryIds).stream()
                        .collect(Collectors.toMap(KnowledgeCategory::getId, c -> c));
        // 逐题校验（纯内存），第一个失败抛异常
        for (Long id : distinctIds) {
            String questionName = idToName.getOrDefault(id, "(ID=" + id + ")");
            List<Long> categoryIds = questionToCategoryIds.getOrDefault(id, List.of());
            questionDomainService.validateActivatable(questionName, categoryIds, categoryMap);
        }
        // 全部通过则执行
        questionRepository.batchUpdateStatus(distinctIds, QuestionStatus.ACTIVE);
    }

    /**
     * 批量禁用
     */
    @Transactional
    public void batchDeactivate(List<Long> ids) {
        // ids 非空由 DTO 层保证；去重与 batchActivate 对称
        List<Long> distinctIds = ids.stream().distinct().toList();
        questionRepository.batchUpdateStatus(distinctIds, QuestionStatus.INACTIVE);
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
     * 选项 DTO 转领域值对象
     */
    private List<QuestionOption> toQuestionOptions(List<CreateQuestionRequest.OptionItem> optionItems) {
        if (optionItems == null) {
            return null;
        }
        return optionItems.stream()
                .map(item -> QuestionOption.of(item.getKey(), item.getContent()))
                .toList();
    }

    /**
     * 领域模型转响应 DTO（含分类 ID 列表）
     */
    private QuestionResponse toResponseWithCategories(Question question) {
        QuestionResponse response = QuestionAssembler.INSTANCE.toResponse(question);
        List<Long> categoryIds = questionRepository.findActiveCategoryIdsByQuestionId(question.getId());
        return QuestionResponse.builder()
                .id(response.getId())
                .type(response.getType())
                .content(response.getContent())
                .options(response.getOptions())
                .answer(response.getAnswer())
                .explanation(response.getExplanation())
                .difficulty(response.getDifficulty())
                .tags(response.getTags())
                .status(response.getStatus())
                .categoryIds(categoryIds)
                .createdAt(response.getCreatedAt())
                .updatedAt(response.getUpdatedAt())
                .build();
    }

    /**
     * 下载导入模板
     */
    public void downloadImportTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=question_import_template.xlsx");

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("题目导入");

            String[] headers = {"题型", "题目内容", "选项A", "选项B", "选项C", "选项D",
                    "选项E", "选项F", "正确答案", "难度", "解析", "标签", "分类路径(可选)"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            // 单选示例
            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("单选");
            row1.createCell(1).setCellValue("Java中哪个关键字用于继承？");
            row1.createCell(2).setCellValue("extends");
            row1.createCell(3).setCellValue("implements");
            row1.createCell(4).setCellValue("inherits");
            row1.createCell(5).setCellValue("super");
            row1.createCell(8).setCellValue("A");
            row1.createCell(9).setCellValue("中等");
            row1.createCell(10).setCellValue("extends用于类继承");
            row1.createCell(11).setCellValue("Java,面向对象");
            row1.createCell(12).setCellValue("编程语言/Java");

            // 多选示例
            Row row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue("多选");
            row2.createCell(1).setCellValue("以下哪些是Java的基本数据类型？");
            row2.createCell(2).setCellValue("int");
            row2.createCell(3).setCellValue("String");
            row2.createCell(4).setCellValue("boolean");
            row2.createCell(5).setCellValue("Array");
            row2.createCell(6).setCellValue("float");
            row2.createCell(8).setCellValue("A,C,E");
            row2.createCell(9).setCellValue("简单");
            row2.createCell(10).setCellValue("int、boolean和float是基本类型，String和Array是引用类型");
            row2.createCell(11).setCellValue("Java,基础");
            row2.createCell(12).setCellValue("编程语言/Java");

            // 判断示例
            Row row3 = sheet.createRow(3);
            row3.createCell(0).setCellValue("判断");
            row3.createCell(1).setCellValue("Java是一种纯编译型语言。");
            row3.createCell(8).setCellValue("错");
            row3.createCell(9).setCellValue("简单");
            row3.createCell(10).setCellValue("Java是先编译后解释执行的，不是纯编译型");
            row3.createCell(11).setCellValue("Java,基础");
            row3.createCell(12).setCellValue("编程语言");

            // 填空示例
            Row row4 = sheet.createRow(4);
            row4.createCell(0).setCellValue("填空");
            row4.createCell(1).setCellValue("Java的创始人全名是？");
            row4.createCell(8).setCellValue("James");
            row4.createCell(9).setCellValue("简单");
            row4.createCell(10).setCellValue("James Gosling（詹姆斯·高斯林）");
            row4.createCell(11).setCellValue("Java,人物");
            row4.createCell(12).setCellValue("编程语言");

            // 填写说明
            Row tipRow = sheet.createRow(5);
            tipRow.createCell(0).setCellValue("【填写说明】");
            tipRow.createCell(1).setCellValue("题型：单选/多选/判断/填空；答案：单选填选项字母(A)，多选填选项字母逗号分隔(A,C)，判断填对/错，填空填关键词逗号分隔；难度：简单/中等/困难；分类路径：从顶级分类起用/分隔，多个分类用逗号分隔，如无分类可留空");

            // 合并填写说明单元格使其可读
            sheet.addMergedRegion(new CellRangeAddress(5, 5, 1, 12));

            workbook.write(response.getOutputStream());
        }
    }

    /**
     * 导入题目
     */
    @Transactional
    public QuestionImportResult importQuestions(MultipartFile file) throws IOException {
        List<ImportFailDetail> failDetails = new ArrayList<>();
        int successCount = 0;

        // 一次性构建分类路径→ID映射，避免逐行查库
        Map<String, Long> pathToIdMap = buildCategoryPathMap();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            int totalCount = sheet.getLastRowNum();

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                // 跳过填写说明行（首列为提示文本）
                String firstCellValue = getCellStringValue(row, 0);
                if (firstCellValue != null && firstCellValue.startsWith("【")) continue;

                try {
                    ImportRowData data = parseRow(row, pathToIdMap);
                    importOneQuestion(data);
                    successCount++;
                } catch (BusinessException e) {
                    failDetails.add(ImportFailDetail.builder()
                            .row(i + 1).reason(e.getMessage()).build());
                }
            }

            return QuestionImportResult.builder()
                    .totalCount(totalCount)
                    .successCount(successCount)
                    .failCount(failDetails.size())
                    .failDetails(failDetails)
                    .build();
        }
    }

    /**
     * 解析一行导入数据
     */
    private ImportRowData parseRow(Row row, Map<String, Long> pathToIdMap) {
        String typeStr = getCellStringValue(row, 0);
        String content = getCellStringValue(row, 1);
        String optionA = getCellStringValue(row, 2);
        String optionB = getCellStringValue(row, 3);
        String optionC = getCellStringValue(row, 4);
        String optionD = getCellStringValue(row, 5);
        String optionE = getCellStringValue(row, 6);
        String optionF = getCellStringValue(row, 7);
        String answer = getCellStringValue(row, 8);
        String difficultyStr = getCellStringValue(row, 9);
        String explanation = getCellStringValue(row, 10);
        String tagsStr = getCellStringValue(row, 11);
        String categoryPathsStr = getCellStringValue(row, 12);

        if (typeStr == null || typeStr.isBlank()) throw new BusinessException("题型不能为空");
        if (content == null || content.isBlank()) throw new BusinessException("题目内容不能为空");
        if (answer == null || answer.isBlank()) throw new BusinessException("正确答案不能为空");
        if (difficultyStr == null || difficultyStr.isBlank()) throw new BusinessException("难度不能为空");

        QuestionType type = switch (typeStr.trim()) {
            case "单选" -> QuestionType.SINGLE_CHOICE;
            case "多选" -> QuestionType.MULTIPLE_CHOICE;
            case "判断" -> QuestionType.TRUE_FALSE;
            case "填空" -> QuestionType.FILL_BLANK;
            default -> throw new BusinessException("题型格式错误，可选值：单选/多选/判断/填空");
        };

        Difficulty difficulty = switch (difficultyStr.trim()) {
            case "简单" -> Difficulty.EASY;
            case "中等" -> Difficulty.MEDIUM;
            case "困难" -> Difficulty.HARD;
            default -> throw new BusinessException("难度格式错误，可选值：简单/中等/困难");
        };

        List<QuestionOption> options = null;
        if (type == QuestionType.SINGLE_CHOICE || type == QuestionType.MULTIPLE_CHOICE) {
            List<QuestionOption> optList = new ArrayList<>();
            if (optionA != null && !optionA.isBlank()) optList.add(QuestionOption.of("A", optionA));
            if (optionB != null && !optionB.isBlank()) optList.add(QuestionOption.of("B", optionB));
            if (optionC != null && !optionC.isBlank()) optList.add(QuestionOption.of("C", optionC));
            if (optionD != null && !optionD.isBlank()) optList.add(QuestionOption.of("D", optionD));
            if (optionE != null && !optionE.isBlank()) optList.add(QuestionOption.of("E", optionE));
            if (optionF != null && !optionF.isBlank()) optList.add(QuestionOption.of("F", optionF));
            options = optList;
        }

        String finalAnswer;
        if (type == QuestionType.TRUE_FALSE) {
            finalAnswer = switch (answer.trim()) {
                case "对", "正确", "true" -> "true";
                case "错", "错误", "false" -> "false";
                default -> throw new BusinessException("判断题答案必须为 对/错");
            };
        } else if (type == QuestionType.MULTIPLE_CHOICE) {
            String[] parts = answer.split(",");
            StringBuilder sb = new StringBuilder("[");
            for (int j = 0; j < parts.length; j++) {
                if (j > 0) sb.append(",");
                sb.append("\"").append(parts[j].trim()).append("\"");
            }
            sb.append("]");
            finalAnswer = sb.toString();
        } else if (type == QuestionType.FILL_BLANK) {
            String[] parts = answer.split(",");
            StringBuilder sb = new StringBuilder("[");
            for (int j = 0; j < parts.length; j++) {
                if (j > 0) sb.append(",");
                sb.append("\"").append(parts[j].trim()).append("\"");
            }
            sb.append("]");
            finalAnswer = sb.toString();
        } else {
            finalAnswer = answer.trim();
        }

        List<String> tags = null;
        if (tagsStr != null && !tagsStr.isBlank()) {
            tags = Arrays.stream(tagsStr.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).toList();
        }

        List<Long> categoryIds = null;
        if (categoryPathsStr != null && !categoryPathsStr.isBlank()) {
            categoryIds = new ArrayList<>();
            String[] paths = categoryPathsStr.split(",");
            for (String path : paths) {
                String trimmedPath = path.trim();
                if (trimmedPath.isEmpty()) continue;
                Long categoryId = pathToIdMap.get(trimmedPath);
                if (categoryId == null) {
                    throw new BusinessException("分类路径不存在: " + trimmedPath);
                }
                categoryIds.add(categoryId);
            }
        }

        return new ImportRowData(type, content, options, finalAnswer, difficulty,
                explanation, tags, categoryIds);
    }

    /**
     * 构建分类路径到ID的映射（全路径 → 分类ID）
     * <p>
     * 路径格式：从顶级分类起，用"/"分隔各级名称。例如"编程语言/Java"对应id=5。
     * 加载所有ACTIVE分类后按parentId递归构建路径。
     */
    private Map<String, Long> buildCategoryPathMap() {
        List<KnowledgeCategory> allCategories = categoryRepositoryPort.findAll();
        Map<Long, KnowledgeCategory> idToCategory = new HashMap<>();
        for (KnowledgeCategory c : allCategories) {
            idToCategory.put(c.getId(), c);
        }

        Map<String, Long> pathToId = new HashMap<>();
        for (KnowledgeCategory category : allCategories) {
            if (category.getStatus() != KnowledgeCategoryStatus.ACTIVE) continue;
            String path = buildCategoryPath(category, idToCategory);
            pathToId.put(path, category.getId());
        }
        return pathToId;
    }

    /**
     * 递归构建单个分类的完整路径（从根到当前节点）
     */
    private String buildCategoryPath(KnowledgeCategory category,
                                     Map<Long, KnowledgeCategory> idToCategory) {
        List<String> names = new ArrayList<>();
        KnowledgeCategory current = category;
        while (current != null) {
            names.addFirst(current.getName());
            Long parentId = current.getParentId();
            current = parentId != null ? idToCategory.get(parentId) : null;
        }
        return String.join("/", names);
    }

    /**
     * 导入单个题目
     */
    private void importOneQuestion(ImportRowData data) {
        Question question = questionDomainService.validateAndCreate(
                data.type, data.content, data.options, data.answer,
                data.difficulty, data.explanation, data.tags
        );
        Question saved = questionRepository.save(question);

        if (data.categoryIds != null && !data.categoryIds.isEmpty()) {
            validateCategoryIds(data.categoryIds);
            questionRepository.saveCategoryRelations(saved.getId(), data.categoryIds);
        }
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
            default -> null;
        };
    }

    /**
     * 导入行数据内部记录
     */
    private record ImportRowData(
            QuestionType type,
            String content,
            List<QuestionOption> options,
            String answer,
            Difficulty difficulty,
            String explanation,
            List<String> tags,
            List<Long> categoryIds) {
    }
}
