package com.knowledgegame.admin.application.service;

import com.knowledgegame.admin.api.assembler.QuestionAssembler;
import com.knowledgegame.admin.api.dto.request.CreateQuestionRequest;
import com.knowledgegame.admin.api.dto.request.UpdateQuestionRequest;
import com.knowledgegame.admin.api.dto.response.ImportFailDetail;
import com.knowledgegame.admin.api.dto.response.QuestionImportResult;
import com.knowledgegame.admin.api.dto.response.QuestionResponse;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.common.util.EnumUtils;
import com.knowledgegame.core.domain.model.domainenum.Difficulty;
import com.knowledgegame.core.domain.model.domainenum.KnowledgeCategoryStatus;
import com.knowledgegame.core.domain.model.domainenum.QuestionStatus;
import com.knowledgegame.core.domain.model.domainenum.QuestionType;
import com.knowledgegame.core.domain.model.entity.Question;
import com.knowledgegame.core.domain.model.vo.PageResult;
import com.knowledgegame.core.domain.model.vo.QuestionOption;
import com.knowledgegame.core.domain.model.vo.SortField;
import com.knowledgegame.core.domain.port.outbound.KnowledgeCategoryRepositoryPort;
import com.knowledgegame.core.domain.port.outbound.QuestionRepository;
import com.knowledgegame.core.domain.service.QuestionDomainService;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 题目管理端应用服务（流程编排 + 事务，返回 DTO）
 */
@Service
public class QuestionAppService {

    private final QuestionRepository questionRepository;
    private final QuestionDomainService questionDomainService;
    private final KnowledgeCategoryRepositoryPort categoryRepositoryPort;

    public QuestionAppService(QuestionRepository questionRepository,
                               QuestionDomainService questionDomainService,
                               KnowledgeCategoryRepositoryPort categoryRepositoryPort) {
        this.questionRepository = questionRepository;
        this.questionDomainService = questionDomainService;
        this.categoryRepositoryPort = categoryRepositoryPort;
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
        SortField sortField = buildSortField(sort, order);

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
     */
    @Transactional
    public QuestionResponse update(Long id, UpdateQuestionRequest request) {
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new BusinessException("题目不存在: " + id));

        List<QuestionOption> options = request.getOptions() != null
                ? toQuestionOptions(request.getOptions()) : null;
        Difficulty difficulty = request.getDifficulty() != null
                ? Difficulty.fromLevel(request.getDifficulty()) : null;

        questionDomainService.validateUpdate(question, request.getContent(),
                options, request.getAnswer(), request.getTags());

        question.update(request.getContent(), options, request.getAnswer(),
                difficulty, request.getExplanation(), request.getTags());
        Question saved = questionRepository.save(question);

        return toResponseWithCategories(saved);
    }

    /**
     * 删除题目（软删除）
     */
    @Transactional
    public void delete(Long id) {
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new BusinessException("题目不存在: " + id));
        question.deactivate();
        questionRepository.save(question);
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
     * 批量启用
     */
    @Transactional
    public void batchActivate(List<Long> ids) {
        questionRepository.batchUpdateStatus(ids, QuestionStatus.ACTIVE);
    }

    /**
     * 批量禁用
     */
    @Transactional
    public void batchDeactivate(List<Long> ids) {
        questionRepository.batchUpdateStatus(ids, QuestionStatus.INACTIVE);
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
     * 构建排序字段
     */
    private SortField buildSortField(String sort, String order) {
        if (sort == null || sort.isBlank()) {
            return SortField.defaultSort();
        }
        SortField.Direction direction = "asc".equalsIgnoreCase(order)
                ? SortField.Direction.ASC : SortField.Direction.DESC;
        return new SortField(sort, direction);
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
                    "正确答案", "难度", "解析", "标签", "分类ID"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            Row exampleRow = sheet.createRow(1);
            exampleRow.createCell(0).setCellValue("单选");
            exampleRow.createCell(1).setCellValue("Java中哪个关键字用于继承？");
            exampleRow.createCell(2).setCellValue("extends");
            exampleRow.createCell(3).setCellValue("implements");
            exampleRow.createCell(4).setCellValue("inherits");
            exampleRow.createCell(5).setCellValue("super");
            exampleRow.createCell(6).setCellValue("A");
            exampleRow.createCell(7).setCellValue("中等");
            exampleRow.createCell(8).setCellValue("extends用于类继承");
            exampleRow.createCell(9).setCellValue("Java,面向对象");
            exampleRow.createCell(10).setCellValue("1,3");

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

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            int totalCount = sheet.getLastRowNum();

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    ImportRowData data = parseRow(row);
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
    private ImportRowData parseRow(Row row) {
        String typeStr = getCellStringValue(row, 0);
        String content = getCellStringValue(row, 1);
        String optionA = getCellStringValue(row, 2);
        String optionB = getCellStringValue(row, 3);
        String optionC = getCellStringValue(row, 4);
        String optionD = getCellStringValue(row, 5);
        String answer = getCellStringValue(row, 6);
        String difficultyStr = getCellStringValue(row, 7);
        String explanation = getCellStringValue(row, 8);
        String tagsStr = getCellStringValue(row, 9);
        String categoryIdsStr = getCellStringValue(row, 10);

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
        if (categoryIdsStr != null && !categoryIdsStr.isBlank()) {
            try {
                categoryIds = Arrays.stream(categoryIdsStr.split(","))
                        .map(String::trim).filter(s -> !s.isEmpty())
                        .map(Long::parseLong).toList();
            } catch (NumberFormatException e) {
                throw new BusinessException("分类ID格式错误: " + categoryIdsStr);
            }
        }

        return new ImportRowData(type, content, options, finalAnswer, difficulty,
                explanation, tags, categoryIds);
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
