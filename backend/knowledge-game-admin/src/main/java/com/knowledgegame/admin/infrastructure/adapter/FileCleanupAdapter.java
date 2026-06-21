package com.knowledgegame.admin.infrastructure.adapter;

import com.knowledgegame.components.feign.client.FileServiceClient;
import com.knowledgegame.core.common.result.Result;
import com.knowledgegame.core.common.result.ResultCode;
import com.knowledgegame.core.domain.port.outbound.FileCleanupPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 文件清理适配器（admin 模块首个 infrastructure/adapter）
 * <p>
 * 实现 core 模块定义的 FileCleanupPort，包装 FileServiceClient 的 Feign 调用。
 * 文件删除失败仅记录 warn 日志，不阻断 purge 流程。
 */
@Component
public class FileCleanupAdapter implements FileCleanupPort {

    private static final Logger log = LoggerFactory.getLogger(FileCleanupAdapter.class);

    private final FileServiceClient fileServiceClient;

    public FileCleanupAdapter(FileServiceClient fileServiceClient) {
        this.fileServiceClient = fileServiceClient;
    }

    @Override
    public void deleteFile(Long fileId) {
        if (fileId == null) {
            return;
        }
        try {
            Result<Void> result = fileServiceClient.deleteFile(fileId);
            if (result.getCode() != ResultCode.SUCCESS.getCode()) {
                log.warn("文件删除失败: fileId={}, code={}, message={}",
                        fileId, result.getCode(), result.getMessage());
            }
        } catch (Exception e) {
            log.warn("文件删除异常: fileId={}", fileId, e);
        }
    }
}
