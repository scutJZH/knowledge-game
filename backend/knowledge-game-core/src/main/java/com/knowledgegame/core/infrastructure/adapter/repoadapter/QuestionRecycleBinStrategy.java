package com.knowledgegame.core.infrastructure.adapter.repoadapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.domainenum.QuestionStatus;
import com.knowledgegame.core.domain.model.domainenum.ResourceType;
import com.knowledgegame.core.domain.model.entity.Question;
import com.knowledgegame.core.domain.model.entity.RecycleBinItem;
import com.knowledgegame.core.domain.model.domainenum.QuestionType;
import com.knowledgegame.core.domain.model.vo.QuestionOption;
import com.knowledgegame.core.domain.port.outbound.QuestionRepository;
import com.knowledgegame.core.domain.port.outbound.RecycleBinItemRepositoryPort;
import com.knowledgegame.core.domain.service.recyclebin.RecycleBinItemStrategy;
import com.knowledgegame.core.infrastructure.db.entity.QuestionCategoryRelationPO;
import com.knowledgegame.core.infrastructure.db.entity.QuestionDeletedPO;
import com.knowledgegame.core.infrastructure.db.entity.RecycleBinItemPO;
import com.knowledgegame.core.infrastructure.db.repository.KnowledgeCategoryJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.QuestionCategoryRelationJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.QuestionDeletedJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.QuestionJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.RecycleBinItemJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 题目回收站策略
 * <p>
 * 实现 validateDeletable / moveToRecycleBin / restore / purge 四方法。
 * 读操作走 Port（返回领域对象），写/删操作走 JPA Repository。
 * Question 无图片字段，不注入 FileCleanupPort。
 * <p>
 * 由 admin 模块通过 @Bean 显式注册，不标记 @Component。
 */
public class QuestionRecycleBinStrategy implements RecycleBinItemStrategy<Question> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final QuestionRepository questionRepository;
    private final RecycleBinItemRepositoryPort recycleBinItemRepositoryPort;
    private final QuestionJpaRepository questionJpaRepository;
    private final QuestionDeletedJpaRepository questionDeletedJpaRepository;
    private final QuestionCategoryRelationJpaRepository relationJpaRepository;
    private final RecycleBinItemJpaRepository recycleBinItemJpaRepository;
    private final KnowledgeCategoryJpaRepository categoryJpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public QuestionRecycleBinStrategy(QuestionRepository questionRepository,
                                       RecycleBinItemRepositoryPort recycleBinItemRepositoryPort,
                                       QuestionJpaRepository questionJpaRepository,
                                       QuestionDeletedJpaRepository questionDeletedJpaRepository,
                                       QuestionCategoryRelationJpaRepository relationJpaRepository,
                                       RecycleBinItemJpaRepository recycleBinItemJpaRepository,
                                       KnowledgeCategoryJpaRepository categoryJpaRepository) {
        this.questionRepository = questionRepository;
        this.recycleBinItemRepositoryPort = recycleBinItemRepositoryPort;
        this.questionJpaRepository = questionJpaRepository;
        this.questionDeletedJpaRepository = questionDeletedJpaRepository;
        this.relationJpaRepository = relationJpaRepository;
        this.recycleBinItemJpaRepository = recycleBinItemJpaRepository;
        this.categoryJpaRepository = categoryJpaRepository;
    }

    @Override
    public ResourceType getResourceType() {
        return ResourceType.QUESTION;
    }

    @Override
    public void validateDeletable(Long originalId) {
        if (questionRepository.findById(originalId).isEmpty()) {
            throw new BusinessException("题目不存在: " + originalId);
        }
    }

    @Override
    public void moveToRecycleBin(Long originalId, String deletedBy) {
        Question question = questionRepository.findById(originalId)
                .orElseThrow(() -> new BusinessException("题目不存在: " + originalId));

        List<QuestionCategoryRelationPO> relations = relationJpaRepository.findByQuestionId(originalId);
        List<Long> categoryIds = relations.stream()
                .map(QuestionCategoryRelationPO::getCategoryId)
                .toList();

        QuestionDeletedPO deletedPO = new QuestionDeletedPO();
        deletedPO.setOriginalId(originalId);
        deletedPO.setType(question.getType());
        deletedPO.setContent(question.getContent());
        deletedPO.setOptions(serializeOptions(question.getOptions()));
        deletedPO.setAnswer(wrapAnswer(question.getType(), question.getAnswer()));
        deletedPO.setExplanation(question.getExplanation());
        deletedPO.setDifficulty(question.getDifficulty());
        deletedPO.setTags(serializeTags(question.getTags()));
        deletedPO.setStatus(question.getStatus());
        deletedPO.setCreatedAt(question.getCreatedAt());
        deletedPO.setUpdatedAt(question.getUpdatedAt());
        deletedPO.setRelatedData(writeCategoryIds(categoryIds));
        deletedPO.setDeletedBy(deletedBy);
        deletedPO.setDeletedAt(LocalDateTime.now());
        questionDeletedJpaRepository.save(deletedPO);

        relationJpaRepository.deleteByQuestionId(originalId);
        questionJpaRepository.deleteById(originalId);

        String content = question.getContent();
        String safeName = content.length() > 100 ? content.substring(0, 100) : content;

        RecycleBinItemPO recycleBinPO = new RecycleBinItemPO();
        recycleBinPO.setResourceType(ResourceType.QUESTION);
        recycleBinPO.setOriginalId(originalId);
        recycleBinPO.setOriginalName(safeName);
        recycleBinPO.setOriginalCreatedAt(question.getCreatedAt());
        recycleBinPO.setOriginalUpdatedAt(question.getUpdatedAt());
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
        QuestionDeletedPO deletedPO = questionDeletedJpaRepository.findByOriginalId(originalId)
                .orElseThrow(() -> new BusinessException("题目快照不存在: " + originalId));

        List<Long> categoryIds = parseCategoryIds(deletedPO.getRelatedData());
        if (categoryIds != null && !categoryIds.isEmpty()) {
            long existingCount = categoryJpaRepository.countByIdIn(categoryIds);
            if (existingCount != categoryIds.size()) {
                throw new BusinessException("题目关联的分类已被删除，无法恢复");
            }
        }

        entityManager.createNativeQuery(
                "INSERT INTO question (id, type, content, options, answer, explanation, "
                        + "difficulty, tags, status, created_at, updated_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?)")
                .setParameter(1, originalId)
                .setParameter(2, deletedPO.getType().name())
                .setParameter(3, deletedPO.getContent())
                .setParameter(4, deletedPO.getOptions())
                .setParameter(5, deletedPO.getAnswer())
                .setParameter(6, deletedPO.getExplanation())
                .setParameter(7, deletedPO.getDifficulty().name())
                .setParameter(8, deletedPO.getTags())
                .setParameter(9, QuestionStatus.INACTIVE.name())
                .setParameter(10, deletedPO.getCreatedAt())
                .setParameter(11, LocalDateTime.now())
                .executeUpdate();

        if (categoryIds != null && !categoryIds.isEmpty()) {
            questionRepository.saveCategoryRelations(originalId, categoryIds);
        }

        questionDeletedJpaRepository.deleteById(deletedPO.getId());
        recycleBinItemJpaRepository.deleteById(recycleBinId);
    }

    @Override
    public void purge(Long recycleBinId) {
        RecycleBinItem recycleBinItem = recycleBinItemRepositoryPort.findById(recycleBinId)
                .orElseThrow(() -> new BusinessException("回收站记录不存在: " + recycleBinId));

        Long originalId = recycleBinItem.getOriginalId();
        QuestionDeletedPO deletedPO = questionDeletedJpaRepository.findByOriginalId(originalId)
                .orElse(null);

        if (deletedPO == null) {
            recycleBinItemJpaRepository.deleteById(recycleBinId);
            return;
        }

        questionDeletedJpaRepository.deleteById(deletedPO.getId());
        recycleBinItemJpaRepository.deleteById(recycleBinId);
    }

    private static String serializeOptions(List<QuestionOption> options) {
        if (options == null) {
            return null;
        }
        try {
            List<Map<String, String>> data = options.stream()
                    .map(o -> Map.of("key", o.getKey(), "content", o.getContent()))
                    .toList();
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("options JSON 序列化失败", e);
        }
    }

    private static String wrapAnswer(QuestionType type, String answer) {
        if (type == QuestionType.SINGLE_CHOICE && answer != null) {
            try {
                return objectMapper.writeValueAsString(answer);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("单选题答案 JSON 序列化失败", e);
            }
        }
        return answer;
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

    public static String writeCategoryIds(List<Long> categoryIds) {
        try {
            return objectMapper.writeValueAsString(
                    Collections.singletonMap("categoryAssociationIds", categoryIds));
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
}
