package com.knowledgegame.file.application;

import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.common.util.EnumUtils;
import com.knowledgegame.file.api.dto.FileInfoResponse;
import com.knowledgegame.file.api.dto.FileUploadResponse;
import com.knowledgegame.file.common.config.FileProperties;
import com.knowledgegame.file.domain.model.BizType;
import com.knowledgegame.file.domain.model.FileInfo;
import com.knowledgegame.file.domain.model.StoredFile;
import com.knowledgegame.file.domain.port.outbound.FileInfoRepository;
import com.knowledgegame.file.domain.port.outbound.FileStorageProvider;
import com.knowledgegame.file.domain.service.UploadCredentialService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件应用服务
 */
public class FileAppService {

    private final FileStorageProvider storageProvider;
    private final FileInfoRepository fileInfoRepository;
    private final UploadCredentialService credentialService;
    private final FileProperties properties;

    public FileAppService(FileStorageProvider storageProvider,
                          FileInfoRepository fileInfoRepository,
                          UploadCredentialService credentialService,
                          FileProperties properties) {
        this.storageProvider = storageProvider;
        this.fileInfoRepository = fileInfoRepository;
        this.credentialService = credentialService;
        this.properties = properties;
    }

    /**
     * 生成上传凭证
     */
    public String generateCredential(long userId, int count) {
        return credentialService.generateCredential(userId, count);
    }

    /**
     * 上传文件
     */
    @Transactional
    public FileUploadResponse uploadFile(long userId, String token, MultipartFile file, String bizType) {
        // 校验业务类型（EnumUtils 无效值直接抛 BusinessException）
        BizType bizTypeEnum = EnumUtils.valueOf(BizType.class, bizType);

        // 校验文件类型
        String contentType = file.getContentType();
        if (contentType == null || !properties.getUpload().getAllowedTypes().contains(contentType)) {
            throw new BusinessException(400, "不支持的文件类型: " + contentType);
        }

        // 校验文件大小
        if (file.getSize() > parseMaxFileSize()) {
            throw new BusinessException(400, "文件大小超过限制");
        }

        // 前置校验凭证有效性（不扣减，防止上传失败浪费凭证）
        if (!credentialService.validate(userId, token)) {
            throw new BusinessException(401, "上传凭证无效或已过期");
        }

        // 存储文件
        StoredFile storedFile;
        try {
            storedFile = storageProvider.store(
                    bizType,
                    file.getOriginalFilename(),
                    file.getInputStream(),
                    file.getSize(),
                    contentType
            );
        } catch (IOException e) {
            throw new BusinessException(500, "文件读取失败");
        }

        // 保存元数据
        FileInfo fileInfo = FileInfo.create(
                file.getOriginalFilename(),
                storedFile,
                bizTypeEnum,
                userId
        );
        FileInfo saved = fileInfoRepository.save(fileInfo);

        // 存储和入库都成功后，原子消费凭证
        if (!credentialService.tryConsume(userId, token)) {
            throw new BusinessException(401, "上传凭证无效或已过期");
        }

        return FileUploadResponse.builder()
                .fileId(saved.getId())
                .url(saved.getUrl())
                .build();
    }

    /**
     * 删除文件（软删除）
     */
    @Transactional
    public void deleteFile(Long fileId) {
        FileInfo fileInfo = fileInfoRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException(404, "文件不存在"));
        fileInfo.markDeleted();
        fileInfoRepository.save(fileInfo);
    }

    /**
     * 查询文件信息
     */
    public FileInfoResponse getFileInfo(Long fileId) {
        FileInfo fileInfo = fileInfoRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException(404, "文件不存在"));
        return FileInfoAssembler.INSTANCE.toResponse(fileInfo);
    }

    /**
     * 批量查询文件 URL
     */
    public List<FileInfoResponse> batchGetUrls(List<Long> fileIds) {
        return FileInfoAssembler.INSTANCE.toResponseList(fileInfoRepository.findAllByIdIn(fileIds));
    }

    /**
     * 批量上传文件（原子性：全部成功或全部失败）
     * <p>
     * 校验顺序：凭证有效性 → 文件数量与凭证次数一致 → 业务类型 → 每个文件类型+大小 → 存储+入库 → 一次性消费凭证
     *
     * @return 每个文件的上传结果（fileId + url），顺序与传入文件列表一致
     */
    @Transactional
    public List<FileUploadResponse> batchUploadFiles(long userId, String token, List<MultipartFile> files, String bizType) {
        // 1. 校验凭证有效性
        int remainingCount = credentialService.getRemainingCount(userId, token);
        if (remainingCount < 0) {
            throw new BusinessException(401, "上传凭证无效或已过期");
        }

        // 2. 校验文件数量与凭证次数完全一致
        if (files == null || files.isEmpty()) {
            throw new BusinessException(400, "上传文件列表不能为空");
        }
        if (files.size() != remainingCount) {
            throw new BusinessException(400, "文件数量(" + files.size() + ")与凭证允许次数(" + remainingCount + ")不一致");
        }

        // 3. 校验业务类型
        BizType bizTypeEnum = EnumUtils.valueOf(BizType.class, bizType);

        // 4. 前置校验所有文件的类型和大小
        for (MultipartFile file : files) {
            String contentType = file.getContentType();
            if (contentType == null || !properties.getUpload().getAllowedTypes().contains(contentType)) {
                throw new BusinessException(400, "不支持的文件类型: " + contentType + " (" + file.getOriginalFilename() + ")");
            }
            if (file.getSize() > parseMaxFileSize()) {
                throw new BusinessException(400, "文件大小超过限制: " + file.getOriginalFilename());
            }
        }

        // 5. 全部校验通过，执行存储+入库
        List<FileUploadResponse> results = new ArrayList<>();
        for (MultipartFile file : files) {
            StoredFile storedFile;
            try {
                storedFile = storageProvider.store(
                        bizType,
                        file.getOriginalFilename(),
                        file.getInputStream(),
                        file.getSize(),
                        file.getContentType()
                );
            } catch (IOException e) {
                throw new BusinessException(500, "文件读取失败: " + file.getOriginalFilename());
            }

            FileInfo fileInfo = FileInfo.create(
                    file.getOriginalFilename(),
                    storedFile,
                    bizTypeEnum,
                    userId
            );
            FileInfo saved = fileInfoRepository.save(fileInfo);

            results.add(FileUploadResponse.builder()
                    .fileId(saved.getId())
                    .url(saved.getUrl())
                    .build());
        }

        // 6. 一次性消费全部凭证次数
        credentialService.consumeAll(userId, token);

        return results;
    }

    /**
     * 清理已软删除的磁盘文件（清理成功后物理删除数据库记录）
     */
    public int cleanupDeletedFiles() {
        List<FileInfo> pending = fileInfoRepository.findPendingCleanup();
        int cleaned = 0;
        for (FileInfo fileInfo : pending) {
            try {
                storageProvider.delete(fileInfo.getFilePath());
                fileInfoRepository.deleteById(fileInfo.getId());
                cleaned++;
            } catch (Exception e) {
                // 容忍删除失败，下次重试
            }
        }
        return cleaned;
    }

    /**
     * 解析最大文件大小
     */
    private long parseMaxFileSize() {
        String maxFileSize = properties.getUpload().getMaxFileSize();
        if (maxFileSize.toUpperCase().endsWith("MB")) {
            return Long.parseLong(maxFileSize.toUpperCase().replace("MB", "")) * 1024 * 1024;
        }
        if (maxFileSize.toUpperCase().endsWith("KB")) {
            return Long.parseLong(maxFileSize.toUpperCase().replace("KB", "")) * 1024;
        }
        return Long.parseLong(maxFileSize);
    }
}
