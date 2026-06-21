package com.knowledgegame.core.domain.port.outbound;

/**
 * 文件清理端口（出端口）
 * <p>
 * 解耦 core 模块与 component-feign 的文件删除依赖。
 * admin 模块通过 FileCleanupAdapter 实现此端口，包装 FileServiceClient.deleteFile。
 * purge 时策略调用此端口而非直接调用 Feign Client。
 */
public interface FileCleanupPort {

    /**
     * 删除文件
     *
     * @param fileId 文件 ID
     */
    void deleteFile(Long fileId);
}
