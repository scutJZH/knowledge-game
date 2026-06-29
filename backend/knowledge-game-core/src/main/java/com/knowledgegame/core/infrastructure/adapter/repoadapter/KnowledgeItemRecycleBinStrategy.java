package com.knowledgegame.core.infrastructure.adapter.repoadapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.domainenum.KnowledgeItemStatus;
import com.knowledgegame.core.domain.model.domainenum.ResourceType;
import com.knowledgegame.core.domain.model.entity.KnowledgeItem;
import com.knowledgegame.core.domain.model.entity.RecycleBinItem;
import com.knowledgegame.core.domain.model.vo.FileRef;
import com.knowledgegame.core.domain.port.outbound.FileCleanupPort;
import com.knowledgegame.core.domain.port.outbound.KnowledgeItemRepository;
import com.knowledgegame.core.domain.port.outbound.RecycleBinItemRepositoryPort;
import com.knowledgegame.core.domain.service.recyclebin.RecycleBinItemStrategy;
import com.knowledgegame.core.infrastructure.db.entity.KnowledgeItemCategoryRelationPO;
import com.knowledgegame.core.infrastructure.db.entity.KnowledgeItemDeletedPO;
import com.knowledgegame.core.infrastructure.db.entity.RecycleBinItemPO;
import com.knowledgegame.core.infrastructure.db.entity.KnowledgeCategoryPO;
import com.knowledgegame.core.infrastructure.db.repository.KnowledgeCategoryJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.KnowledgeItemCategoryRelationJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.KnowledgeItemDeletedJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.KnowledgeItemJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.RecycleBinItemJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 知识条目回收站策略
 * <p>
 * 实现 validateDeletable / moveToRecycleBin / restore / purge 四方法。
 * 读操作走 Port（返回领域对象），写/删操作走 JPA Repository。
 * KnowledgeItem 有 coverImage（FileRef），purge 时需调用 FileCleanupPort 清理封面图。
 * <p>
 * 由 admin 模块通过 @Bean 显式注册，不标记 @Component。
 */
public class KnowledgeItemRecycleBinStrategy implements RecycleBinItemStrategy<KnowledgeItem> {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeItemRecycleBinStrategy.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final KnowledgeItemRepository itemRepository;
    private final RecycleBinItemRepositoryPort recycleBinItemRepositoryPort;
    private final KnowledgeItemJpaRepository itemJpaRepository;
    private final KnowledgeItemDeletedJpaRepository itemDeletedJpaRepository;
    private final KnowledgeItemCategoryRelationJpaRepository relationJpaRepository;
    private final RecycleBinItemJpaRepository recycleBinItemJpaRepository;
    private final KnowledgeCategoryJpaRepository categoryJpaRepository;
    private final FileCleanupPort fileCleanupPort;

    @PersistenceContext
    private EntityManager entityManager;

    public void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public KnowledgeItemRecycleBinStrategy(KnowledgeItemRepository itemRepository,
                                           RecycleBinItemRepositoryPort recycleBinItemRepositoryPort,
                                           KnowledgeItemJpaRepository itemJpaRepository,
                                           KnowledgeItemDeletedJpaRepository itemDeletedJpaRepository,
                                           KnowledgeItemCategoryRelationJpaRepository relationJpaRepository,
                                           RecycleBinItemJpaRepository recycleBinItemJpaRepository,
                                           KnowledgeCategoryJpaRepository categoryJpaRepository,
                                           FileCleanupPort fileCleanupPort) {
        this.itemRepository = itemRepository;
        this.recycleBinItemRepositoryPort = recycleBinItemRepositoryPort;
        this.itemJpaRepository = itemJpaRepository;
        this.itemDeletedJpaRepository = itemDeletedJpaRepository;
        this.relationJpaRepository = relationJpaRepository;
        this.recycleBinItemJpaRepository = recycleBinItemJpaRepository;
        this.categoryJpaRepository = categoryJpaRepository;
        this.fileCleanupPort = fileCleanupPort;
    }

    @Override
    public ResourceType getResourceType() {
        return ResourceType.KNOWLEDGE_ITEM;
    }

    @Override
    public void validateDeletable(Long originalId) {
        if (itemRepository.findById(originalId).isEmpty()) {
            throw new BusinessException("知识条目不存在: " + originalId);
        }
    }

    @Override
    public void moveToRecycleBin(Long originalId, String deletedBy) {
        KnowledgeItem item = itemRepository.findById(originalId)
                .orElseThrow(() -> new BusinessException("知识条目不存在: " + originalId));

        List<KnowledgeItemCategoryRelationPO> relations = relationJpaRepository.findByItemId(originalId);
        List<Long> categoryIds = relations.stream()
                .map(KnowledgeItemCategoryRelationPO::getCategoryId)
                .toList();
        Map<Long, String> idToName = categoryIds.isEmpty()
                ? Map.of()
                : categoryJpaRepository.findAllById(categoryIds).stream()
                        .collect(Collectors.toMap(KnowledgeCategoryPO::getId, KnowledgeCategoryPO::getName));

        KnowledgeItemDeletedPO deletedPO = new KnowledgeItemDeletedPO();
        deletedPO.setOriginalId(originalId);
        deletedPO.setTitle(item.getTitle());
        deletedPO.setContent(item.getContent());
        deletedPO.setContentHtml(item.getContentHtml());
        FileRef coverImage = item.getCoverImage();
        deletedPO.setCoverImageFileId(coverImage != null ? coverImage.fileId() : null);
        deletedPO.setCoverImageUrl(coverImage != null ? coverImage.url() : null);
        deletedPO.setTags(serializeTags(item.getTags()));
        deletedPO.setSortOrder(item.getSortOrder());
        deletedPO.setStatus(item.getStatus());
        deletedPO.setCreatedAt(item.getCreatedAt());
        deletedPO.setUpdatedAt(item.getUpdatedAt());
        deletedPO.setRelatedData(writeCategoryIds(categoryIds, idToName));
        deletedPO.setDeletedBy(deletedBy);
        deletedPO.setDeletedAt(LocalDateTime.now());
        itemDeletedJpaRepository.save(deletedPO);

        relationJpaRepository.deleteByItemId(originalId);
        itemJpaRepository.deleteById(originalId);

        RecycleBinItemPO recycleBinPO = new RecycleBinItemPO();
        recycleBinPO.setResourceType(ResourceType.KNOWLEDGE_ITEM);
        recycleBinPO.setOriginalId(originalId);
        recycleBinPO.setOriginalName(item.getTitle());
        recycleBinPO.setOriginalCreatedAt(item.getCreatedAt());
        recycleBinPO.setOriginalUpdatedAt(item.getUpdatedAt());
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
        KnowledgeItemDeletedPO deletedPO = itemDeletedJpaRepository.findByOriginalId(originalId)
                .orElseThrow(() -> new BusinessException("知识条目快照不存在: " + originalId));

        List<Long> categoryIds = parseCategoryIds(deletedPO.getRelatedData());
        if (categoryIds != null && !categoryIds.isEmpty()) {
            Set<Long> existingIds = categoryJpaRepository.findAllById(categoryIds).stream()
                    .map(KnowledgeCategoryPO::getId).collect(Collectors.toSet());
            if (existingIds.size() != categoryIds.size()) {
                Map<Long, String> snapshotNames = parseCategoryNames(deletedPO.getRelatedData());
                String missingDesc = categoryIds.stream()
                        .filter(id -> !existingIds.contains(id))
                        .map(id -> {
                            if (snapshotNames != null && snapshotNames.containsKey(id)) {
                                return snapshotNames.get(id) + "(ID=" + id + ")";
                            }
                            return "#" + id;
                        })
                        .collect(Collectors.joining(", "));
                throw new BusinessException("知识条目关联的分类已被删除，无法恢复。缺失分类: " + missingDesc);
            }
        }

        entityManager.createNativeQuery(
                "INSERT INTO knowledge_item (id, title, content, content_html, "
                        + "cover_image_file_id, cover_image_url, tags, sort_order, "
                        + "status, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)")
                .setParameter(1, originalId)
                .setParameter(2, deletedPO.getTitle())
                .setParameter(3, deletedPO.getContent())
                .setParameter(4, deletedPO.getContentHtml())
                .setParameter(5, deletedPO.getCoverImageFileId())
                .setParameter(6, deletedPO.getCoverImageUrl())
                .setParameter(7, deletedPO.getTags())
                .setParameter(8, deletedPO.getSortOrder())
                .setParameter(9, KnowledgeItemStatus.INACTIVE.name())
                .setParameter(10, deletedPO.getCreatedAt())
                .setParameter(11, LocalDateTime.now())
                .executeUpdate();

        if (categoryIds != null && !categoryIds.isEmpty()) {
            itemRepository.saveCategoryRelations(originalId, categoryIds);
        }

        itemDeletedJpaRepository.deleteById(deletedPO.getId());
        recycleBinItemJpaRepository.deleteById(recycleBinId);
    }

    @Override
    public void purge(Long recycleBinId) {
        RecycleBinItem recycleBinItem = recycleBinItemRepositoryPort.findById(recycleBinId)
                .orElseThrow(() -> new BusinessException("回收站记录不存在: " + recycleBinId));

        Long originalId = recycleBinItem.getOriginalId();
        KnowledgeItemDeletedPO deletedPO = itemDeletedJpaRepository.findByOriginalId(originalId)
                .orElse(null);

        if (deletedPO == null) {
            recycleBinItemJpaRepository.deleteById(recycleBinId);
            return;
        }

        if (deletedPO.getCoverImageFileId() != null) {
            try {
                fileCleanupPort.deleteFile(deletedPO.getCoverImageFileId());
            } catch (Exception e) {
                log.warn("封面图清理失败: originalId={}, coverImageFileId={}",
                        originalId, deletedPO.getCoverImageFileId(), e);
            }
        }

        itemDeletedJpaRepository.deleteById(deletedPO.getId());
        recycleBinItemJpaRepository.deleteById(recycleBinId);
    }

    private static String serializeTags(List<String> tags) {
        if (tags == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(tags);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("tags JSON 序列化失败", e);
        }
    }

    public static String writeCategoryIds(List<Long> categoryIds, Map<Long, String> idToName) {
        try {
            Map<String, Object> data = new java.util.HashMap<>();
            data.put("categoryAssociationIds", categoryIds);
            data.put("categoryAssociationNames", idToName);
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            throw new RuntimeException("JSON 序列化 categoryIds 失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    static List<Long> parseCategoryIds(String relatedData) {
        if (relatedData == null) {
            return null;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(relatedData, Map.class);
            List<Object> raw = (List<Object>) map.get("categoryAssociationIds");
            if (raw == null) {
                return null;
            }
            return raw.stream().map(o -> ((Number) o).longValue()).toList();
        } catch (Exception e) {
            throw new BusinessException("回收站快照数据已损坏（related_data JSON 解析失败），无法恢复，请使用永久删除");
        }
    }

    @SuppressWarnings("unchecked")
    static Map<Long, String> parseCategoryNames(String relatedData) {
        if (relatedData == null) {
            return null;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(relatedData, Map.class);
            Map<String, Object> raw = (Map<String, Object>) map.get("categoryAssociationNames");
            if (raw == null) {
                return null;
            }
            Map<Long, String> result = new java.util.HashMap<>();
            for (Map.Entry<String, Object> e : raw.entrySet()) {
                result.put(Long.parseLong(e.getKey()), (String) e.getValue());
            }
            return result;
        } catch (Exception e) {
            return null; // 兼容旧格式（无 names 字段），返回 null 由调用方降级处理
        }
    }
}
