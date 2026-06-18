package com.knowledgegame.core.domain.model.entity;

import com.knowledgegame.core.domain.model.domainenum.Difficulty;
import com.knowledgegame.core.domain.model.domainenum.QuestionStatus;
import com.knowledgegame.core.domain.model.domainenum.QuestionType;
import com.knowledgegame.core.domain.model.vo.QuestionOption;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Question 领域实体单元测试
 */
class QuestionTest {

    /**
     * 创建单选题 - 正常
     */
    @Test
    void create_shouldSucceed_whenSingleChoice() {
        List<QuestionOption> options = List.of(
                QuestionOption.of("A", "选项A"),
                QuestionOption.of("B", "选项B")
        );

        Question question = Question.create(
                QuestionType.SINGLE_CHOICE,
                "测试题目",
                options,
                "A",
                Difficulty.EASY,
                "解析内容",
                List.of("Java")
        );

        assertNotNull(question);
        assertEquals(QuestionType.SINGLE_CHOICE, question.getType());
        assertEquals("测试题目", question.getContent());
        assertEquals(2, question.getOptions().size());
        assertEquals("A", question.getAnswer());
        assertEquals(Difficulty.EASY, question.getDifficulty());
        assertEquals(QuestionStatus.ACTIVE, question.getStatus());
    }

    /**
     * 创建判断题 - options 为空
     */
    @Test
    void create_shouldSucceed_whenTrueFalse() {
        Question question = Question.create(
                QuestionType.TRUE_FALSE,
                "判断题目",
                null,
                "true",
                Difficulty.MEDIUM,
                null,
                null
        );

        assertEquals(QuestionType.TRUE_FALSE, question.getType());
        assertNull(question.getOptions());
        assertEquals("true", question.getAnswer());
    }

    /**
     * 创建填空题 - answer 为 JSON 数组字符串
     */
    @Test
    void create_shouldSucceed_whenFillBlank() {
        Question question = Question.create(
                QuestionType.FILL_BLANK,
                "填空题目___",
                null,
                "[\"keyword1\",\"keyword2\"]",
                Difficulty.HARD,
                null,
                List.of("Spring")
        );

        assertEquals(QuestionType.FILL_BLANK, question.getType());
        assertNull(question.getOptions());
        assertEquals("[\"keyword1\",\"keyword2\"]", question.getAnswer());
    }

    /**
     * reconstruct - 从持久化重建
     */
    @Test
    void reconstruct_shouldRestore() {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
        Question question = Question.reconstruct(
                1L, QuestionType.SINGLE_CHOICE, "题目", null, "A",
                Difficulty.EASY, "解析", List.of("Java"),
                QuestionStatus.ACTIVE, now, now
        );

        assertEquals(1L, question.getId());
        assertEquals("题目", question.getContent());
        assertEquals(QuestionStatus.ACTIVE, question.getStatus());
    }

    /**
     * update - 更新题目必填字段，explanation/tags 用独立方法更新
     */
    @Test
    void update_shouldModifyFields() {
        Question question = Question.create(
                QuestionType.SINGLE_CHOICE, "旧题目",
                List.of(QuestionOption.of("A", "A"), QuestionOption.of("B", "B")),
                "A", Difficulty.EASY, null, null
        );

        question.update("新题目",
                List.of(QuestionOption.of("A", "A"), QuestionOption.of("B", "B"), QuestionOption.of("C", "C")),
                "B", Difficulty.HARD);
        question.updateExplanation("新解析");
        question.updateTags(List.of("Java"));

        assertEquals("新题目", question.getContent());
        assertEquals(3, question.getOptions().size());
        assertEquals("B", question.getAnswer());
        assertEquals(Difficulty.HARD, question.getDifficulty());
        assertEquals("新解析", question.getExplanation());
        assertEquals(List.of("Java"), question.getTags());
    }

    /**
     * deactivate - 软删除
     */
    @Test
    void deactivate_shouldSetInactive() {
        Question question = Question.create(
                QuestionType.TRUE_FALSE, "题目", null, "true",
                Difficulty.EASY, null, null
        );

        question.deactivate();

        assertEquals(QuestionStatus.INACTIVE, question.getStatus());
    }

    /**
     * activate - 重新启用
     */
    @Test
    void activate_shouldSetActive() {
        Question question = Question.reconstruct(
                1L, QuestionType.TRUE_FALSE, "题目", null, "true",
                Difficulty.EASY, null, null, QuestionStatus.INACTIVE,
                LocalDateTime.now(), LocalDateTime.now()
        );

        question.activate();

        assertEquals(QuestionStatus.ACTIVE, question.getStatus());
    }

    /**
     * 创建多选题 - 正常
     */
    @Test
    void create_shouldSucceed_whenMultipleChoice() {
        List<QuestionOption> options = List.of(
                QuestionOption.of("A", "选项A"),
                QuestionOption.of("B", "选项B"),
                QuestionOption.of("C", "选项C")
        );

        Question question = Question.create(
                QuestionType.MULTIPLE_CHOICE,
                "多选题目",
                options,
                "[\"A\",\"C\"]",
                Difficulty.HARD,
                null,
                null
        );

        assertEquals(QuestionType.MULTIPLE_CHOICE, question.getType());
        assertEquals(3, question.getOptions().size());
        assertEquals("[\"A\",\"C\"]", question.getAnswer());
        assertEquals(Difficulty.HARD, question.getDifficulty());
    }

    /**
     * 创建 - 验证 createdAt 和 updatedAt 被设置
     */
    @Test
    void create_shouldSetTimestamps() {
        LocalDateTime before = LocalDateTime.now();

        Question question = Question.create(
                QuestionType.TRUE_FALSE, "题目", null, "true",
                Difficulty.EASY, null, null
        );

        LocalDateTime after = LocalDateTime.now();

        assertNotNull(question.getCreatedAt());
        assertNotNull(question.getUpdatedAt());
        assertTrue(!question.getCreatedAt().isBefore(before));
        assertTrue(!question.getCreatedAt().isAfter(after));
    }

    /**
     * 创建 - explanation 和 tags 为 null 时正常
     */
    @Test
    void create_shouldSucceed_whenOptionalFieldsNull() {
        Question question = Question.create(
                QuestionType.TRUE_FALSE, "判断题", null, "true",
                Difficulty.EASY, null, null
        );

        assertNull(question.getExplanation());
        assertNull(question.getTags());
        assertEquals(QuestionStatus.ACTIVE, question.getStatus());
    }

    /**
     * 创建 - 带解析和标签
     */
    @Test
    void create_shouldSucceed_withExplanationAndTags() {
        Question question = Question.create(
                QuestionType.SINGLE_CHOICE, "题目",
                List.of(QuestionOption.of("A", "A"), QuestionOption.of("B", "B")),
                "A", Difficulty.MEDIUM, "这是解析内容",
                List.of("Java", "Spring")
        );

        assertEquals("这是解析内容", question.getExplanation());
        assertEquals(2, question.getTags().size());
        assertTrue(question.getTags().contains("Java"));
        assertTrue(question.getTags().contains("Spring"));
    }

    /**
     * reconstruct - 状态为 INACTIVE
     */
    @Test
    void reconstruct_shouldRestoreInactiveStatus() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 1, 12, 0);
        Question question = Question.reconstruct(
                99L, QuestionType.FILL_BLANK, "填空___", null, "答案",
                Difficulty.HARD, "解析", List.of("标签"),
                QuestionStatus.INACTIVE, now, now
        );

        assertEquals(99L, question.getId());
        assertEquals(QuestionType.FILL_BLANK, question.getType());
        assertEquals(QuestionStatus.INACTIVE, question.getStatus());
        assertEquals("答案", question.getAnswer());
        assertEquals(Difficulty.HARD, question.getDifficulty());
    }

    /**
     * update - 必填字段 null=不更新，原值保持
     */
    @Test
    void update_shouldNotModify_whenNullFields() {
        Question question = Question.create(
                QuestionType.SINGLE_CHOICE, "原始题目",
                List.of(QuestionOption.of("A", "A"), QuestionOption.of("B", "B")),
                "A", Difficulty.EASY, "原始解析", List.of("原始标签")
        );

        question.update(null, null, null, null);

        assertEquals("原始题目", question.getContent());
        assertEquals("A", question.getAnswer());
        assertEquals(Difficulty.EASY, question.getDifficulty());
        // explanation/tags 不在 update 方法处理范围内（用专门方法）
        assertEquals("原始解析", question.getExplanation());
        assertEquals(List.of("原始标签"), question.getTags());
    }

    /**
     * update - 仅更新部分字段
     */
    @Test
    void update_shouldModifyPartialFields() {
        Question question = Question.create(
                QuestionType.SINGLE_CHOICE, "原始题目",
                List.of(QuestionOption.of("A", "A"), QuestionOption.of("B", "B")),
                "A", Difficulty.EASY, null, null
        );

        question.update("新题目", null, "B", null);

        assertEquals("新题目", question.getContent());
        assertEquals("B", question.getAnswer());
        // 未传入的字段保持不变
        assertEquals(Difficulty.EASY, question.getDifficulty());
        assertEquals(2, question.getOptions().size());
    }

    /**
     * updateExplanation - 正常更新解析
     */
    @Test
    void updateExplanation_shouldSetExplanation() {
        Question question = Question.create(
                QuestionType.TRUE_FALSE, "题目", null, "true",
                Difficulty.EASY, null, null
        );

        question.updateExplanation("新解析");

        assertEquals("新解析", question.getExplanation());
    }

    /**
     * updateExplanation - null 抛 IllegalArgumentException
     */
    @Test
    void updateExplanation_shouldThrow_whenNull() {
        Question question = Question.create(
                QuestionType.TRUE_FALSE, "题目", null, "true",
                Difficulty.EASY, "旧解析", null
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> question.updateExplanation(null));
        assertTrue(ex.getMessage().contains("clearExplanation"));
        // 原值不变
        assertEquals("旧解析", question.getExplanation());
    }

    /**
     * clearExplanation - 清空解析
     */
    @Test
    void clearExplanation_shouldSetNull() {
        Question question = Question.create(
                QuestionType.TRUE_FALSE, "题目", null, "true",
                Difficulty.EASY, "旧解析", null
        );

        question.clearExplanation();

        assertNull(question.getExplanation());
    }

    /**
     * updateTags - 正常更新标签
     */
    @Test
    void updateTags_shouldSetTags() {
        Question question = Question.create(
                QuestionType.TRUE_FALSE, "题目", null, "true",
                Difficulty.EASY, null, List.of("旧标签")
        );

        question.updateTags(List.of("新标签1", "新标签2"));

        assertEquals(List.of("新标签1", "新标签2"), question.getTags());
    }

    /**
     * updateTags - 传入空列表应覆盖原标签（与 clearTags 区分：空列表 ≠ null）
     */
    @Test
    void updateTags_shouldReplaceWithEmptyList() {
        Question question = Question.create(
                QuestionType.TRUE_FALSE, "题目", null, "true",
                Difficulty.EASY, null, List.of("旧标签")
        );

        question.updateTags(List.of());

        assertEquals(List.of(), question.getTags());
    }

    /**
     * updateTags - null 抛 IllegalArgumentException
     */
    @Test
    void updateTags_shouldThrow_whenNull() {
        Question question = Question.create(
                QuestionType.TRUE_FALSE, "题目", null, "true",
                Difficulty.EASY, null, List.of("旧标签")
        );

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> question.updateTags(null));
        assertTrue(ex.getMessage().contains("clearTags"));
        // 原值不变
        assertEquals(List.of("旧标签"), question.getTags());
    }

    /**
     * clearTags - 清空标签（tags 设为 null，区别于空列表）
     */
    @Test
    void clearTags_shouldSetNull() {
        Question question = Question.create(
                QuestionType.TRUE_FALSE, "题目", null, "true",
                Difficulty.EASY, null, List.of("旧标签")
        );

        question.clearTags();

        assertNull(question.getTags());
    }

    /**
     * deactivate - 验证 updatedAt 被更新
     */
    @Test
    void deactivate_shouldUpdateTimestamp() {
        Question question = Question.create(
                QuestionType.TRUE_FALSE, "题目", null, "true",
                Difficulty.EASY, null, null
        );
        LocalDateTime beforeDeactivate = question.getUpdatedAt();

        question.deactivate();

        assertEquals(QuestionStatus.INACTIVE, question.getStatus());
        assertTrue(question.getUpdatedAt().isAfter(beforeDeactivate)
                || question.getUpdatedAt().equals(beforeDeactivate));
    }

    /**
     * activate - 验证 updatedAt 被更新
     */
    @Test
    void activate_shouldUpdateTimestamp() {
        LocalDateTime fixedTime = LocalDateTime.of(2026, 1, 1, 0, 0);
        Question question = Question.reconstruct(
                1L, QuestionType.TRUE_FALSE, "题目", null, "true",
                Difficulty.EASY, null, null, QuestionStatus.INACTIVE,
                fixedTime, fixedTime
        );

        question.activate();

        assertEquals(QuestionStatus.ACTIVE, question.getStatus());
        assertTrue(question.getUpdatedAt().isAfter(fixedTime));
    }
}