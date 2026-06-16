package com.knowledgegame.admin.api.controller;

import java.util.HashMap;
import java.util.Map;

import com.knowledgegame.admin.config.FilePathMapping;
import com.knowledgegame.auth.security.SecurityUtils;
import com.knowledgegame.components.feign.client.FileServiceClient;
import com.knowledgegame.components.feign.dto.GenerateCredentialRequest;
import com.knowledgegame.components.feign.dto.UploadCredentialResponse;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.common.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文件上传凭证 Controller（仅参数接收 + 结果返回，无业务逻辑）
 */
@RestController
@RequestMapping("/api/admin")
public class FileController {

    private final FileServiceClient fileServiceClient;

    @Value("${knowledgegame.file.upload-base-url}")
    private String uploadBaseUrl;

    public FileController(FileServiceClient fileServiceClient) {
        this.fileServiceClient = fileServiceClient;
    }

    private static final Logger log = LoggerFactory.getLogger(FileController.class);

    /**
     * 获取上传凭证
     * <p>
     * 前端调用此接口获取一次性上传凭证（token）和对应的上传地址（uploadUrl），
     * 然后携带 token 直接向文件服务发起文件上传请求。
     *
     * @param bizType 业务类型（如 IP_SERIES、CARD_TEMPLATE），用于映射到文件服务的存储路径
     * @param count   凭证允许上传的文件数量，默认 1
     * @return 上传凭证响应（含 token 和 uploadUrl）
     */
    @GetMapping("/upload-credential")
    public Result<UploadCredentialResponse> getCredential(
            @RequestParam String bizType,
            @RequestParam(defaultValue = "1") int count) {
        // 获取当前登录用户 ID
        Long userId = SecurityUtils.getCurrentUserId();

        // 将业务类型转换为文件服务的存储路径标识
        String basePath = FilePathMapping.toBasePath(bizType);
        if (basePath == null) {
            throw new BusinessException("不支持的业务类型: " + bizType);
        }

        // 组装 metadata（当前所有 bizType 共享 { bizType, userId } 模板）
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("bizType", bizType);
        metadata.put("userId", userId);

        // 通过 Feign 调用文件服务生成凭证
        String token;
        try {
            GenerateCredentialRequest request = new GenerateCredentialRequest(userId, count, basePath, metadata);
            Result<String> credentialResult = fileServiceClient.generateCredential(request);
            token = credentialResult.getData();
            if (token == null || token.isBlank()) {
                String errorMsg = credentialResult.getMessage() != null ? credentialResult.getMessage() : "文件服务返回空凭证";
                throw new BusinessException(errorMsg);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用文件服务生成凭证失败: bizType={}, basePath={}", bizType, basePath, e);
            throw new BusinessException("文件服务暂不可用，请稍后重试");
        }

        // 根据 count 决定上传地址：批量上传或单个上传
        String uploadUrl = count > 1
                ? uploadBaseUrl + "/api/file/batch-upload"
                : uploadBaseUrl + "/api/file/upload";

        return Result.success(new UploadCredentialResponse(token, uploadUrl));
    }
}
