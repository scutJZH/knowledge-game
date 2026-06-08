package com.knowledgegame.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * 管理端启动类
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.knowledgegame")
public class KnowledgeGameAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowledgeGameAdminApplication.class, args);
    }
}
