package com.knowledgegame.app.api.controller;

import com.knowledgegame.app.api.dto.request.PointTransactionQueryRequest;
import com.knowledgegame.app.api.dto.response.BalanceResponse;
import com.knowledgegame.app.api.dto.response.PointTransactionCrossGroupResponse;
import com.knowledgegame.app.api.dto.response.PointTransactionPageResponse;
import com.knowledgegame.app.api.dto.response.PointTransactionResponse;
import com.knowledgegame.app.application.service.PointTransactionAppService;
import com.knowledgegame.core.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 积分流水 Controller（用户端）
 */
@RestController
public class PointTransactionController {

    private final PointTransactionAppService appService;

    public PointTransactionController(PointTransactionAppService appService) {
        this.appService = appService;
    }

    @GetMapping("/api/study-groups/{groupId}/point-transactions")
    public Result<PointTransactionPageResponse<PointTransactionResponse>> listByGroup(
            @PathVariable Long groupId,
            @ModelAttribute PointTransactionQueryRequest req) {
        return Result.success(appService.listByGroup(groupId, req.toQuery()));
    }

    @GetMapping("/api/me/point-transactions")
    public Result<PointTransactionPageResponse<PointTransactionCrossGroupResponse>> listByUser(
            @ModelAttribute PointTransactionQueryRequest req) {
        return Result.success(appService.listByUser(req.toQuery()));
    }

    @GetMapping("/api/study-groups/{groupId}/members/me/balance")
    public Result<BalanceResponse> getBalance(@PathVariable Long groupId) {
        return Result.success(appService.getBalance(groupId));
    }
}
