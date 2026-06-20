package com.knowledgegame.app.api.controller;

import com.knowledgegame.app.api.dto.CreateStudyGroupRequest;
import com.knowledgegame.app.api.dto.StudyGroupResponse;
import com.knowledgegame.app.application.service.StudyGroupAppService;
import com.knowledgegame.core.common.result.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学习群组 Controller（用户端）
 */
@RestController
@RequestMapping("/api/study-groups")
public class StudyGroupController {

    private final StudyGroupAppService appService;

    public StudyGroupController(StudyGroupAppService appService) {
        this.appService = appService;
    }

    @PostMapping
    public Result<StudyGroupResponse> create(@Valid @RequestBody CreateStudyGroupRequest request) {
        return Result.success(appService.create(request));
    }

    /**
     * 重新生成邀请码
     */
    @PostMapping("/{id}/invite-code/regenerate")
    public Result<StudyGroupResponse> regenerateInviteCode(@PathVariable("id") Long groupId) {
        return Result.success(appService.regenerateInviteCode(groupId));
    }
}
