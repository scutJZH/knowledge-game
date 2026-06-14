package com.knowledgegame.core.infrastructure.db.converter;

import com.knowledgegame.core.domain.model.domainenum.Difficulty;
import com.knowledgegame.core.domain.model.domainenum.QuestionStatus;
import com.knowledgegame.core.domain.model.domainenum.QuestionType;
import com.knowledgegame.core.domain.model.entity.Question;
import com.knowledgegame.core.domain.model.vo.QuestionOption;
import com.knowledgegame.core.infrastructure.db.entity.QuestionPO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * QuestionConverter 单元测试（手动 JSON 处理逻辑，非纯 MapStruct 自动生成）
 */
class QuestionConverterTest {

    private final QuestionConverter converter = QuestionConverter.INSTANCE;

    /** 构造完整单选题目 */
    private Question createSingleChoice(String answer) {
        return Question.reconstruct(1L, QuestionType.SINGLE_CHOICE, "测试题目",
                List.of(QuestionOption.of("A", "选项A"), QuestionOption.of("B", "选项B")),
                answer, Difficulty.EASY, "解析", List.of("Java"), QuestionStatus.ACTIVE,
                LocalDateTime.now(), LocalDateTime.now());
    }

    /** 构造完整多选题目 */
    private Question createMultiChoice(String answer) {
        return Question.reconstruct(1L, QuestionType.MULTIPLE_CHOICE, "多选题目",
                List.of(QuestionOption.of("A", "选项A"), QuestionOption.of("B", "选项B"), QuestionOption.of("C", "选项C")),
                answer, Difficulty.MEDIUM, null, List.of(), QuestionStatus.ACTIVE,
                LocalDateTime.now(), LocalDateTime.now());
    }

    /** 构造完整判断题 */
    private Question createTrueFalse(String answer) {
        return Question.reconstruct(2L, QuestionType.TRUE_FALSE, "判断题目",
                null, answer, Difficulty.EASY, null, null, QuestionStatus.ACTIVE,
                LocalDateTime.now(), LocalDateTime.now());
    }

    /** 构造完整填空题 */
    private Question createFillBlank(String answer) {
        return Question.reconstruct(3L, QuestionType.FILL_BLANK, "填空题目___",
                null, answer, Difficulty.HARD, "解析内容", List.of("tag"), QuestionStatus.ACTIVE,
                LocalDateTime.now(), LocalDateTime.now());
    }

    // ---- 单选 wrap/unwrap 往返 ----

    @Test
    void shouldRoundTrip_whenSingleChoice() {
        Question question = createSingleChoice("A");
        QuestionPO po = converter.toPO(question);
        Question restored = converter.toDomain(po);

        assertEquals(QuestionType.SINGLE_CHOICE, restored.getType());
        assertEquals("A", restored.getAnswer());
        assertEquals("测试题目", restored.getContent());
        assertNotNull(restored.getOptions());
        assertEquals(2, restored.getOptions().size());
    }

    @Test
    void shouldWrapSingleChoiceAnswerAsJsonString() {
        Question question = createSingleChoice("B");
        QuestionPO po = converter.toPO(question);
        // 存储时 answer 应为 JSON 字符串值（如 "B"），即带引号
        assertEquals("\"B\"", po.getAnswer());
    }

    @Test
    void shouldUnwrapSingleChoiceAnswerFromJsonString() {
        QuestionPO po = QuestionPO.builder()
                .id(1L)
                .type(QuestionType.SINGLE_CHOICE)
                .content("测试")
                .answer("\"A\"") // 数据库存储的 JSON 字符串值
                .difficulty(Difficulty.EASY)
                .status(QuestionStatus.ACTIVE)
                .build();
        Question question = converter.toDomain(po);
        assertEquals("A", question.getAnswer());
    }

    // ---- 其他题型不受影响 ----

    @Test
    void shouldNotWrapMultiChoiceAnswer() {
        Question question = createMultiChoice("[\"A\",\"C\"]");
        QuestionPO po = converter.toPO(question);
        assertEquals("[\"A\",\"C\"]", po.getAnswer());
    }

    @Test
    void shouldNotWrapTrueFalseAnswer() {
        Question question = createTrueFalse("true");
        QuestionPO po = converter.toPO(question);
        assertEquals("true", po.getAnswer());
    }

    @Test
    void shouldNotWrapFillBlankAnswer() {
        Question question = createFillBlank("[\"k1\",\"k2\"]");
        QuestionPO po = converter.toPO(question);
        assertEquals("[\"k1\",\"k2\"]", po.getAnswer());
    }

    // ---- 兼容旧数据 ----

    @Test
    void shouldUnwrapCompatibleWithOldRawString() {
        // 旧数据：单选 answer 存储为非 JSON 格式的裸字符串
        QuestionPO po = QuestionPO.builder()
                .id(1L)
                .type(QuestionType.SINGLE_CHOICE)
                .content("旧数据")
                .answer("A") // 旧格式：裸字符串
                .difficulty(Difficulty.EASY)
                .status(QuestionStatus.ACTIVE)
                .build();
        Question question = converter.toDomain(po);
        assertEquals("A", question.getAnswer());
    }

    @Test
    void shouldUnwrapCompatibleWithJsonBoolean() {
        // 判断题旧数据：如果某天修改过，确保兼容
        QuestionPO po = QuestionPO.builder()
                .id(2L)
                .type(QuestionType.TRUE_FALSE)
                .content("判断")
                .answer("true")
                .difficulty(Difficulty.EASY)
                .status(QuestionStatus.ACTIVE)
                .build();
        Question question = converter.toDomain(po);
        assertEquals("true", question.getAnswer());
    }

    // ---- null 边界 ----

    @Test
    void shouldHandleNullAnswer() {
        Question question = createSingleChoice(null);
        QuestionPO po = converter.toPO(question);
        assertNull(po.getAnswer());
    }

    @Test
    void shouldHandleNullAnswerInDomain() {
        QuestionPO po = QuestionPO.builder()
                .id(1L)
                .type(QuestionType.SINGLE_CHOICE)
                .content("测试")
                .answer(null)
                .difficulty(Difficulty.EASY)
                .status(QuestionStatus.ACTIVE)
                .build();
        Question question = converter.toDomain(po);
        assertNull(question.getAnswer());
    }

    // ---- 选项/标签 JSON 转换 ----

    @Test
    void shouldRoundTripOptions() {
        Question question = createSingleChoice("A");
        QuestionPO po = converter.toPO(question);
        // 选项应序列化为 JSON 数组字符串
        assertNotNull(po.getOptions());
        assertTrue(po.getOptions().startsWith("["));
        Question restored = converter.toDomain(po);
        assertNotNull(restored.getOptions());
        assertEquals(2, restored.getOptions().size());
        assertEquals("A", restored.getOptions().get(0).getKey());
        assertEquals("选项A", restored.getOptions().get(0).getContent());
    }

    @Test
    void shouldRoundTripTags() {
        Question question = createSingleChoice("A");
        QuestionPO po = converter.toPO(question);
        assertNotNull(po.getTags());
        assertTrue(po.getTags().startsWith("["));
        Question restored = converter.toDomain(po);
        assertEquals(List.of("Java"), restored.getTags());
    }
}
