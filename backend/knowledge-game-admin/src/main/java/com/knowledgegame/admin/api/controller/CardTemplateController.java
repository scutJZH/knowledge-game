package com.knowledgegame.admin.api.controller;

import com.knowledgegame.admin.api.dto.request.AddStarImageRequest;
import com.knowledgegame.admin.api.dto.request.CreateCardTemplateRequest;
import com.knowledgegame.admin.api.dto.request.StarImageRequest;
import com.knowledgegame.admin.api.dto.request.UpdateCardTemplateRequest;
import com.knowledgegame.admin.api.dto.response.CardTemplateListResponse;
import com.knowledgegame.admin.api.dto.response.CardTemplateResponse;
import com.knowledgegame.admin.application.command.StarImageCommand;
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

import java.util.Collections;
import java.util.List;

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
        List<StarImageCommand> starImages = toStarImageCommands(request.getStarImages());
        CardTemplateResponse response = cardTemplateAppService.createCardTemplate(
                request.getIpSeriesId(),
                request.getCode(),
                request.getName(),
                request.getRarity(),
                request.getDescription(),
                request.getStatus(),
                starImages
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
            @RequestParam(required = false) Long ipSeriesId,
            @RequestParam(required = false) String rarity,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResult<CardTemplateListResponse> result = cardTemplateAppService.listCardTemplates(
                name, ipSeriesId, rarity, status, page, size);
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
                request.getStatus()
        );
        return Result.success(response);
    }

    /**
     * 添加/替换单张星级图片
     */
    @PostMapping("/{id}/star-images")
    public Result<CardTemplateResponse> addStarImage(@PathVariable Long id,
                                                      @Valid @RequestBody AddStarImageRequest request) {
        CardTemplateResponse response = cardTemplateAppService.addOrUpdateStarImage(
                id, request.getStarLevel(), request.getImageUrl());
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
     * 将请求 DTO 列表转换为应用层命令列表
     */
    private List<StarImageCommand> toStarImageCommands(List<StarImageRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return Collections.emptyList();
        }
        return requests.stream()
                .map(req -> new StarImageCommand(req.getStarLevel(), req.getImageUrl()))
                .toList();
    }
}
