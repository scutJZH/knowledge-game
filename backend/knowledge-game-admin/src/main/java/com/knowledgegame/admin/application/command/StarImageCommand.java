package com.knowledgegame.admin.application.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 星级图片命令对象（应用层入参，用于隔离 api 层与 domain 层）
 */
@Getter
@Setter
public class StarImageCommand {

    private int starLevel;
    private String imageUrl;

    public StarImageCommand() {
    }

    public StarImageCommand(int starLevel, String imageUrl) {
        this.starLevel = starLevel;
        this.imageUrl = imageUrl;
    }
}
