package com.knowledgegame.admin.api.controller;

import com.knowledgegame.admin.api.dto.request.RecycleBinListRequest;
import com.knowledgegame.admin.api.dto.response.RecycleBinItemResponse;
import com.knowledgegame.admin.api.dto.response.SupportedTypeResponse;
import com.knowledgegame.admin.application.service.RecycleBinAppService;
import com.knowledgegame.core.common.result.Result;
import com.knowledgegame.core.domain.model.vo.PageResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 回收站管理端 Controller
 * <p>
 * 本需求实现列表查询和 supported-types 两个端点。
 * 恢复/永久删除等留后端点由 REQ-102/103 新增。
 */
@RestController
@RequestMapping("/api/admin/recycle-bin")
public class RecycleBinController {

    private final RecycleBinAppService appService;

    public RecycleBinController(RecycleBinAppService appService) {
        this.appService = appService;
    }

    /**
     * 分页查询回收站列表
     */
    @GetMapping
    public Result<PageResult<RecycleBinItemResponse>> list(@Valid RecycleBinListRequest request) {
        return Result.success(appService.list(request));
    }

    /**
     * 已接入回收站的资源类型（前端目录树）
     */
    @GetMapping("/supported-types")
    public Result<List<SupportedTypeResponse>> supportedTypes() {
        return Result.success(appService.supportedTypes());
    }
}
