package com.knowledgegame.app.api.controller;

import com.knowledgegame.app.api.dto.GroupMemberResponse;
import com.knowledgegame.app.api.dto.JoinByInviteRequest;
import com.knowledgegame.app.application.service.GroupMemberAppService;
import com.knowledgegame.core.common.result.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 群组成员 Controller（用户端）
 */
@RestController
@RequestMapping("/api/study-groups")
public class GroupMemberController {

    private final GroupMemberAppService appService;

    public GroupMemberController(GroupMemberAppService appService) {
        this.appService = appService;
    }

    /**
     * 直接加入群组
     */
    @PostMapping("/{id}/members")
    public Result<GroupMemberResponse> joinDirectly(@PathVariable("id") Long groupId) {
        return Result.success(appService.joinDirectly(groupId));
    }

    /**
     * 凭邀请码加入群组
     */
    @PostMapping("/join-by-invite")
    public Result<GroupMemberResponse> joinByInvite(@Valid @RequestBody JoinByInviteRequest request) {
        return Result.success(appService.joinByInvite(request.getInviteCode()));
    }

    /**
     * 退出群组
     */
    @DeleteMapping("/{id}/members/me")
    public Result<Void> leave(@PathVariable("id") Long groupId) {
        appService.leave(groupId);
        return Result.success(null);
    }

    /**
     * 查询当前成员身份
     */
    @GetMapping("/{id}/members/me")
    public Result<GroupMemberResponse> getCurrentMember(@PathVariable("id") Long groupId) {
        return Result.success(appService.getCurrentMember(groupId));
    }
}
