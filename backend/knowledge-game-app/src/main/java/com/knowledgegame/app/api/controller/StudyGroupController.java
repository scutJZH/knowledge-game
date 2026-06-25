package com.knowledgegame.app.api.controller;

import com.knowledgegame.app.api.dto.CreateStudyGroupRequest;
import com.knowledgegame.app.api.dto.StudyGroupDetailResponse;
import com.knowledgegame.app.api.dto.StudyGroupListResponse;
import com.knowledgegame.app.api.dto.StudyGroupResponse;
import com.knowledgegame.app.api.dto.UpdateStudyGroupRequest;
import com.knowledgegame.app.application.service.StudyGroupAppService;
import com.knowledgegame.core.common.result.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    /**
     * 查询当前用户已加入的群组列表
     */
    @GetMapping
    public Result<List<StudyGroupListResponse>> listMyGroups() {
        return Result.success(appService.listMyGroups());
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

    @GetMapping("/{id}")
    public Result<StudyGroupDetailResponse> getDetail(@PathVariable("id") Long groupId) {
        return Result.success(appService.getDetail(groupId));
    }

    @PutMapping("/{id}")
    public Result<StudyGroupResponse> update(@PathVariable("id") Long groupId,
                                              @RequestBody UpdateStudyGroupRequest request) {
        return Result.success(appService.update(groupId, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> disband(@PathVariable("id") Long groupId) {
        appService.disband(groupId);
        return Result.success(null);
    }
}
