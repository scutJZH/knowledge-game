package com.knowledgegame.app.api.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 更新用户请求 DTO
 */
@Getter
@Setter
public class UpdateUserRequest {

    @Size(max = 50, message = "昵称最长 50")
    private String nickname;

    private Long avatarFileId;
}
