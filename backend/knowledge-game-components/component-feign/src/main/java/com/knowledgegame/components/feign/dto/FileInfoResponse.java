package com.knowledgegame.components.feign.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 文件信息响应 DTO（共享，供 Feign Client 反序列化）
 * <p>
 * 与文件服务 FileInfoResponse JSON 结构一致
 */
public class FileInfoResponse {

    private Long fileId;
    private String url;
    private String originalName;
    private String contentType;
    private Long fileSize;
    private String basePath;
    private Long uploaderId;
    private LocalDateTime createdAt;
    private Map<String, Object> metadata;

    public FileInfoResponse() {
    }

    private FileInfoResponse(Builder builder) {
        this.fileId = builder.fileId;
        this.url = builder.url;
        this.originalName = builder.originalName;
        this.contentType = builder.contentType;
        this.fileSize = builder.fileSize;
        this.basePath = builder.basePath;
        this.uploaderId = builder.uploaderId;
        this.createdAt = builder.createdAt;
        this.metadata = builder.metadata;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Long getFileId() { return fileId; }
    public String getUrl() { return url; }
    public String getOriginalName() { return originalName; }
    public String getContentType() { return contentType; }
    public Long getFileSize() { return fileSize; }
    public String getBasePath() { return basePath; }
    public Long getUploaderId() { return uploaderId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Map<String, Object> getMetadata() { return metadata; }

    public static class Builder {
        private Long fileId;
        private String url;
        private String originalName;
        private String contentType;
        private Long fileSize;
        private String basePath;
        private Long uploaderId;
        private LocalDateTime createdAt;
        private Map<String, Object> metadata;

        public Builder fileId(Long fileId) { this.fileId = fileId; return this; }
        public Builder url(String url) { this.url = url; return this; }
        public Builder originalName(String originalName) { this.originalName = originalName; return this; }
        public Builder contentType(String contentType) { this.contentType = contentType; return this; }
        public Builder fileSize(Long fileSize) { this.fileSize = fileSize; return this; }
        public Builder basePath(String basePath) { this.basePath = basePath; return this; }
        public Builder uploaderId(Long uploaderId) { this.uploaderId = uploaderId; return this; }
        public Builder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }

        public FileInfoResponse build() {
            return new FileInfoResponse(this);
        }
    }
}
