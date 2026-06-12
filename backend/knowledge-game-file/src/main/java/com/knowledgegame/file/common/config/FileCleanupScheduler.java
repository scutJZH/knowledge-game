package com.knowledgegame.file.common.config;

import com.knowledgegame.file.application.FileAppService;
import com.knowledgegame.file.domain.service.UploadCredentialService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 文件服务定时任务
 */
@Component
public class FileCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(FileCleanupScheduler.class);

    private final UploadCredentialService credentialService;
    private final FileAppService fileAppService;

    public FileCleanupScheduler(UploadCredentialService credentialService, FileAppService fileAppService) {
        this.credentialService = credentialService;
        this.fileAppService = fileAppService;
    }

    /**
     * 每分钟清理过期的上传凭证
     */
    @Scheduled(fixedRate = 60_000)
    public void cleanupExpiredCredentials() {
        int cleaned = credentialService.cleanupExpired();
        if (cleaned > 0) {
            log.info("清理过期上传凭证: {} 条", cleaned);
        }
    }

    /**
     * 每小时清理已软删除的磁盘文件
     */
    @Scheduled(fixedRate = 3_600_000)
    public void cleanupDeletedFiles() {
        int cleaned = fileAppService.cleanupDeletedFiles();
        if (cleaned > 0) {
            log.info("清理已删除磁盘文件: {} 个", cleaned);
        }
    }
}
