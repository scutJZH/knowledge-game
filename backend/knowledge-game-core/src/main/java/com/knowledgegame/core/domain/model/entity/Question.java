package com.knowledgegame.core.domain.model.entity;

import com.knowledgegame.core.domain.model.domainenum.Difficulty;
import com.knowledgegame.core.domain.model.domainenum.QuestionStatus;
import com.knowledgegame.core.domain.model.domainenum.QuestionType;
import com.knowledgegame.core.domain.model.vo.QuestionOption;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 题目聚合根（无框架注解）
 */
@Getter
public class Question {

    private Long id;
    private QuestionType type;
    private String content;
    private List<QuestionOption> options;
    private String answer;
    private String explanation;
    private Difficulty difficulty;
    private List<String> tags;
    private QuestionStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 创建新题目（工厂方法）
     */
    public static Question create(QuestionType type, String content, List<QuestionOption> options,
                                   String answer, Difficulty difficulty, String explanation,
                                   List<String> tags) {
        Question question = new Question();
        question.type = type;
        question.content = content;
        question.options = options;
        question.answer = answer;
        question.difficulty = difficulty;
        question.explanation = explanation;
        question.tags = tags;
        question.status = QuestionStatus.ACTIVE;
        question.createdAt = LocalDateTime.now();
        question.updatedAt = LocalDateTime.now();
        return question;
    }

    /**
     * 从持久化重建（用于 Repository 加载）
     */
    public static Question reconstruct(Long id, QuestionType type, String content,
                                        List<QuestionOption> options, String answer,
                                        Difficulty difficulty, String explanation,
                                        List<String> tags, QuestionStatus status,
                                        LocalDateTime createdAt, LocalDateTime updatedAt) {
        Question question = new Question();
        question.id = id;
        question.type = type;
        question.content = content;
        question.options = options;
        question.answer = answer;
        question.difficulty = difficulty;
        question.explanation = explanation;
        question.tags = tags;
        question.status = status;
        question.createdAt = createdAt;
        question.updatedAt = updatedAt;
        return question;
    }

    /**
     * 更新必填字段（不支持清空）。可清空字段（explanation/tags）请用对应的 updateXxx / clearXxx 方法。
     * <p>
     * 各字段 null=不更新（沿用 REQ-88 必填字段语义）。
     */
    public void update(String content, List<QuestionOption> options, String answer,
                       Difficulty difficulty) {
        if (content != null) {
            this.content = content;
        }
        if (options != null) {
            this.options = options;
        }
        if (answer != null) {
            this.answer = answer;
        }
        if (difficulty != null) {
            this.difficulty = difficulty;
        }
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 更新解析（清空请用 clearExplanation）
     *
     * @throws IllegalArgumentException explanation 为 null 时抛出
     */
    public void updateExplanation(String explanation) {
        if (explanation == null) {
            throw new IllegalArgumentException("explanation 清空请用 clearExplanation()");
        }
        this.explanation = explanation;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 清空解析
     */
    public void clearExplanation() {
        this.explanation = null;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 更新标签列表（清空请用 clearTags）
     *
     * @throws IllegalArgumentException tags 为 null 时抛出（清空意图请显式调用 clearTags()）
     */
    public void updateTags(List<String> tags) {
        if (tags == null) {
            throw new IllegalArgumentException("tags 清空请用 clearTags()");
        }
        this.tags = tags;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 清空标签列表
     */
    public void clearTags() {
        this.tags = null;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 软删除（status→INACTIVE）
     */
    public void deactivate() {
        this.status = QuestionStatus.INACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 重新启用（status→ACTIVE）
     */
    public void activate() {
        this.status = QuestionStatus.ACTIVE;
        this.updatedAt = LocalDateTime.now();
    }
}