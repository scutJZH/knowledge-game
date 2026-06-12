package com.knowledgegame.file.infrastructure.db.repository;

import com.knowledgegame.file.infrastructure.db.entity.FileInfoPO;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 文件信息 JPA Repository
 */
public interface FileInfoJpaRepository extends JpaRepository<FileInfoPO, Long> {

    /**
     * 根据 ID 查询（排除已删除）
     */
    Optional<FileInfoPO> findByIdAndDeletedFalse(Long id);

    /**
     * 根据 ID 列表批量查询（排除已删除）
     */
    List<FileInfoPO> findAllByIdInAndDeletedFalse(List<Long> ids);

    /**
     * 查询已标记删除但磁盘文件未清理的记录
     */
    List<FileInfoPO> findByDeletedTrue();
}
