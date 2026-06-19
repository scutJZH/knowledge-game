package com.knowledgegame.app.api.controller;

import com.knowledgegame.app.api.dto.response.KnowledgeCategoryTreeResponse;
import com.knowledgegame.app.application.service.KnowledgeCategoryAppService;
import com.knowledgegame.core.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识点分类用户端 Controller（仅参数接收 + 结果返回，无业务逻辑）
 */
@RestController
@RequestMapping("/api/knowledge-categories")
public class KnowledgeCategoryController {

    private final KnowledgeCategoryAppService appService;

    public KnowledgeCategoryController(KnowledgeCategoryAppService appService) {
        this.appService = appService;
    }

    /**
     * 查询 ACTIVE 分类树
     */
    @GetMapping("/tree")
    public Result<List<KnowledgeCategoryTreeResponse>> tree() {
        List<KnowledgeCategoryTreeResponse> tree = appService.tree();
        return Result.success(tree);
    }
}
