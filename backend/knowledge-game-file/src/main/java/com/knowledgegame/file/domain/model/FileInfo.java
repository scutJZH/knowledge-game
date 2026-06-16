package com.knowledgegame.file.domain.model;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 文件信息聚合根
 */
public class FileInfo {

    private Long id;
    private String originalName;
    private String storedName;
    private String filePath;
    private String url;
    private String contentType;
    private long fileSize;
    private String basePath;
    private long uploaderId;
    private LocalDateTime createdAt;
    private boolean deleted;
    private Map<String, Object> metadata;

    private FileInfo() {
    }

    /**
     * 创建文件信息
     */
    public static FileInfo create(String originalName, StoredFile storedFile, String basePath, long uploaderId,
                                   Map<String, Object> metadata) {
        FileInfo info = new FileInfo();
        info.originalName = originalName;
        info.storedName = storedFile.storedName();
        info.filePath = storedFile.filePath();
        info.url = storedFile.url();
        info.contentType = storedFile.contentType();
        info.fileSize = storedFile.fileSize();
        info.basePath = basePath;
        info.uploaderId = uploaderId;
        info.createdAt = LocalDateTime.now();
        info.deleted = false;
        info.metadata = metadata;
        return info;
    }

    /**
     * 从持久化层重建领域对象（仅由 Converter 调用）
     */
    public static FileInfo reconstruct(Long id, String originalName, String storedName, String filePath,
                                String url, String contentType, long fileSize, String basePath,
                                long uploaderId, LocalDateTime createdAt, boolean deleted,
                                Map<String, Object> metadata) {
        FileInfo info = new FileInfo();
        info.id = id;
        info.originalName = originalName;
        info.storedName = storedName;
        info.filePath = filePath;
        info.url = url;
        info.contentType = contentType;
        info.fileSize = fileSize;
        info.basePath = basePath;
        info.uploaderId = uploaderId;
        info.createdAt = createdAt;
        info.deleted = deleted;
        info.metadata = metadata;
        return info;
    }

    /**
     * 标记为已删除（软删除）
     */
    public void markDeleted() {
        this.deleted = true;
    }

    public Long getId() {
        return id;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getStoredName() {
        return storedName;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getUrl() {
        return url;
    }

    public String getContentType() {
        return contentType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getBasePath() {
        return basePath;
    }

    public long getUploaderId() {
        return uploaderId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    void setId(Long id) {
        this.id = id;
    }
}
