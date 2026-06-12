package com.knowledgegame.file.domain.model;

/**
 * 文件存储结果值对象
 */
public record StoredFile(

        /**
         * 存储文件名（UUID）
         */
        String storedName,

        /**
         * 磁盘相对路径
         */
        String filePath,

        /**
         * 静态访问 URL
         */
        String url,

        /**
         * MIME 类型
         */
        String contentType,

        /**
         * 文件大小（字节）
         */
        long fileSize
) {
}
