package com.knowledgegame.file.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 文件服务配置属性
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "knowledgegame.file")
public class FileProperties {

    /**
     * 存储配置
     */
    private Storage storage = new Storage();

    /**
     * 上传配置
     */
    private Upload upload = new Upload();

    /**
     * 凭证配置
     */
    private Credential credential = new Credential();

    @Getter
    @Setter
    public static class Storage {
        /**
         * 存储类型：local
         */
        private String type = "local";

        /**
         * 本地存储配置
         */
        private Local local = new Local();
    }

    @Getter
    @Setter
    public static class Local {
        /**
         * 本地磁盘存储根目录
         */
        private String storageDir = "./uploads";
    }

    @Getter
    @Setter
    public static class Upload {
        /**
         * 单文件大小上限（字符串格式，如 10MB）
         */
        private String maxFileSize = "10MB";

        /**
         * 允许的 MIME 类型
         */
        private List<String> allowedTypes = new ArrayList<>(List.of(
                "image/jpeg", "image/png", "image/gif", "image/webp"
        ));
    }

    @Getter
    @Setter
    public static class Credential {
        /**
         * 凭证过期时间（分钟）
         */
        private int expireMinutes = 5;
    }
}
