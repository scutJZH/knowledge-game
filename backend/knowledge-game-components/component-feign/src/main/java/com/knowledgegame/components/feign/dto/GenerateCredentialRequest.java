package com.knowledgegame.components.feign.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * 生成上传凭证请求体（M2M 内部接口，Feign Client 专用）
 */
public record GenerateCredentialRequest(
        @NotNull Long userId,
        @NotNull Integer count,
        @NotBlank String basePath,
        @Size(max = 10) Map<String, Object> metadata
) {
}
