package com.knowledgegame.core.infrastructure.adapter.repoadapter;

import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.domainenum.CardTemplateStatus;
import com.knowledgegame.core.domain.model.domainenum.ResourceType;
import com.knowledgegame.core.domain.model.entity.CardTemplate;
import com.knowledgegame.core.domain.model.entity.RecycleBinItem;
import com.knowledgegame.core.domain.port.outbound.CardTemplateRepositoryPort;
import com.knowledgegame.core.domain.port.outbound.FileCleanupPort;
import com.knowledgegame.core.domain.port.outbound.IpSeriesRepositoryPort;
import com.knowledgegame.core.domain.port.outbound.RecycleBinItemRepositoryPort;
import com.knowledgegame.core.domain.service.recyclebin.RecycleBinItemStrategy;
import com.knowledgegame.core.infrastructure.db.entity.CardTemplateDeletedPO;
import com.knowledgegame.core.infrastructure.db.entity.RecycleBinItemPO;
import com.knowledgegame.core.infrastructure.db.repository.CardTemplateDeletedJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.CardTemplateJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.RecycleBinItemJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * 卡牌模板回收站策略
 * <p>
 * 实现 validateDeletable / moveToRecycleBin / restore / purge 四方法。
 * 读操作走 Port（返回领域对象），写/删操作走 JPA Repository。
 * <p>
 * 由 admin 模块通过 @Bean 显式注册，不标记 @Component。
 */
public class CardTemplateRecycleBinStrategy implements RecycleBinItemStrategy<CardTemplate> {

    private static final Logger log = LoggerFactory.getLogger(CardTemplateRecycleBinStrategy.class);

    private final CardTemplateRepositoryPort cardTemplateRepositoryPort;
    private final RecycleBinItemRepositoryPort recycleBinItemRepositoryPort;
    private final CardTemplateJpaRepository cardTemplateJpaRepository;
    private final CardTemplateDeletedJpaRepository cardTemplateDeletedJpaRepository;
    private final RecycleBinItemJpaRepository recycleBinItemJpaRepository;
    private final IpSeriesRepositoryPort ipSeriesRepositoryPort;
    private final FileCleanupPort fileCleanupPort;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 供测试注入 mock EntityManager（纯 Mockito 测试无 Spring 容器时使用）
     */
    public void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public CardTemplateRecycleBinStrategy(CardTemplateRepositoryPort cardTemplateRepositoryPort,
                                           RecycleBinItemRepositoryPort recycleBinItemRepositoryPort,
                                           CardTemplateJpaRepository cardTemplateJpaRepository,
                                           CardTemplateDeletedJpaRepository cardTemplateDeletedJpaRepository,
                                           RecycleBinItemJpaRepository recycleBinItemJpaRepository,
                                           IpSeriesRepositoryPort ipSeriesRepositoryPort,
                                           FileCleanupPort fileCleanupPort) {
        this.cardTemplateRepositoryPort = cardTemplateRepositoryPort;
        this.recycleBinItemRepositoryPort = recycleBinItemRepositoryPort;
        this.cardTemplateJpaRepository = cardTemplateJpaRepository;
        this.cardTemplateDeletedJpaRepository = cardTemplateDeletedJpaRepository;
        this.recycleBinItemJpaRepository = recycleBinItemJpaRepository;
        this.ipSeriesRepositoryPort = ipSeriesRepositoryPort;
        this.fileCleanupPort = fileCleanupPort;
    }

    @Override
    public ResourceType getResourceType() {
        return ResourceType.CARD_TEMPLATE;
    }

    @Override
    public void validateDeletable(Long originalId) {
        if (!cardTemplateRepositoryPort.existsById(originalId)) {
            throw new BusinessException("卡牌模板不存在: " + originalId);
        }
    }

    @Override
    public void moveToRecycleBin(Long originalId, String deletedBy) {
        CardTemplate template = cardTemplateRepositoryPort.findById(originalId)
                .orElseThrow(() -> new BusinessException("卡牌模板不存在: " + originalId));

        CardTemplateDeletedPO deletedPO = new CardTemplateDeletedPO();
        deletedPO.setOriginalId(originalId);
        deletedPO.setIpSeriesId(template.getIpSeriesId());
        ipSeriesRepositoryPort.findById(template.getIpSeriesId())
                .ifPresent(ip -> deletedPO.setIpSeriesName(ip.getName()));
        deletedPO.setCode(template.getCode());
        deletedPO.setName(template.getName());
        deletedPO.setRarity(template.getRarity());
        deletedPO.setDescription(template.getDescription());
        if (template.getImage() != null) {
            deletedPO.setImageFileId(template.getImage().fileId());
            deletedPO.setImageUrl(template.getImage().url());
        }
        deletedPO.setStatus(template.getStatus());
        deletedPO.setCreatedAt(template.getCreatedAt());
        deletedPO.setUpdatedAt(template.getUpdatedAt());
        deletedPO.setRelatedData(null);
        deletedPO.setDeletedBy(deletedBy);
        deletedPO.setDeletedAt(LocalDateTime.now());
        cardTemplateDeletedJpaRepository.save(deletedPO);

        cardTemplateJpaRepository.deleteById(originalId);

        RecycleBinItemPO recycleBinPO = new RecycleBinItemPO();
        recycleBinPO.setResourceType(ResourceType.CARD_TEMPLATE);
        recycleBinPO.setOriginalId(originalId);
        recycleBinPO.setOriginalName(template.getName());
        recycleBinPO.setOriginalCreatedAt(template.getCreatedAt());
        recycleBinPO.setOriginalUpdatedAt(template.getUpdatedAt());
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
        CardTemplateDeletedPO deletedPO = cardTemplateDeletedJpaRepository.findByOriginalId(originalId)
                .orElseThrow(() -> new BusinessException("卡牌模板快照不存在: " + originalId));

        if (!ipSeriesRepositoryPort.existsById(deletedPO.getIpSeriesId())) {
            String ipDesc = deletedPO.getIpSeriesName() != null
                    ? "《" + deletedPO.getIpSeriesName() + "》(ID=" + deletedPO.getIpSeriesId() + ")"
                    : "ID=" + deletedPO.getIpSeriesId();
            throw new BusinessException("卡牌模板关联的 IP 系列" + ipDesc + " 已不存在，无法恢复");
        }

        cardTemplateJpaRepository.findByIpSeriesIdAndCode(
                deletedPO.getIpSeriesId(), deletedPO.getCode()).ifPresent(po -> {
            throw new BusinessException("卡牌编码已存在，无法恢复: " + deletedPO.getCode());
        });

        entityManager.createNativeQuery(
                "INSERT INTO card_template (id, ip_series_id, code, name, rarity, "
                        + "description, status, image_file_id, image_url, created_at, updated_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?)")
                .setParameter(1, originalId)
                .setParameter(2, deletedPO.getIpSeriesId())
                .setParameter(3, deletedPO.getCode())
                .setParameter(4, deletedPO.getName())
                .setParameter(5, deletedPO.getRarity().name())
                .setParameter(6, deletedPO.getDescription())
                .setParameter(7, CardTemplateStatus.INACTIVE.name())
                .setParameter(8, deletedPO.getImageFileId())
                .setParameter(9, deletedPO.getImageUrl())
                .setParameter(10, deletedPO.getCreatedAt())
                .setParameter(11, LocalDateTime.now())
                .executeUpdate();

        cardTemplateDeletedJpaRepository.deleteById(deletedPO.getId());
        recycleBinItemJpaRepository.deleteById(recycleBinId);
    }

    @Override
    public void purge(Long recycleBinId) {
        RecycleBinItem recycleBinItem = recycleBinItemRepositoryPort.findById(recycleBinId)
                .orElseThrow(() -> new BusinessException("回收站记录不存在: " + recycleBinId));

        Long originalId = recycleBinItem.getOriginalId();
        CardTemplateDeletedPO deletedPO = cardTemplateDeletedJpaRepository.findByOriginalId(originalId)
                .orElse(null);

        if (deletedPO != null && deletedPO.getImageFileId() != null) {
            try {
                fileCleanupPort.deleteFile(deletedPO.getImageFileId());
            } catch (Exception e) {
                log.warn("文件清理失败: originalId={}, fileId={}", originalId,
                        deletedPO.getImageFileId(), e);
            }
        }

        if (deletedPO != null) {
            cardTemplateDeletedJpaRepository.deleteById(deletedPO.getId());
        }
        recycleBinItemJpaRepository.deleteById(recycleBinId);
    }
}
