package com.knowledgegame.core.infrastructure.adapter.repoadapter;

import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.domainenum.IpSeriesStatus;
import com.knowledgegame.core.domain.model.domainenum.ResourceType;
import com.knowledgegame.core.domain.model.entity.IpSeries;
import com.knowledgegame.core.domain.model.entity.RecycleBinItem;
import com.knowledgegame.core.domain.port.outbound.FileCleanupPort;
import com.knowledgegame.core.domain.port.outbound.IpSeriesRepositoryPort;
import com.knowledgegame.core.domain.port.outbound.RecycleBinItemRepositoryPort;
import com.knowledgegame.core.domain.service.IpSeriesDomainService;
import com.knowledgegame.core.domain.service.recyclebin.RecycleBinItemStrategy;
import com.knowledgegame.core.infrastructure.db.entity.IpSeriesDeletedPO;
import com.knowledgegame.core.infrastructure.db.entity.IpSeriesPO;
import com.knowledgegame.core.infrastructure.db.entity.RecycleBinItemPO;
import com.knowledgegame.core.infrastructure.db.repository.IpSeriesDeletedJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.IpSeriesJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.RecycleBinItemJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.time.LocalDateTime;

/**
 * IP 系列回收站策略（首个 RecycleBinItemStrategy 实现）
 * <p>
 * 实现 validateDeletable / moveToRecycleBin / restore / purge 四方法。
 * 读操作走 Port（返回领域对象），写/删操作走 JPA Repository。
 */
@Component
public class IpSeriesRecycleBinStrategy implements RecycleBinItemStrategy<IpSeries> {

    private static final Logger log = LoggerFactory.getLogger(IpSeriesRecycleBinStrategy.class);

    private final IpSeriesDomainService ipSeriesDomainService;
    private final IpSeriesRepositoryPort ipSeriesRepositoryPort;
    private final RecycleBinItemRepositoryPort recycleBinItemRepositoryPort;
    private final IpSeriesJpaRepository ipSeriesJpaRepository;
    private final IpSeriesDeletedJpaRepository ipSeriesDeletedJpaRepository;
    private final RecycleBinItemJpaRepository recycleBinItemJpaRepository;
    private final FileCleanupPort fileCleanupPort;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 供测试注入 mock EntityManager（纯 Mockito 测试无 Spring 容器时使用）
     */
    public void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public IpSeriesRecycleBinStrategy(IpSeriesDomainService ipSeriesDomainService,
                                       IpSeriesRepositoryPort ipSeriesRepositoryPort,
                                       RecycleBinItemRepositoryPort recycleBinItemRepositoryPort,
                                       IpSeriesJpaRepository ipSeriesJpaRepository,
                                       IpSeriesDeletedJpaRepository ipSeriesDeletedJpaRepository,
                                       RecycleBinItemJpaRepository recycleBinItemJpaRepository,
                                       Optional<FileCleanupPort> fileCleanupPort) {
        this.ipSeriesDomainService = ipSeriesDomainService;
        this.ipSeriesRepositoryPort = ipSeriesRepositoryPort;
        this.recycleBinItemRepositoryPort = recycleBinItemRepositoryPort;
        this.ipSeriesJpaRepository = ipSeriesJpaRepository;
        this.ipSeriesDeletedJpaRepository = ipSeriesDeletedJpaRepository;
        this.recycleBinItemJpaRepository = recycleBinItemJpaRepository;
        this.fileCleanupPort = fileCleanupPort.orElse(null);
    }

    @Override
    public ResourceType getResourceType() {
        return ResourceType.IP_SERIES;
    }

    @Override
    public void validateDeletable(Long originalId) {
        try {
            ipSeriesDomainService.validateDeactivatable(originalId);
        } catch (BusinessException e) {
            throw new BusinessException(e.getMessage().replace("无法停用", "无法删除"));
        }
    }

    @Override
    public void moveToRecycleBin(Long originalId, String deletedBy) {
        IpSeries ipSeries = ipSeriesRepositoryPort.findById(originalId)
                .orElseThrow(() -> new BusinessException("IP 系列不存在: " + originalId));

        IpSeriesDeletedPO deletedPO = new IpSeriesDeletedPO();
        deletedPO.setOriginalId(originalId);
        deletedPO.setCode(ipSeries.getCode());
        deletedPO.setName(ipSeries.getName());
        deletedPO.setDescription(ipSeries.getDescription());
        if (ipSeries.getCoverImage() != null) {
            deletedPO.setCoverImageFileId(ipSeries.getCoverImage().fileId());
            deletedPO.setCoverImageUrl(ipSeries.getCoverImage().url());
        }
        deletedPO.setStatus(ipSeries.getStatus());
        deletedPO.setCreatedAt(ipSeries.getCreatedAt());
        deletedPO.setUpdatedAt(ipSeries.getUpdatedAt());
        deletedPO.setRelatedData(null);
        deletedPO.setDeletedBy(deletedBy);
        deletedPO.setDeletedAt(LocalDateTime.now());
        ipSeriesDeletedJpaRepository.save(deletedPO);

        ipSeriesJpaRepository.deleteById(originalId);

        RecycleBinItemPO recycleBinPO = new RecycleBinItemPO();
        recycleBinPO.setResourceType(ResourceType.IP_SERIES);
        recycleBinPO.setOriginalId(originalId);
        recycleBinPO.setOriginalName(ipSeries.getName());
        recycleBinPO.setOriginalCreatedAt(ipSeries.getCreatedAt());
        recycleBinPO.setOriginalUpdatedAt(ipSeries.getUpdatedAt());
        recycleBinPO.setDeletedBy(deletedBy);
        recycleBinPO.setDeletedAt(LocalDateTime.now());
        recycleBinPO.setRestoreDeadline(LocalDateTime.now().plusDays(30));
        recycleBinItemJpaRepository.save(recycleBinPO);
    }

    @Override
    public void restore(Long recycleBinId) {
        RecycleBinItem recycleBinItem = recycleBinItemRepositoryPort.findById(recycleBinId)
                .orElseThrow(() -> new BusinessException("回收站记录不存在: " + recycleBinId));

        Long originalId = recycleBinItem.getOriginalId();
        IpSeriesDeletedPO deletedPO = ipSeriesDeletedJpaRepository.findByOriginalId(originalId)
                .orElseThrow(() -> new BusinessException("IP 系列快照不存在: " + originalId));

        ipSeriesJpaRepository.findByCode(deletedPO.getCode()).ifPresent(po -> {
            throw new BusinessException("IP 系列编码已存在，无法恢复: " + deletedPO.getCode());
        });
        ipSeriesJpaRepository.findByName(deletedPO.getName()).ifPresent(po -> {
            throw new BusinessException("IP 系列名称已存在，无法恢复: " + deletedPO.getName());
        });

        IpSeriesPO restoredPO = new IpSeriesPO();
        restoredPO.setId(originalId);
        restoredPO.setCode(deletedPO.getCode());
        restoredPO.setName(deletedPO.getName());
        restoredPO.setDescription(deletedPO.getDescription());
        restoredPO.setCoverImageFileId(deletedPO.getCoverImageFileId());
        restoredPO.setCoverImageUrl(deletedPO.getCoverImageUrl());
        restoredPO.setStatus(IpSeriesStatus.INACTIVE);
        restoredPO.setCreatedAt(deletedPO.getCreatedAt());
        restoredPO.setUpdatedAt(LocalDateTime.now());

        entityManager.createNativeQuery(
                "INSERT INTO ip_series (id, code, name, description, cover_image_file_id, "
                        + "cover_image_url, status, created_at, updated_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?)")
                .setParameter(1, restoredPO.getId())
                .setParameter(2, restoredPO.getCode())
                .setParameter(3, restoredPO.getName())
                .setParameter(4, restoredPO.getDescription())
                .setParameter(5, restoredPO.getCoverImageFileId())
                .setParameter(6, restoredPO.getCoverImageUrl())
                .setParameter(7, restoredPO.getStatus().name())
                .setParameter(8, restoredPO.getCreatedAt())
                .setParameter(9, restoredPO.getUpdatedAt())
                .executeUpdate();

        ipSeriesDeletedJpaRepository.deleteById(deletedPO.getId());
        recycleBinItemJpaRepository.deleteById(recycleBinId);
    }

    @Override
    public void purge(Long recycleBinId) {
        RecycleBinItem recycleBinItem = recycleBinItemRepositoryPort.findById(recycleBinId)
                .orElseThrow(() -> new BusinessException("回收站记录不存在: " + recycleBinId));

        Long originalId = recycleBinItem.getOriginalId();
        IpSeriesDeletedPO deletedPO = ipSeriesDeletedJpaRepository.findByOriginalId(originalId)
                .orElse(null);

        if (fileCleanupPort != null && deletedPO != null && deletedPO.getCoverImageFileId() != null) {
            try {
                fileCleanupPort.deleteFile(deletedPO.getCoverImageFileId());
            } catch (Exception e) {
                log.warn("文件清理失败: originalId={}, fileId={}", originalId,
                        deletedPO.getCoverImageFileId(), e);
            }
        }

        if (deletedPO != null) {
            ipSeriesDeletedJpaRepository.deleteById(deletedPO.getId());
        }
        recycleBinItemJpaRepository.deleteById(recycleBinId);
    }
}
