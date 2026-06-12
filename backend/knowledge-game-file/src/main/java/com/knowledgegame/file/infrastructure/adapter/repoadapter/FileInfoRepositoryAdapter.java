package com.knowledgegame.file.infrastructure.adapter.repoadapter;

import com.knowledgegame.file.domain.model.FileInfo;
import com.knowledgegame.file.domain.port.outbound.FileInfoRepository;
import com.knowledgegame.file.infrastructure.db.converter.FileInfoConverter;
import com.knowledgegame.file.infrastructure.db.entity.FileInfoPO;
import com.knowledgegame.file.infrastructure.db.repository.FileInfoJpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 文件信息仓储适配器
 */
public class FileInfoRepositoryAdapter implements FileInfoRepository {

    private final FileInfoJpaRepository jpaRepository;
    private final FileInfoConverter converter;

    public FileInfoRepositoryAdapter(FileInfoJpaRepository jpaRepository, FileInfoConverter converter) {
        this.jpaRepository = jpaRepository;
        this.converter = converter;
    }

    @Override
    public FileInfo save(FileInfo fileInfo) {
        FileInfoPO po = converter.toPO(fileInfo);
        FileInfoPO saved = jpaRepository.save(po);
        return converter.toDomain(saved);
    }

    @Override
    public Optional<FileInfo> findById(Long id) {
        return jpaRepository.findByIdAndDeletedFalse(id)
                .map(converter::toDomain);
    }

    @Override
    public List<FileInfo> findAllByIdIn(List<Long> ids) {
        return converter.toDomainList(jpaRepository.findAllByIdInAndDeletedFalse(ids));
    }

    @Override
    public List<FileInfo> findPendingCleanup() {
        return converter.toDomainList(jpaRepository.findByDeletedTrue());
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }
}
