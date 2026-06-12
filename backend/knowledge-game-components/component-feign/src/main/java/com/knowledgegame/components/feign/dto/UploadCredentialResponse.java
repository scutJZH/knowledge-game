package com.knowledgegame.components.feign.dto;

/**
 * 上传凭证响应 DTO
 * <p>
 * app/admin 凭证接口统一返回此 DTO
 */
public record UploadCredentialResponse(
        String token,
        String uploadUrl
) {
}
