package com.knowledgegame.app.application.command;

import lombok.Builder;
import lombok.Getter;

/**
 * 登录命令（Controller → AppService 隔离层）
 */
@Getter
@Builder
public class LoginCommand {

    private final String username;
    private final String rawPassword;
}
