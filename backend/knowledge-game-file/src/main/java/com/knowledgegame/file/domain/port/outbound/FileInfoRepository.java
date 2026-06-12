package com.knowledgegame.file.domain.port.outbound;

import com.knowledgegame.file.domain.model.FileInfo;

import java.util.List;
import java.util.Optional;

/**
 * 文件信息仓储端口
 */
public interface FileInfoRepository {

    /**
     * 保存文件信息
     */
    FileInfo save(FileInfo fileInfo);

    /**
     * 根据 ID 查询（排除已删除）
     */
    Optional<FileInfo> findById(Long id);

    /**
     * 根据 ID 列表批量查询（排除已删除）
     */
    List<FileInfo> findAllByIdIn(List<Long> ids);

    /**
     * 查询已标记删除但磁盘文件未清理的记录
     */
    List<FileInfo> findPendingCleanup();

    /**
     * 物理删除记录（磁盘文件清理完成后调用）
     */
    void deleteById(Long id);
}
