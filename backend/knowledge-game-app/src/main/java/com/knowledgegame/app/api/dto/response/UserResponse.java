package com.knowledgegame.app.api.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * 用户响应 DTO
 */
@Getter
@Builder
public class UserResponse {

    private Long id;
    private String username;
    private String nickname;
    private Long avatarFileId;
    private String avatarUrl;
    private String role;
}
