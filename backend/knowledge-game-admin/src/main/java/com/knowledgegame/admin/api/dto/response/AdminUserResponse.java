package com.knowledgegame.admin.api.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * 管理端用户响应 DTO
 */
@Getter
@Builder
public class AdminUserResponse {

    private Long id;
    private String username;
    private String nickname;
    private String role;
}
