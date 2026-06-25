package com.knowledgegame.app.api.controller;

import com.knowledgegame.app.api.dto.QuestionPageResponse;
import com.knowledgegame.app.application.service.QuestionAppService;
import com.knowledgegame.core.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    private final QuestionAppService appService;

    public QuestionController(QuestionAppService appService) {
        this.appService = appService;
    }

    @GetMapping
    public Result<QuestionPageResponse> listByCategory(
            @RequestParam Long categoryId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(appService.listByCategory(categoryId, page, size));
    }
}
