package com.knowledgegame.app.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 凭邀请码加入群组请求 DTO
 */
public class JoinByInviteRequest {

    @NotBlank(message = "邀请码不能为空")
    @Pattern(regexp = "^[0-9A-HJKMNP-TV-Z]{8}$", message = "邀请码格式错误")
    private String inviteCode;

    public String getInviteCode() { return inviteCode; }
    public void setInviteCode(String inviteCode) { this.inviteCode = inviteCode; }
}
