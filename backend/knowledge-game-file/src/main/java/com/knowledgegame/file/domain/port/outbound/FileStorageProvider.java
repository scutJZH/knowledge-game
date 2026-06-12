package com.knowledgegame.file.domain.port.outbound;

import com.knowledgegame.file.domain.model.StoredFile;

import java.io.InputStream;

/**
 * 文件存储出端口
 */
public interface FileStorageProvider {

    /**
     * 存储文件
     *
     * @param basePath     目录路径
     * @param originalName 原始文件名
     * @param content      文件内容流
     * @param size         文件大小
     * @param contentType  MIME 类型
     * @return 存储结果
     */
    StoredFile store(String basePath, String originalName, InputStream content, long size, String contentType);

    /**
     * 删除文件
     *
     * @param filePath 磁盘相对路径
     */
    void delete(String filePath);

    /**
     * 加载文件
     *
     * @param filePath 磁盘相对路径
     * @return 文件内容流
     */
    InputStream load(String filePath);
}
