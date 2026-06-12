package com.knowledgegame.file.api.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 文件信息响应 DTO
 */
@Getter
@Builder
public class FileInfoResponse {

    private Long fileId;
    private String url;
    private String originalName;
    private String contentType;
    private Long fileSize;
    private String basePath;
    private Long uploaderId;
    private LocalDateTime createdAt;
}
