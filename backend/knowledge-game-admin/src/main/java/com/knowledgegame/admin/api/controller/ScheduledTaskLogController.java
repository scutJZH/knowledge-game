package com.knowledgegame.admin.api.controller;

import com.knowledgegame.admin.api.dto.request.ScheduledTaskLogListRequest;
import com.knowledgegame.admin.api.dto.response.ScheduledTaskLogResponse;
import com.knowledgegame.admin.application.service.ScheduledTaskLogAppService;
import com.knowledgegame.core.common.result.Result;
import com.knowledgegame.core.domain.model.vo.PageResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 定时任务执行日志 Controller
 */
@RestController
@RequestMapping("/api/admin/scheduled-task-logs")
public class ScheduledTaskLogController {

    private final ScheduledTaskLogAppService appService;

    public ScheduledTaskLogController(ScheduledTaskLogAppService appService) {
        this.appService = appService;
    }

    @GetMapping
    public Result<PageResult<ScheduledTaskLogResponse>> list(@Valid ScheduledTaskLogListRequest request) {
        return Result.success(appService.list(request));
    }
}
