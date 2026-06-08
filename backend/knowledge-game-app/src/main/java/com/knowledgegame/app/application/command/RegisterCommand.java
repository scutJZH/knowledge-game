package com.knowledgegame.app.application.command;

import lombok.Builder;
import lombok.Getter;

/**
 * 用户注册命令（Controller → AppService 传输对象，隔离 domain 层）
 */
@Getter
@Builder
public class RegisterCommand {

    private String username;
    private String rawPassword;
    private String nickname;
}
