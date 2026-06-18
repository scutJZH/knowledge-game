package com.knowledgegame.admin.api.controller;

import com.knowledgegame.admin.api.dto.request.CreateIpSeriesRequest;
import com.knowledgegame.admin.api.dto.request.UpdateIpSeriesRequest;
import com.knowledgegame.admin.api.dto.response.IpSeriesResponse;
import com.knowledgegame.admin.application.service.IpSeriesAppService;
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

/**
 * IP 系列管理端 Controller（仅参数接收 + 结果返回，无业务逻辑）
 */
@RestController
@RequestMapping("/api/admin/ip-series")
public class IpSeriesController {

    private final IpSeriesAppService ipSeriesAppService;

    public IpSeriesController(IpSeriesAppService ipSeriesAppService) {
        this.ipSeriesAppService = ipSeriesAppService;
    }

    /**
     * 创建 IP 系列
     */
    @PostMapping
    public Result<IpSeriesResponse> create(@Valid @RequestBody CreateIpSeriesRequest request) {
        IpSeriesResponse response = ipSeriesAppService.createIpSeries(
                request.getCode(),
                request.getName(),
                request.getDescription(),
                request.getCoverImageFileId(),
                request.getStatus()
        );
        return Result.success(response);
    }

    /**
     * 查询 IP 系列详情
     */
    @GetMapping("/{id}")
    public Result<IpSeriesResponse> getById(@PathVariable Long id) {
        IpSeriesResponse response = ipSeriesAppService.getIpSeriesById(id);
        return Result.success(response);
    }

    /**
     * 分页查询 IP 系列列表
     */
    @GetMapping
    public Result<PageResult<IpSeriesResponse>> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String order,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResult<IpSeriesResponse> result = ipSeriesAppService.listIpSeries(
                name, code, status, sort, order, page, size);
        return Result.success(result);
    }

    /**
     * 更新 IP 系列
     */
    @PutMapping("/{id}")
    public Result<IpSeriesResponse> update(@PathVariable Long id,
                                           @Valid @RequestBody UpdateIpSeriesRequest request) {
        IpSeriesResponse response = ipSeriesAppService.updateIpSeries(
                id,
                request.getCode(),
                request.getName(),
                request.getDescription(),
                request.getCoverImageFileId(),
                request.getStatus()
        );
        return Result.success(response);
    }

    /**
     * 软删除 IP 系列
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        ipSeriesAppService.deleteIpSeries(id);
        return Result.success();
    }
}
