package com.knowledgegame.admin.api.controller;

import com.knowledgegame.admin.api.dto.request.BatchSortRequest;
import com.knowledgegame.admin.api.dto.request.BatchStatusRequest;
import com.knowledgegame.admin.api.dto.request.CreateKnowledgeItemRequest;
import com.knowledgegame.admin.api.dto.request.KnowledgeItemCategoryUpdateRequest;
import com.knowledgegame.admin.api.dto.request.UpdateKnowledgeItemRequest;
import com.knowledgegame.admin.api.dto.response.KnowledgeItemImportResult;
import com.knowledgegame.admin.api.dto.response.KnowledgeItemResponse;
import com.knowledgegame.admin.application.service.KnowledgeItemAppService;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 知识条目管理端 Controller（仅参数接收 + 结果返回，无业务逻辑）
 */
@RestController
@RequestMapping("/api/admin/knowledge-items")
public class KnowledgeItemController {

    private final KnowledgeItemAppService appService;

    public KnowledgeItemController(KnowledgeItemAppService appService) {
        this.appService = appService;
    }

    /**
     * 创建知识条目
     */
    @PostMapping
    public Result<KnowledgeItemResponse> create(@Valid @RequestBody CreateKnowledgeItemRequest request) {
        KnowledgeItemResponse response = appService.create(request);
        return Result.success(response);
    }

    /**
     * 查询知识条目详情
     */
    @GetMapping("/{id}")
    public Result<KnowledgeItemResponse> getById(@PathVariable Long id) {
        KnowledgeItemResponse response = appService.getById(id);
        return Result.success(response);
    }

    /**
     * 分页查询知识条目列表
     */
    @GetMapping
    public Result<PageResult<KnowledgeItemResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String order,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResult<KnowledgeItemResponse> result = appService.list(
                keyword, categoryId, tag, status, sort, order, page, size);
        return Result.success(result);
    }

    /**
     * 更新知识条目
     */
    @PutMapping("/{id}")
    public Result<KnowledgeItemResponse> update(@PathVariable Long id,
                                                  @Valid @RequestBody UpdateKnowledgeItemRequest request) {
        KnowledgeItemResponse response = appService.update(id, request);
        return Result.success(response);
    }

    /**
     * 删除知识条目（软删除，含分类校验）
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        appService.delete(id);
        return Result.success();
    }

    /**
     * 查询知识条目关联的分类列表
     */
    @GetMapping("/{id}/categories")
    public Result<List<Long>> getCategories(@PathVariable Long id) {
        List<Long> categoryIds = appService.getCategoryIds(id);
        return Result.success(categoryIds);
    }

    /**
     * 更新知识条目的分类关联（全量替换）
     */
    @PutMapping("/{id}/categories")
    public Result<Void> updateCategories(@PathVariable Long id,
                                           @Valid @RequestBody KnowledgeItemCategoryUpdateRequest request) {
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
     * 批量排序
     */
    @PutMapping("/batch-sort")
    public Result<Void> batchSort(@Valid @RequestBody BatchSortRequest request) {
        appService.batchSort(request);
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
     * Excel 批量导入知识条目
     */
    @PostMapping("/import")
    public Result<KnowledgeItemImportResult> importExcel(@RequestParam("file") MultipartFile file) throws IOException {
        KnowledgeItemImportResult result = appService.importExcel(file);
        return Result.success(result);
    }

    /**
     * Markdown zip 批量导入知识条目
     */
    @PostMapping("/import-markdown")
    public Result<KnowledgeItemImportResult> importMarkdownZip(@RequestParam("file") MultipartFile file) throws IOException {
        KnowledgeItemImportResult result = appService.importMarkdownZip(file);
        return Result.success(result);
    }
}
