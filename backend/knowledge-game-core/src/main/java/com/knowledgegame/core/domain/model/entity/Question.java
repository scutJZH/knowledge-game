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
     * 更新题目信息
     */
    public void update(String content, List<QuestionOption> options, String answer,
                       Difficulty difficulty, String explanation, List<String> tags) {
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
        if (explanation != null) {
            this.explanation = explanation;
        }
        if (tags != null) {
            this.tags = tags;
        }
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