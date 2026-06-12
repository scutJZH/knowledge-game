package com.knowledgegame.file.api.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 文件上传响应 DTO
 */
@Getter
@Builder
public class FileUploadResponse {

    /**
     * 文件 ID
     */
    private Long fileId;

    /**
     * 静态访问 URL
     */
    private String url;
}
