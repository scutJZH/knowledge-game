package com.knowledgegame.app.api.controller;

import com.knowledgegame.app.api.dto.response.ActiveIpSeriesResponse;
import com.knowledgegame.app.application.service.IpSeriesAppService;
import com.knowledgegame.core.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * IP 系列用户端控制器
 */
@RestController
@RequestMapping("/api")
public class IpSeriesController {

    private final IpSeriesAppService ipSeriesAppService;

    public IpSeriesController(IpSeriesAppService ipSeriesAppService) {
        this.ipSeriesAppService = ipSeriesAppService;
    }

    /**
     * 查询全部 ACTIVE 状态的 IP 系列
     */
    @GetMapping("/ip-series")
    public Result<List<ActiveIpSeriesResponse>> listActive() {
        return Result.success(ipSeriesAppService.listActive());
    }
}
