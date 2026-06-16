package com.knowledgegame.admin.api.controller;

import com.knowledgegame.admin.api.dto.request.BatchSortRequest;
import com.knowledgegame.admin.api.dto.request.CreateKnowledgeCategoryRequest;
import com.knowledgegame.admin.api.dto.request.MoveKnowledgeCategoryRequest;
import com.knowledgegame.admin.api.dto.request.UpdateKnowledgeCategoryRequest;
import com.knowledgegame.admin.api.dto.response.KnowledgeCategoryResponse;
import com.knowledgegame.admin.api.dto.response.KnowledgeCategoryTreeResponse;
import com.knowledgegame.admin.application.service.KnowledgeCategoryAppService;
import com.knowledgegame.core.common.result.Result;
import com.knowledgegame.core.domain.model.vo.PageResult;
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

import java.util.List;

/**
 * 知识点分类管理端 Controller（仅参数接收 + 结果返回，无业务逻辑）
 */
@RestController
@RequestMapping("/api/admin/knowledge-categories")
public class KnowledgeCategoryController {

    private final KnowledgeCategoryAppService appService;

    public KnowledgeCategoryController(KnowledgeCategoryAppService appService) {
        this.appService = appService;
    }

    /**
     * 创建知识点分类
     */
    @PostMapping
    public Result<KnowledgeCategoryResponse> create(@Valid @RequestBody CreateKnowledgeCategoryRequest request) {
        KnowledgeCategoryResponse response = appService.create(
                request.getName(),
                request.getDescription(),
                request.getParentId(),
                request.getIconFileId(),
                request.getColor(),
                request.getCoverImageFileId(),
                request.getSortOrder()
        );
        return Result.success(response);
    }

    /**
     * 查询知识点分类详情
     */
    @GetMapping("/{id}")
    public Result<KnowledgeCategoryResponse> getById(@PathVariable Long id) {
        KnowledgeCategoryResponse response = appService.getById(id);
        return Result.success(response);
    }

    /**
     * 分页查询知识点分类列表
     */
    @GetMapping
    public Result<PageResult<KnowledgeCategoryResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long parentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResult<KnowledgeCategoryResponse> result = appService.list(keyword, status, parentId, page, size);
        return Result.success(result);
    }

    /**
     * 查询完整分类树
     */
    @GetMapping("/tree")
    public Result<List<KnowledgeCategoryTreeResponse>> tree() {
        List<KnowledgeCategoryTreeResponse> tree = appService.tree();
        return Result.success(tree);
    }

    /**
     * 更新知识点分类
     */
    @PutMapping("/{id}")
    public Result<KnowledgeCategoryResponse> update(@PathVariable Long id,
                                                     @Valid @RequestBody UpdateKnowledgeCategoryRequest request) {
        KnowledgeCategoryResponse response = appService.update(
                id,
                request.getName(),
                request.getDescription(),
                request.getIconFileId(),
                request.getColor(),
                request.getCoverImageFileId(),
                request.getSortOrder()
        );
        return Result.success(response);
    }

    /**
     * 移动知识点分类
     */
    @PutMapping("/{id}/move")
    public Result<KnowledgeCategoryResponse> move(@PathVariable Long id,
                                                   @Valid @RequestBody MoveKnowledgeCategoryRequest request) {
        KnowledgeCategoryResponse response = appService.move(id, request.getNewParentId());
        return Result.success(response);
    }

    /**
     * 批量排序知识点分类
     */
    @PutMapping("/batch-sort")
    public Result<Void> batchSort(@Valid @RequestBody BatchSortRequest request) {
        appService.batchSort(request.getItems());
        return Result.success();
    }

    /**
     * 软删除知识点分类
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        appService.delete(id);
        return Result.success();
    }
}
