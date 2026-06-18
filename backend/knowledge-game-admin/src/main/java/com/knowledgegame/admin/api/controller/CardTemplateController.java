package com.knowledgegame.admin.api.controller;

import com.knowledgegame.admin.api.dto.request.BatchStatusRequest;
import com.knowledgegame.admin.api.dto.request.CreateCardTemplateRequest;
import com.knowledgegame.admin.api.dto.request.UpdateCardTemplateRequest;
import com.knowledgegame.admin.api.dto.response.CardTemplateListResponse;
import com.knowledgegame.admin.api.dto.response.CardTemplateResponse;
import com.knowledgegame.admin.application.service.CardTemplateAppService;
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
 * 卡牌模板管理端 Controller（仅参数接收 + 结果返回，无业务逻辑）
 */
@RestController
@RequestMapping("/api/admin/card-templates")
public class CardTemplateController {

    private final CardTemplateAppService cardTemplateAppService;

    public CardTemplateController(CardTemplateAppService cardTemplateAppService) {
        this.cardTemplateAppService = cardTemplateAppService;
    }

    /**
     * 创建卡牌模板
     */
    @PostMapping
    public Result<CardTemplateResponse> create(@Valid @RequestBody CreateCardTemplateRequest request) {
        CardTemplateResponse response = cardTemplateAppService.createCardTemplate(
                request.getIpSeriesId(),
                request.getCode(),
                request.getName(),
                request.getRarity(),
                request.getDescription(),
                request.getStatus(),
                request.getImageFileId()
        );
        return Result.success(response);
    }

    /**
     * 查询卡牌模板详情
     */
    @GetMapping("/{id}")
    public Result<CardTemplateResponse> getById(@PathVariable Long id) {
        CardTemplateResponse response = cardTemplateAppService.getCardTemplateById(id);
        return Result.success(response);
    }

    /**
     * 分页查询卡牌模板列表
     */
    @GetMapping
    public Result<PageResult<CardTemplateListResponse>> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) Long ipSeriesId,
            @RequestParam(required = false) String rarity,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String order,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResult<CardTemplateListResponse> result = cardTemplateAppService.listCardTemplates(
                name, code, ipSeriesId, rarity, status, sort, order, page, size);
        return Result.success(result);
    }

    /**
     * 更新卡牌模板基础信息
     */
    @PutMapping("/{id}")
    public Result<CardTemplateResponse> update(@PathVariable Long id,
                                                @Valid @RequestBody UpdateCardTemplateRequest request) {
        CardTemplateResponse response = cardTemplateAppService.updateCardTemplate(
                id,
                request.getCode(),
                request.getName(),
                request.getRarity(),
                request.getDescription(),
                request.getStatus(),
                request.getImageFileId()
        );
        return Result.success(response);
    }

    /**
     * 软删除卡牌模板
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        cardTemplateAppService.deleteCardTemplate(id);
        return Result.success();
    }

    /**
     * 批量启用卡牌模板
     */
    @PutMapping("/batch-activate")
    public Result<Void> batchActivate(@Valid @RequestBody BatchStatusRequest request) {
        cardTemplateAppService.batchActivate(request.getIds());
        return Result.success();
    }

    /**
     * 批量停用卡牌模板
     */
    @PutMapping("/batch-deactivate")
    public Result<Void> batchDeactivate(@Valid @RequestBody BatchStatusRequest request) {
        cardTemplateAppService.batchDeactivate(request.getIds());
        return Result.success();
    }
}
