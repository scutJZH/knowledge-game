package com.knowledgegame.components.feign.client;

import com.knowledgegame.components.feign.dto.FileInfoResponse;
import com.knowledgegame.components.feign.dto.GenerateCredentialRequest;
import com.knowledgegame.core.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 文件服务 Feign Client
 * <p>
 * 按提供方维度组织，app/admin 共用
 */
@FeignClient(name = "knowledge-game-file")
public interface FileServiceClient {

    /**
     * 生成上传凭证（M2M 接口）
     *
     * @param request 凭证请求（含 userId、count、basePath、metadata）
     * @return 凭证 token
     */
    @PostMapping("/api/file/internal/credential")
    Result<String> generateCredential(@RequestBody GenerateCredentialRequest request);

    /**
     * 查询文件信息（M2M 接口）
     * <p>
     * 供业务 AppService 校验 fileId 时调用，响应含 metadata 字段
     *
     * @param fileId 文件 ID
     * @return 文件信息（含 metadata）
     */
    @GetMapping("/api/file/internal/{fileId}")
    Result<FileInfoResponse> getFileInfo(@PathVariable("fileId") Long fileId);
}
