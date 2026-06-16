package com.knowledgegame.core.domain.service;

import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.domainenum.Difficulty;
import com.knowledgegame.core.domain.model.domainenum.KnowledgeCategoryStatus;
import com.knowledgegame.core.domain.model.domainenum.QuestionType;
import com.knowledgegame.core.domain.model.entity.KnowledgeCategory;
import com.knowledgegame.core.domain.model.entity.Question;
import com.knowledgegame.core.domain.model.vo.QuestionOption;
import com.knowledgegame.core.domain.port.outbound.QuestionRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 题目领域服务（校验逻辑，纯 POJO）
 */
public class QuestionDomainService {

    private static final int MAX_CONTENT_LENGTH = 500;
    private static final int MIN_OPTIONS = 2;
    private static final int MAX_OPTIONS = 6;
    private static final int MAX_TAGS = 10;

    private final QuestionRepository questionRepository;

    public QuestionDomainService(QuestionRepository questionRepository) {
        this.questionRepository = questionRepository;
    }

    /**
     * 校验并创建题目
     */
    public Question validateAndCreate(QuestionType type, String content, List<QuestionOption> options,
                                       String answer, Difficulty difficulty, String explanation,
                                       List<String> tags) {
        validateContent(content);
        validateTypeSpecificRules(type, options, answer);
        validateTags(tags);

        return Question.create(type, content, options, answer, difficulty, explanation, tags);
    }

    /**
     * 校验更新题目（传入已有 Question，用于选项/答案的 fallback）
     */
    public void validateUpdate(Question existing, String content, List<QuestionOption> options,
                                String answer, List<String> tags) {
        if (content != null) {
            validateContent(content);
        }
        if (options != null || answer != null) {
            List<QuestionOption> effectiveOptions = options != null ? options : existing.getOptions();
            String effectiveAnswer = answer != null ? answer : existing.getAnswer();
            validateTypeSpecificRules(existing.getType(), effectiveOptions, effectiveAnswer);
        }
        if (tags != null) {
            validateTags(tags);
        }
    }

    private void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException("题目内容不能为空");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException("题目内容不能超过 " + MAX_CONTENT_LENGTH + " 字");
        }
    }

    private void validateTypeSpecificRules(QuestionType type, List<QuestionOption> options,
                                            String answer) {
        switch (type) {
            case SINGLE_CHOICE -> validateSingleChoice(options, answer);
            case MULTIPLE_CHOICE -> validateMultipleChoice(options, answer);
            case TRUE_FALSE -> validateTrueFalse(options, answer);
            case FILL_BLANK -> validateFillBlank(options, answer);
        }
    }

    private void validateSingleChoice(List<QuestionOption> options, String answer) {
        validateChoiceOptions(options);
        Set<String> keys = options.stream().map(QuestionOption::getKey).collect(Collectors.toSet());
        if (!keys.contains(answer)) {
            throw new BusinessException("单选题答案必须是选项之一");
        }
    }

    private void validateMultipleChoice(List<QuestionOption> options, String answer) {
        validateChoiceOptions(options);
        Set<String> keys = options.stream().map(QuestionOption::getKey).collect(Collectors.toSet());
        if (answer == null || !answer.startsWith("[")) {
            throw new BusinessException("多选题答案格式错误");
        }
        String cleaned = answer.replace("[", "").replace("]", "").replace("\"", " ").trim();
        List<String> answers = Arrays.stream(cleaned.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (answers.size() < 2) {
            throw new BusinessException("多选题至少选择 2 个答案");
        }
        for (String a : answers) {
            if (!keys.contains(a)) {
                throw new BusinessException("多选题答案必须是选项之一: " + a);
            }
        }
    }

    private void validateTrueFalse(List<QuestionOption> options, String answer) {
        if (options != null && !options.isEmpty()) {
            throw new BusinessException("判断题不能有选项");
        }
        if (!"true".equals(answer) && !"false".equals(answer)) {
            throw new BusinessException("判断题答案必须为 true 或 false");
        }
    }

    private void validateFillBlank(List<QuestionOption> options, String answer) {
        if (options != null && !options.isEmpty()) {
            throw new BusinessException("填空题不能有选项");
        }
        if (answer == null || answer.isBlank()) {
            throw new BusinessException("填空题答案不能为空");
        }
    }

    private void validateChoiceOptions(List<QuestionOption> options) {
        if (options == null || options.size() < MIN_OPTIONS) {
            throw new BusinessException("选择题至少需要 " + MIN_OPTIONS + " 个选项");
        }
        if (options.size() > MAX_OPTIONS) {
            throw new BusinessException("选择题最多 " + MAX_OPTIONS + " 个选项");
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
            if (tag.length() > 20) {
                throw new BusinessException("标签长度不能超过 20 字: " + tag);
            }
        }
    }

    /**
     * 校验题目可否启用：关联的所有分类必须全部 ACTIVE（纯内存校验，调用方预加载分类）
     *
     * @param questionName 题目名（用于错误消息）
     * @param categoryIds  该题目关联的分类 ID 列表
     * @param categoryMap  分类 ID → 分类实体的预加载映射（批量场景由调用方一次性加载）
     */
    public void validateActivatable(String questionName, List<Long> categoryIds,
                                     Map<Long, KnowledgeCategory> categoryMap) {
        if (categoryIds.isEmpty()) {
            return; // 题目未关联任何分类，允许启用
        }
        // 检查 INACTIVE 或缺失（已删除）的分类
        List<String> inactiveNames = categoryIds.stream()
                .map(cid -> {
                    KnowledgeCategory c = categoryMap.get(cid);
                    if (c == null) {
                        return "《(ID=" + cid + ")》"; // 分类已删除
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
                    "题目《" + questionName + "》关联的知识点分类" + names + "处于停用状态，请先启用对应分类再启用题目");
        }
    }
}
