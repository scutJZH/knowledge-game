package com.knowledgegame.file.api.controller;

import com.knowledgegame.core.common.result.Result;
import com.knowledgegame.file.api.dto.FileInfoResponse;
import com.knowledgegame.file.api.dto.FileUploadResponse;
import com.knowledgegame.file.application.FileAppService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件服务 Controller
 * <p>
 * 路径规划：
 * - /api/file/upload        — 前端直传（凭证鉴权），不走 M2M
 * - /api/file/internal/**   — 机机接口（M2M 鉴权），由 app/admin 通过 Feign 调用
 * - /static/**              — 静态资源，无鉴权
 */
@RestController
@RequestMapping("/api/file")
public class FileController {

    private final FileAppService fileAppService;

    public FileController(FileAppService fileAppService) {
        this.fileAppService = fileAppService;
    }

    // ==================== 前端直传接口（凭证鉴权） ====================

    /**
     * 上传文件（前端直传，通过凭证鉴权）
     */
    @PostMapping("/upload")
    public Result<FileUploadResponse> uploadFile(
            @RequestHeader("X-Upload-Token") String token,
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam("file") MultipartFile file) {
        FileUploadResponse response = fileAppService.uploadFile(userId, token, file);
        return Result.success(response);
    }

    /**
     * 批量上传文件（前端直传，通过凭证鉴权）
     * 返回结果顺序与 files 参数顺序一致
     */
    @PostMapping("/batch-upload")
    public Result<List<FileUploadResponse>> batchUploadFiles(
            @RequestHeader("X-Upload-Token") String token,
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam("files") List<MultipartFile> files) {
        List<FileUploadResponse> responses = fileAppService.batchUploadFiles(userId, token, files);
        return Result.success(responses);
    }

    // ==================== 机机接口（M2M 鉴权） ====================

    /**
     * 生成上传凭证（M2M，由 app/admin 通过 Feign 调用）
     * count 参数指定凭证允许上传的文件数量，默认 1
     * basePath 指定文件存储目录路径
     */
    @PostMapping("/internal/credential")
    public Result<String> generateCredential(@RequestParam Long userId,
                                              @RequestParam(defaultValue = "1") int count,
                                              @RequestParam String basePath) {
        String token = fileAppService.generateCredential(userId, count, basePath);
        return Result.success(token);
    }

    /**
     * 删除文件（M2M）
     */
    @DeleteMapping("/internal/{fileId}")
    public Result<Void> deleteFile(@PathVariable Long fileId) {
        fileAppService.deleteFile(fileId);
        return Result.success();
    }

    /**
     * 查询文件信息（M2M）
     */
    @GetMapping("/internal/{fileId}")
    public Result<FileInfoResponse> getFileInfo(@PathVariable Long fileId) {
        FileInfoResponse response = fileAppService.getFileInfo(fileId);
        return Result.success(response);
    }

    /**
     * 批量查询文件 URL（M2M）
     */
    @PostMapping("/internal/batch-urls")
    public Result<List<FileInfoResponse>> batchGetUrls(@RequestBody BatchUrlsRequest request) {
        List<FileInfoResponse> responses = fileAppService.batchGetUrls(request.fileIds());
        return Result.success(responses);
    }

    /**
     * 批量查询请求体
     */
    public record BatchUrlsRequest(List<Long> fileIds) {
    }
}
