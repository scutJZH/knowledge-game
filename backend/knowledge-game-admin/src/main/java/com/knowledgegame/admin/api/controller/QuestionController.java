package com.knowledgegame.admin.api.controller;

import com.knowledgegame.admin.api.dto.request.BatchStatusRequest;
import com.knowledgegame.admin.api.dto.request.CreateQuestionRequest;
import com.knowledgegame.admin.api.dto.request.QuestionCategoryUpdateRequest;
import com.knowledgegame.admin.api.dto.request.UpdateQuestionRequest;
import com.knowledgegame.admin.api.dto.response.QuestionImportResult;
import com.knowledgegame.admin.api.dto.response.QuestionResponse;
import com.knowledgegame.admin.application.service.QuestionAppService;
import com.knowledgegame.core.common.result.Result;
import com.knowledgegame.core.domain.model.vo.PageResult;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 题目管理端 Controller（仅参数接收 + 结果返回，无业务逻辑）
 */
@RestController
@RequestMapping("/api/admin/questions")
public class QuestionController {

    private final QuestionAppService appService;

    public QuestionController(QuestionAppService appService) {
        this.appService = appService;
    }

    /**
     * 创建题目
     */
    @PostMapping
    public Result<QuestionResponse> create(@Valid @RequestBody CreateQuestionRequest request) {
        QuestionResponse response = appService.create(request);
        return Result.success(response);
    }

    /**
     * 查询题目详情
     */
    @GetMapping("/{id}")
    public Result<QuestionResponse> getById(@PathVariable Long id) {
        QuestionResponse response = appService.getById(id);
        return Result.success(response);
    }

    /**
     * 分页查询题目列表
     */
    @GetMapping
    public Result<PageResult<QuestionResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String order,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResult<QuestionResponse> result = appService.list(
                keyword, type, difficulty, categoryId, tag, status,
                sort, order, page, size);
        return Result.success(result);
    }

    /**
     * 更新题目
     */
    @PutMapping("/{id}")
    public Result<QuestionResponse> update(@PathVariable Long id,
                                             @Valid @RequestBody UpdateQuestionRequest request) {
        QuestionResponse response = appService.update(id, request);
        return Result.success(response);
    }

    /**
     * 删除题目（软删除）
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        appService.delete(id);
        return Result.success();
    }

    /**
     * 查询题目关联的分类列表
     */
    @GetMapping("/{id}/categories")
    public Result<List<Long>> getCategories(@PathVariable Long id) {
        List<Long> categoryIds = appService.getCategoryIds(id);
        return Result.success(categoryIds);
    }

    /**
     * 更新题目的分类关联（全量替换）
     */
    @PutMapping("/{id}/categories")
    public Result<Void> updateCategories(@PathVariable Long id,
                                           @Valid @RequestBody QuestionCategoryUpdateRequest request) {
        appService.updateCategories(id, request.getCategoryIds());
        return Result.success();
    }

    /**
     * 批量启用
     */
    @PutMapping("/batch-activate")
    public Result<Void> batchActivate(@Valid @RequestBody BatchStatusRequest request) {
        appService.batchActivate(request.getIds());
        return Result.success();
    }

    /**
     * 批量禁用
     */
    @PutMapping("/batch-deactivate")
    public Result<Void> batchDeactivate(@Valid @RequestBody BatchStatusRequest request) {
        appService.batchDeactivate(request.getIds());
        return Result.success();
    }

    /**
     * 下载导入模板
     */
    @GetMapping("/import-template")
    public void downloadImportTemplate(HttpServletResponse response) throws IOException {
        appService.downloadImportTemplate(response);
    }

    /**
     * 导入题目
     */
    @PostMapping("/import")
    public Result<QuestionImportResult> importQuestions(@RequestPart MultipartFile file) throws IOException {
        QuestionImportResult result = appService.importQuestions(file);
        return Result.success(result);
    }
}
