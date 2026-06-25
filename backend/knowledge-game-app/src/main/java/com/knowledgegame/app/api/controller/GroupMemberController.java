package com.knowledgegame.app.api.controller;

import com.knowledgegame.app.api.dto.GroupMemberListResponse;
import com.knowledgegame.app.api.dto.GroupMemberResponse;
import com.knowledgegame.app.api.dto.JoinByInviteRequest;
import com.knowledgegame.app.api.dto.TransferOwnershipRequest;
import com.knowledgegame.app.api.dto.UpdateMemberRoleRequest;
import com.knowledgegame.app.application.service.GroupMemberAppService;
import com.knowledgegame.core.common.result.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

    /**
     * 更新成员角色（仅 OWNER，ADMIN / MEMBER 互转）
     */
    @PutMapping("/{id}/members/{userId}")
    public Result<Void> updateRole(@PathVariable("id") Long groupId,
                                    @PathVariable("userId") Long targetUserId,
                                    @Valid @RequestBody UpdateMemberRoleRequest request) {
        appService.updateRole(groupId, targetUserId, request.getRole());
        return Result.success(null);
    }

    /**
     * 转让群主（仅 OWNER，原 OWNER 变为 ADMIN）
     */
    @PostMapping("/{id}/transfer-ownership")
    public Result<Void> transferOwnership(@PathVariable("id") Long groupId,
                                           @Valid @RequestBody TransferOwnershipRequest request) {
        appService.transferOwnership(groupId, request.getToUserId());
        return Result.success(null);
    }

    /**
     * 查询群组成员列表（按积分降序）
     */
    @GetMapping("/{id}/members")
    public Result<List<GroupMemberListResponse>> listMembers(@PathVariable("id") Long groupId) {
        return Result.success(appService.listMembers(groupId));
    }

    /**
     * 踢出成员（路由安全：Spring MVC 优先匹配 /members/me，字符串 "me" 无法 bind 到 Long）
     */
    @DeleteMapping("/{id}/members/{userId}")
    public Result<Void> kick(@PathVariable("id") Long groupId,
                             @PathVariable("userId") Long targetUserId) {
        appService.kick(groupId, targetUserId);
        return Result.success(null);
    }
}
