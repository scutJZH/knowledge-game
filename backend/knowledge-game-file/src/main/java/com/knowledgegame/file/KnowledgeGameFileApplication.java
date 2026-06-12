package com.knowledgegame.file;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 文件服务启动类
 */
@SpringBootApplication
@EnableScheduling
public class KnowledgeGameFileApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowledgeGameFileApplication.class, args);
    }
}
