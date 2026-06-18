package com.knowledgegame.core.domain.service;

import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.domainenum.KnowledgeCategoryStatus;
import com.knowledgegame.core.domain.model.entity.KnowledgeCategory;
import com.knowledgegame.core.domain.model.entity.KnowledgeItem;
import com.knowledgegame.core.domain.model.vo.FileRef;
import com.knowledgegame.core.domain.port.outbound.KnowledgeCategoryRepositoryPort;
import com.knowledgegame.core.domain.port.outbound.KnowledgeItemRepository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识条目领域服务（校验逻辑，纯 POJO）
 */
public class KnowledgeItemDomainService {

    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_CONTENT_LENGTH = 50000;
    private static final int MAX_TAGS = 10;
    private static final int MAX_TAG_LENGTH = 20;

    private final KnowledgeItemRepository itemRepository;
    private final KnowledgeCategoryRepositoryPort categoryRepositoryPort;

    public KnowledgeItemDomainService(KnowledgeItemRepository itemRepository,
                                       KnowledgeCategoryRepositoryPort categoryRepositoryPort) {
        this.itemRepository = itemRepository;
        this.categoryRepositoryPort = categoryRepositoryPort;
    }

    /**
     * 校验并创建知识条目
     */
    public KnowledgeItem validateAndCreate(String title, String content, FileRef coverImage,
                                            List<String> tags, int sortOrder, List<Long> categoryIds) {
        validateTitle(title);
        validateContent(content);
        validateTags(tags);
        validateCategoryIds(categoryIds);

        return KnowledgeItem.create(title, content, coverImage, tags, sortOrder);
    }

    /**
     * 校验更新知识条目（null 字段跳过校验）
     */
    public void validateUpdate(KnowledgeItem existing, String title, String content,
                                List<String> tags) {
        if (title != null) {
            validateTitle(title);
        }
        if (content != null) {
            validateContent(content);
        }
        if (tags != null) {
            validateTags(tags);
        }
    }

    private void validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new BusinessException("知识条目标题不能为空");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new BusinessException("知识条目标题不能超过 " + MAX_TITLE_LENGTH + " 字");
        }
    }

    private void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException("知识条目内容不能为空");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException("知识条目内容不能超过 " + MAX_CONTENT_LENGTH + " 字");
        }
    }

    private void validateTags(List<String> tags) {
        if (tags == null) {
            return;
        }
        if (tags.size() > MAX_TAGS) {
            throw new BusinessException("标签最多 " + MAX_TAGS + " 个");
        }
        for (String tag : tags) {
            if (tag.length() > MAX_TAG_LENGTH) {
                throw new BusinessException("标签长度不能超过 " + MAX_TAG_LENGTH + " 字: " + tag);
            }
        }
    }

    private void validateCategoryIds(List<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            throw new BusinessException("知识条目必须关联至少一个分类");
        }
        List<KnowledgeCategory> categories = categoryRepositoryPort.findAllByIdIn(categoryIds);
        List<Long> foundIds = categories.stream().map(KnowledgeCategory::getId).toList();
        List<Long> missingIds = categoryIds.stream()
                .filter(id -> !foundIds.contains(id))
                .toList();
        if (!missingIds.isEmpty()) {
            throw new BusinessException("分类不存在: " + missingIds);
        }
        List<String> inactiveNames = categories.stream()
                .filter(c -> c.getStatus() != KnowledgeCategoryStatus.ACTIVE)
                .map(KnowledgeCategory::getName)
                .toList();
        if (!inactiveNames.isEmpty()) {
            throw new BusinessException("以下分类已停用，无法关联: " + String.join("、", inactiveNames));
        }
    }

    /**
     * 校验知识条目可否启用：关联的所有分类必须全部 ACTIVE（纯内存校验，调用方预加载分类）
     *
     * @param itemTitle   条目名（用于错误消息）
     * @param categoryIds 该条目关联的分类 ID 列表
     * @param categoryMap 分类 ID → 分类实体的预加载映射
     */
    public void validateActivatable(String itemTitle, List<Long> categoryIds,
                                     Map<Long, KnowledgeCategory> categoryMap) {
        if (categoryIds.isEmpty()) {
            return;
        }
        List<String> inactiveNames = categoryIds.stream()
                .map(cid -> {
                    KnowledgeCategory c = categoryMap.get(cid);
                    if (c == null) {
                        return "《(ID=" + cid + ")》";
                    }
                    if (c.getStatus() != KnowledgeCategoryStatus.ACTIVE) {
                        return "《" + c.getName() + "》";
                    }
                    return null;
                })
                .filter(n -> n != null)
                .toList();
        if (!inactiveNames.isEmpty()) {
            String names = String.join("、", inactiveNames);
            throw new BusinessException(
                    "知识条目《" + itemTitle + "》关联的知识点分类" + names + "处于停用状态，请先启用对应分类再启用条目");
        }
    }
}
