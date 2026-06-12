package com.knowledgegame.file.infrastructure.storage;

import com.knowledgegame.file.domain.model.StoredFile;
import com.knowledgegame.file.domain.port.outbound.FileStorageProvider;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * 本地磁盘文件存储实现
 */
public class LocalFileStorageProvider implements FileStorageProvider {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * MIME 类型 → 扩展名映射
     */
    private static final Map<String, String> EXTENSION_MAP = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/gif", ".gif",
            "image/webp", ".webp"
    );

    private final String basePath;

    public LocalFileStorageProvider(String basePath) {
        this.basePath = basePath;
    }

    @Override
    public StoredFile store(String bizType, String originalName, InputStream content, long size, String contentType) {
        String dateDir = LocalDate.now().format(DATE_FMT);
        String storedName = UUID.randomUUID().toString() + extensionFromContentType(contentType);
        String relativePath = bizType + "/" + dateDir + "/" + storedName;

        Path fullPath = Paths.get(basePath, relativePath);
        try {
            Files.createDirectories(fullPath.getParent());
            Files.copy(content, fullPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("文件存储失败: " + e.getMessage(), e);
        }

        // URL 由调用方拼接（需要知道 host:port），此处只返回相对路径
        String url = "/static/" + relativePath;

        return new StoredFile(storedName, relativePath, url, contentType, size);
    }

    @Override
    public void delete(String filePath) {
        Path fullPath = Paths.get(basePath, filePath);
        try {
            Files.deleteIfExists(fullPath);
        } catch (IOException e) {
            throw new RuntimeException("文件删除失败: " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream load(String filePath) {
        Path fullPath = Paths.get(basePath, filePath);
        try {
            return Files.newInputStream(fullPath);
        } catch (IOException e) {
            throw new RuntimeException("文件加载失败: " + e.getMessage(), e);
        }
    }

    /**
     * 根据 contentType 推断扩展名
     */
    private String extensionFromContentType(String contentType) {
        return EXTENSION_MAP.getOrDefault(contentType, "");
    }
}
