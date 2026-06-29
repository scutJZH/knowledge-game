package com.knowledgegame.core.infrastructure.adapter.repoadapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.domainenum.KnowledgeCategoryStatus;
import com.knowledgegame.core.domain.model.domainenum.ResourceType;
import com.knowledgegame.core.domain.model.entity.KnowledgeCategory;
import com.knowledgegame.core.domain.model.entity.RecycleBinItem;
import com.knowledgegame.core.domain.port.outbound.FileCleanupPort;
import com.knowledgegame.core.domain.port.outbound.KnowledgeCategoryRepositoryPort;
import com.knowledgegame.core.domain.port.outbound.KnowledgeItemRepository;
import com.knowledgegame.core.domain.port.outbound.QuestionRepository;
import com.knowledgegame.core.domain.port.outbound.RecycleBinItemRepositoryPort;
import com.knowledgegame.core.domain.service.recyclebin.RecycleBinItemStrategy;
import com.knowledgegame.core.infrastructure.db.entity.KnowledgeCategoryDeletedPO;
import com.knowledgegame.core.infrastructure.db.entity.RecycleBinItemPO;
import com.knowledgegame.core.infrastructure.db.repository.KnowledgeCategoryDeletedJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.KnowledgeCategoryJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.RecycleBinItemJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 知识分类回收站策略（首个递归子树删除策略）
 * <p>
 * 实现 validateDeletable / moveToRecycleBin / restore / purge 四方法。
 * 读操作走 Port（返回领域对象），写/删操作走 JPA Repository。
 * <p>
 * 由 admin 模块通过 @Bean 显式注册，不标记 @Component。
 */
public class KnowledgeCategoryRecycleBinStrategy implements RecycleBinItemStrategy<KnowledgeCategory> {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeCategoryRecycleBinStrategy.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final KnowledgeCategoryRepositoryPort categoryRepositoryPort;
    private final QuestionRepository questionRepository;
    private final KnowledgeItemRepository itemRepository;
    private final RecycleBinItemRepositoryPort recycleBinItemRepositoryPort;
    private final KnowledgeCategoryJpaRepository categoryJpaRepository;
    private final KnowledgeCategoryDeletedJpaRepository deletedJpaRepository;
    private final RecycleBinItemJpaRepository recycleBinItemJpaRepository;
    private final FileCleanupPort fileCleanupPort;

    @PersistenceContext
    private EntityManager entityManager;

    public void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public KnowledgeCategoryRecycleBinStrategy(
            KnowledgeCategoryRepositoryPort categoryRepositoryPort,
            QuestionRepository questionRepository,
            KnowledgeItemRepository itemRepository,
            RecycleBinItemRepositoryPort recycleBinItemRepositoryPort,
            KnowledgeCategoryJpaRepository categoryJpaRepository,
            KnowledgeCategoryDeletedJpaRepository deletedJpaRepository,
            RecycleBinItemJpaRepository recycleBinItemJpaRepository,
            FileCleanupPort fileCleanupPort) {
        this.categoryRepositoryPort = categoryRepositoryPort;
        this.questionRepository = questionRepository;
        this.itemRepository = itemRepository;
        this.recycleBinItemRepositoryPort = recycleBinItemRepositoryPort;
        this.categoryJpaRepository = categoryJpaRepository;
        this.deletedJpaRepository = deletedJpaRepository;
        this.recycleBinItemJpaRepository = recycleBinItemJpaRepository;
        this.fileCleanupPort = fileCleanupPort;
    }

    @Override
    public ResourceType getResourceType() {
        return ResourceType.KNOWLEDGE_CATEGORY;
    }

    @Override
    public void validateDeletable(Long originalId) {
        KnowledgeCategory category = categoryRepositoryPort.findById(originalId)
                .orElseThrow(() -> new BusinessException("知识点分类不存在: " + originalId));

        List<Long> subtreeIds = new ArrayList<>(categoryRepositoryPort.findDescendantIds(originalId));
        subtreeIds.add(originalId);

        // 批量加载子树名称，用于异常消息定位
        Map<Long, String> nameMap = new LinkedHashMap<>();
        List<KnowledgeCategory> allCategories = categoryRepositoryPort.findAllByIdIn(subtreeIds);
        for (KnowledgeCategory c : allCategories) {
            nameMap.put(c.getId(), c.getName());
        }

        for (Long id : subtreeIds) {
            String catName = nameMap.getOrDefault(id, "ID=" + id);
            long questionCount = questionRepository.countByCategoryId(id);
            if (questionCount > 0) {
                throw new BusinessException("知识点分类「" + catName + "」关联 " + questionCount
                        + " 道题目，无法删除");
            }
            long itemCount = itemRepository.countByCategoryId(id);
            if (itemCount > 0) {
                throw new BusinessException("知识点分类「" + catName + "」关联 " + itemCount
                        + " 个知识条目，无法删除");
            }
        }
    }

    @Override
    public void moveToRecycleBin(Long originalId, String deletedBy) {
        KnowledgeCategory root = categoryRepositoryPort.findById(originalId)
                .orElseThrow(() -> new BusinessException("知识点分类不存在: " + originalId));

        List<Long> descendantIds = categoryRepositoryPort.findDescendantIds(originalId);
        List<Long> subtreeIds = new ArrayList<>(descendantIds);
        subtreeIds.add(originalId);

        List<KnowledgeCategory> allCategories = categoryRepositoryPort.findAllByIdIn(subtreeIds);
        Map<Long, KnowledgeCategory> categoryMap = new LinkedHashMap<>();
        for (KnowledgeCategory c : allCategories) {
            categoryMap.put(c.getId(), c);
        }

        List<KnowledgeCategoryDeletedPO> deletedPOs = new ArrayList<>();
        for (Long id : subtreeIds) {
            KnowledgeCategory cat = categoryMap.get(id);
            KnowledgeCategoryDeletedPO po = new KnowledgeCategoryDeletedPO();
            po.setOriginalId(id);
            po.setParentId(cat.getParentId());
            po.setName(cat.getName());
            po.setDescription(cat.getDescription());
            if (cat.getIcon() != null) {
                po.setIconFileId(cat.getIcon().fileId());
                po.setIconUrl(cat.getIcon().url());
            }
            po.setColor(cat.getColor());
            if (cat.getCoverImage() != null) {
                po.setCoverImageFileId(cat.getCoverImage().fileId());
                po.setCoverImageUrl(cat.getCoverImage().url());
            }
            po.setSortOrder(cat.getSortOrder());
            po.setStatus(cat.getStatus());
            po.setCreatedAt(cat.getCreatedAt());
            po.setUpdatedAt(cat.getUpdatedAt());
            // 主节点存子树 ID 列表，子节点为 null
            po.setRelatedData(Objects.equals(id, originalId) ? writeSubtreeIds(subtreeIds) : null);
            po.setDeletedBy(deletedBy);
            po.setDeletedAt(LocalDateTime.now());
            deletedPOs.add(po);
        }
        deletedJpaRepository.saveAll(deletedPOs);

        categoryJpaRepository.deleteAllById(subtreeIds);

        RecycleBinItemPO recycleBinPO = new RecycleBinItemPO();
        recycleBinPO.setResourceType(ResourceType.KNOWLEDGE_CATEGORY);
        recycleBinPO.setOriginalId(originalId);
        recycleBinPO.setOriginalName(root.getName());
        recycleBinPO.setOriginalCreatedAt(root.getCreatedAt());
        recycleBinPO.setOriginalUpdatedAt(root.getUpdatedAt());
        recycleBinPO.setDeletedBy(deletedBy);
        recycleBinPO.setDeletedAt(LocalDateTime.now());
        recycleBinPO.setRestoreDeadline(LocalDateTime.now().plusDays(30));
        recycleBinItemJpaRepository.save(recycleBinPO);
    }

    @Override
    public void restore(Long recycleBinId) {
        RecycleBinItem recycleBinItem = recycleBinItemRepositoryPort.findById(recycleBinId)
                .orElseThrow(() -> new BusinessException("回收站记录不存在: " + recycleBinId));

        Long rootOriginalId = recycleBinItem.getOriginalId();
        KnowledgeCategoryDeletedPO rootDeletedPO = deletedJpaRepository.findByOriginalId(rootOriginalId)
                .orElseThrow(() -> new BusinessException("分类快照不存在: " + rootOriginalId));

        List<Long> subtreeIds = parseSubtreeIds(rootDeletedPO.getRelatedData());
        if (subtreeIds == null || subtreeIds.isEmpty()) {
            subtreeIds = List.of(rootOriginalId);
        }
        List<KnowledgeCategoryDeletedPO> allDeletedPOs = deletedJpaRepository.findAllByOriginalIdIn(subtreeIds);
        Map<Long, KnowledgeCategoryDeletedPO> poMap = new LinkedHashMap<>();
        for (KnowledgeCategoryDeletedPO po : allDeletedPOs) {
            poMap.put(po.getOriginalId(), po);
        }

        // 恢复前校验：同名同父级不冲突
        for (KnowledgeCategoryDeletedPO po : allDeletedPOs) {
            if (categoryRepositoryPort.existsByNameAndParentId(po.getName(), po.getParentId())) {
                throw new BusinessException("目标父级下已存在同名分类: " + po.getName());
            }
        }

        // 拓扑排序：按 parentId 建树 → 从顶级开始 BFS
        Map<Long, List<Long>> childrenMap = new LinkedHashMap<>();
        List<Long> roots = new ArrayList<>();
        for (KnowledgeCategoryDeletedPO po : allDeletedPOs) {
            if (po.getParentId() == null || !poMap.containsKey(po.getParentId())) {
                roots.add(po.getOriginalId());
            } else {
                childrenMap.computeIfAbsent(po.getParentId(), k -> new ArrayList<>()).add(po.getOriginalId());
            }
        }

        List<Long> sortedIds = new ArrayList<>();
        List<Long> queue = new ArrayList<>(roots);
        while (!queue.isEmpty()) {
            Long current = queue.remove(0);
            sortedIds.add(current);
            List<Long> children = childrenMap.getOrDefault(current, Collections.emptyList());
            queue.addAll(children);
        }

        // 逐条 native INSERT（保留原始 ID，强制 INACTIVE）
        for (Long id : sortedIds) {
            KnowledgeCategoryDeletedPO po = poMap.get(id);
            entityManager.createNativeQuery(
                    "INSERT INTO knowledge_category (id, parent_id, name, description, "
                            + "icon_file_id, icon_url, color, cover_image_file_id, cover_image_url, "
                            + "sort_order, status, created_at, updated_at) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)")
                    .setParameter(1, po.getOriginalId())
                    .setParameter(2, po.getParentId())
                    .setParameter(3, po.getName())
                    .setParameter(4, po.getDescription())
                    .setParameter(5, po.getIconFileId())
                    .setParameter(6, po.getIconUrl())
                    .setParameter(7, po.getColor())
                    .setParameter(8, po.getCoverImageFileId())
                    .setParameter(9, po.getCoverImageUrl())
                    .setParameter(10, po.getSortOrder())
                    .setParameter(11, KnowledgeCategoryStatus.INACTIVE.name())
                    .setParameter(12, po.getCreatedAt())
                    .setParameter(13, LocalDateTime.now())
                    .executeUpdate();
        }

        List<Long> deletedPOIds = allDeletedPOs.stream().map(KnowledgeCategoryDeletedPO::getId).toList();
        deletedJpaRepository.deleteAllById(deletedPOIds);
        recycleBinItemJpaRepository.deleteById(recycleBinId);
    }

    @Override
    public void purge(Long recycleBinId) {
        RecycleBinItem recycleBinItem = recycleBinItemRepositoryPort.findById(recycleBinId)
                .orElseThrow(() -> new BusinessException("回收站记录不存在: " + recycleBinId));

        Long rootOriginalId = recycleBinItem.getOriginalId();
        KnowledgeCategoryDeletedPO rootDeletedPO = deletedJpaRepository.findByOriginalId(rootOriginalId)
                .orElse(null);
        if (rootDeletedPO == null) {
            recycleBinItemJpaRepository.deleteById(recycleBinId);
            return;
        }

        List<Long> subtreeIds = parseSubtreeIds(rootDeletedPO.getRelatedData());
        if (subtreeIds == null || subtreeIds.isEmpty()) {
            subtreeIds = List.of(rootOriginalId);
        }
        List<KnowledgeCategoryDeletedPO> allDeletedPOs = deletedJpaRepository.findAllByOriginalIdIn(subtreeIds);

        for (KnowledgeCategoryDeletedPO po : allDeletedPOs) {
            if (po.getIconFileId() != null) {
                try {
                    fileCleanupPort.deleteFile(po.getIconFileId());
                } catch (Exception e) {
                    log.warn("文件清理失败: originalId={}, iconFileId={}", po.getOriginalId(),
                            po.getIconFileId(), e);
                }
            }
            if (po.getCoverImageFileId() != null) {
                try {
                    fileCleanupPort.deleteFile(po.getCoverImageFileId());
                } catch (Exception e) {
                    log.warn("文件清理失败: originalId={}, coverImageFileId={}", po.getOriginalId(),
                            po.getCoverImageFileId(), e);
                }
            }
        }

        List<Long> deletedPOIds = allDeletedPOs.stream().map(KnowledgeCategoryDeletedPO::getId).toList();
        deletedJpaRepository.deleteAllById(deletedPOIds);
        recycleBinItemJpaRepository.deleteById(recycleBinId);
    }

    private static String writeSubtreeIds(List<Long> subtreeIds) {
        try {
            return objectMapper.writeValueAsString(Collections.singletonMap("subtreeOriginalIds", subtreeIds));
        } catch (Exception e) {
            throw new RuntimeException("JSON 序列化 subtreeIds 失败", e);
        }
    }

    private static List<Long> parseSubtreeIds(String relatedData) {
        if (relatedData == null) {
            return null;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(relatedData, new TypeReference<>() {});
            @SuppressWarnings("unchecked")
            List<Object> raw = (List<Object>) map.get("subtreeOriginalIds");
            if (raw == null) {
                return null;
            }
            List<Long> result = new ArrayList<>();
            for (Object o : raw) {
                result.add(((Number) o).longValue());
            }
            return result;
        } catch (Exception e) {
            throw new BusinessException("回收站快照数据已损坏（related_data JSON 解析失败），无法恢复，请使用永久删除");
        }
    }
}
