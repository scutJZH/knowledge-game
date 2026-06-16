package com.knowledgegame.core.domain.service;

import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.domainenum.Difficulty;
import com.knowledgegame.core.domain.model.domainenum.QuestionType;
import com.knowledgegame.core.domain.model.entity.Question;
import com.knowledgegame.core.domain.model.vo.QuestionOption;
import com.knowledgegame.core.domain.model.domainenum.KnowledgeCategoryStatus;
import com.knowledgegame.core.domain.model.entity.KnowledgeCategory;
import com.knowledgegame.core.domain.port.outbound.QuestionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * QuestionDomainService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class QuestionDomainServiceTest {

    @Mock
    private QuestionRepository questionRepository;

    /**
     * 校验创建 - 单选题正常
     */
    @Test
    void validateAndCreate_shouldSucceed_whenSingleChoice() {
        QuestionDomainService service = new QuestionDomainService(questionRepository);

        var result = service.validateAndCreate(
                QuestionType.SINGLE_CHOICE, "题目",
                List.of(QuestionOption.of("A", "A"), QuestionOption.of("B", "B")),
                "A", Difficulty.EASY, null, null
        );

        assertNotNull(result);
        assertEquals(QuestionType.SINGLE_CHOICE, result.getType());
    }

    /**
     * 校验创建 - 判断题正常
     */
    @Test
    void validateAndCreate_shouldSucceed_whenTrueFalse() {
        QuestionDomainService service = new QuestionDomainService(questionRepository);

        var result = service.validateAndCreate(
                QuestionType.TRUE_FALSE, "判断题", null, "true",
                Difficulty.EASY, null, null
        );

        assertNotNull(result);
    }

    /**
     * 校验创建 - 填空题正常
     */
    @Test
    void validateAndCreate_shouldSucceed_whenFillBlank() {
        QuestionDomainService service = new QuestionDomainService(questionRepository);

        var result = service.validateAndCreate(
                QuestionType.FILL_BLANK, "填空___", null, "[\"keyword\"]",
                Difficulty.MEDIUM, null, null
        );

        assertNotNull(result);
    }

    /**
     * 校验创建 - 多选题正常
     */
    @Test
    void validateAndCreate_shouldSucceed_whenMultipleChoice() {
        QuestionDomainService service = new QuestionDomainService(questionRepository);

        var result = service.validateAndCreate(
                QuestionType.MULTIPLE_CHOICE, "多选题目",
                List.of(QuestionOption.of("A", "A"), QuestionOption.of("B", "B"), QuestionOption.of("C", "C")),
                "[\"A\",\"C\"]", Difficulty.HARD, null, null
        );

        assertNotNull(result);
    }

    /**
     * 校验创建 - 题目内容为空抛异常
     */
    @Test
    void validateAndCreate_shouldThrow_whenContentBlank() {
        QuestionDomainService service = new QuestionDomainService(questionRepository);

        assertThrows(BusinessException.class,
                () -> service.validateAndCreate(
                        QuestionType.SINGLE_CHOICE, "",
                        List.of(QuestionOption.of("A", "A"), QuestionOption.of("B", "B")),
                        "A", Difficulty.EASY, null, null
                ));
    }

    /**
     * 校验创建 - 题目内容超长抛异常
     */
    @Test
    void validateAndCreate_shouldThrow_whenContentTooLong() {
        QuestionDomainService service = new QuestionDomainService(questionRepository);
        String longContent = "a".repeat(501);

        assertThrows(BusinessException.class,
                () -> service.validateAndCreate(
                        QuestionType.TRUE_FALSE, longContent, null, "true",
                        Difficulty.EASY, null, null
                ));
    }

    /**
     * 校验创建 - 选择题选项少于 2 个抛异常
     */
    @Test
    void validateAndCreate_shouldThrow_whenOptionsTooFew() {
        QuestionDomainService service = new QuestionDomainService(questionRepository);

        assertThrows(BusinessException.class,
                () -> service.validateAndCreate(
                        QuestionType.SINGLE_CHOICE, "题目",
                        List.of(QuestionOption.of("A", "A")),
                        "A", Difficulty.EASY, null, null
                ));
    }

    /**
     * 校验创建 - 选择题选项超过 6 个抛异常
     */
    @Test
    void validateAndCreate_shouldThrow_whenOptionsTooMany() {
        QuestionDomainService service = new QuestionDomainService(questionRepository);
        List<QuestionOption> options = List.of(
                QuestionOption.of("A", "A"), QuestionOption.of("B", "B"),
                QuestionOption.of("C", "C"), QuestionOption.of("D", "D"),
                QuestionOption.of("E", "E"), QuestionOption.of("F", "F"),
                QuestionOption.of("G", "G")
        );

        assertThrows(BusinessException.class,
                () -> service.validateAndCreate(
                        QuestionType.SINGLE_CHOICE, "题目", options,
                        "A", Difficulty.EASY, null, null
                ));
    }

    /**
     * 校验创建 - 判断题有选项抛异常
     */
    @Test
    void validateAndCreate_shouldThrow_whenTrueFalseHasOptions() {
        QuestionDomainService service = new QuestionDomainService(questionRepository);

        assertThrows(BusinessException.class,
                () -> service.validateAndCreate(
                        QuestionType.TRUE_FALSE, "题目",
                        List.of(QuestionOption.of("A", "A")),
                        "true", Difficulty.EASY, null, null
                ));
    }

    /**
     * 校验创建 - 单选题答案不在选项中抛异常
     */
    @Test
    void validateAndCreate_shouldThrow_whenAnswerNotInOptions() {
        QuestionDomainService service = new QuestionDomainService(questionRepository);

        assertThrows(BusinessException.class,
                () -> service.validateAndCreate(
                        QuestionType.SINGLE_CHOICE, "题目",
                        List.of(QuestionOption.of("A", "A"), QuestionOption.of("B", "B")),
                        "C", Difficulty.EASY, null, null
                ));
    }

    /**
     * 校验创建 - 多选题答案少于 2 个抛异常
     */
    @Test
    void validateAndCreate_shouldThrow_whenMultiChoiceOnlyOneAnswer() {
        QuestionDomainService service = new QuestionDomainService(questionRepository);

        assertThrows(BusinessException.class,
                () -> service.validateAndCreate(
                        QuestionType.MULTIPLE_CHOICE, "题目",
                        List.of(QuestionOption.of("A", "A"), QuestionOption.of("B", "B")),
                        "A", Difficulty.EASY, null, null
                ));
    }

    /**
     * 校验创建 - 标签超过 10 个抛异常
     */
    @Test
    void validateAndCreate_shouldThrow_whenTooManyTags() {
        QuestionDomainService service = new QuestionDomainService(questionRepository);

        List<String> tags = IntStream.range(0, 11)
                .mapToObj(i -> "tag" + i).toList();

        assertThrows(BusinessException.class,
                () -> service.validateAndCreate(
                        QuestionType.TRUE_FALSE, "题目", null, "true",
                        Difficulty.EASY, null, tags
                ));
    }

    /**
     * 校验创建 - 题目内容为 null 抛异常
     */
    @Test
    void validateAndCreate_shouldThrow_whenContentNull() {
        QuestionDomainService service = new QuestionDomainService(questionRepository);

        assertThrows(BusinessException.class,
                () -> service.validateAndCreate(
                        QuestionType.TRUE_FALSE, null, null, "true",
                        Difficulty.EASY, null, null
                ));
    }

    /**
     * 校验创建 - 题目内容恰好 500 字不抛异常
     */
    @Test
    void validateAndCreate_shouldSucceed_whenContentExactly500() {
        QuestionDomainService service = new QuestionDomainService(questionRepository);
        String content = "a".repeat(500);

        var result = service.validateAndCreate(
                QuestionType.TRUE_FALSE, content, null, "true",
                Difficulty.EASY, null, null
        );

        assertNotNull(result);
        assertEquals(500, result.getContent().length());
    }

    /**
     * 校验创建 - 题目内容 501 字抛异常
     */
    @Test
    void validateAndCreate_shouldThrow_whenContent501() {
        QuestionDomainService service = new QuestionDomainService(questionRepository);
        String content = "a".repeat(501);

        assertThrows(BusinessException.class,
                () -> service.validateAndCreate(
                        QuestionType.TRUE_FALSE, content, null, "true",
                        Difficulty.EASY, null, null
                ));
    }

    /**
     * 校验创建 - 判断题答案不是 true/false 抛异常
     */
    @Test
    void validateAndCreate_shouldThrow_whenTrueFalseInvalidAnswer() {
        QuestionDomainService service = new QuestionDomainService(questionRepository);

        assertThrows(BusinessException.class,
                () -> service.validateAndCreate(
                        QuestionType.TRUE_FALSE, "题目", null, "yes",
                        Difficulty.EASY, null, null
                ));
    }

    /**
     * 校验创建 - 判断题答案为 false 正常
     */
    @Test
    void validateAndCreate_shouldSucceed_whenTrueFalseAnswerFalse() {
        QuestionDomainService service = new QuestionDomainService(questionRepository);

        var result = service.validateAndCreate(
                QuestionType.TRUE_FALSE, "判断题", null, "false",
                Difficulty.EASY, null, null
        );

        assertNotNull(result);
        assertEquals("false", result.getAnswer());
    }

    /**
     * 校验创建 - 填空题答案为空抛异常
     */
    @Test
    void validateAndCreate_shouldThrow_whenFillBlankAnswerBlank() {
        QuestionDomainService service = new QuestionDomainService(questionRepository);

        assertThrows(BusinessException.class,
                () -> service.validateAndCreate(
                        QuestionType.FILL_BLANK, "填空___", null, "   ",
                        Difficulty.EASY, null, null
                ));
    }

    /**
     * 校验创建 - 填空题答案为 null 抛异常
     */
    @Test
    void validateAndCreate_shouldThrow_whenFillBlankAnswerNull() {
        QuestionDomainService service = new QuestionDomainService(questionRepository);

        assertThrows(BusinessException.class,
                () -> service.validateAndCreate(
                        QuestionType.FILL_BLANK, "填空___", null, null,
                        Difficulty.EASY, null, null
                ));
    }

    /**
     * 校验创建 - 填空题有选项抛异常
     */
    @Test
    void validateAndCreate_shouldThrow_whenFillBlankHasOptions() {
        QuestionDomainService service = new QuestionDomainService(questionRepository);

        assertThrows(BusinessException.class,
                () -> service.validateAndCreate(
                        QuestionType.FILL_BLANK, "填空___",
                        List.of(QuestionOption.of("A", "A")),
                        "答案", Difficulty.EASY, null, null
                ));
    }

    /**
     * 校验创建 - 单选题选项为 null 抛异常
     */
    @Test
    void validateAndCreate_shouldThrow_whenSingleChoiceOptionsNull() {
        QuestionDomainService service = new QuestionDomainService(questionRepository);

        assertThrows(BusinessException.class,
                () -> service.validateAndCreate(
                        QuestionType.SINGLE_CHOICE, "题目", null, "A",
                        Difficulty.EASY, null, null
                ));
    }

    /**
     * 校验创建 - 选项恰好 2 个正常
     */
    @Test
    void validateAndCreate_shouldSucceed_whenExactly2Options() {
        QuestionDomainService service = new QuestionDomainService(questionRepository);

        var result = service.validateAndCreate(
                QuestionType.SINGLE_CHOICE, "题目",
                List.of(QuestionOption.of("A", "A"), QuestionOption.of("B", "B")),
                "A", Difficulty.EASY, null, null
        );

        assertNotNull(result);
        assertEquals(2, result.getOptions().size());
    }

    /**
     * 校验创建 - 选项恰好 6 个正常
     */
    @Test
    void validateAndCreate_shouldSucceed_whenExactly6Options() {
        QuestionDomainService service = new QuestionDomainService(questionRepository);
        List<QuestionOption> options = List.of(
                QuestionOption.of("A", "A"), QuestionOption.of("B", "B"),
                QuestionOption.of("C", "C"), QuestionOption.of("D", "D"),
                QuestionOption.of("E", "E"), QuestionOption.of("F", "F")
        );

        var result = service.validateAndCreate(
                QuestionType.SINGLE_CHOICE, "题目", options,
                "A", Difficulty.EASY, null, null
        );

        assertNotNull(result);
        assertEquals(6, result.getOptions().size());
    }

    /**
     * 校验创建 - 多选题答案格式错误（不以 [ 开头）抛异常
     */
    @Test
    void validateAndCreate_shouldThrow_whenMultiChoiceBadFormat() {
        QuestionDomainService service = new QuestionDomainService(questionRepository);

        assertThrows(BusinessException.class,
                () -> service.validateAndCreate(
                        QuestionType.MULTIPLE_CHOICE, "题目",
                        List.of(QuestionOption.of("A", "A"), QuestionOption.of("B", "B")),
                        "A,B", Difficulty.EASY, null, null
                ));
    }

    /**
     * 校验创建 - 多选题答案不在选项中抛异常
     */
    @Test
    void validateAndCreate_shouldThrow_whenMultiChoiceAnswerNotInOptions() {
        QuestionDomainService service = new QuestionDomainService(questionRepository);

        assertThrows(BusinessException.class,
                () -> service.validateAndCreate(
                        QuestionType.MULTIPLE_CHOICE, "题目",
                        List.of(QuestionOption.of("A", "A"), QuestionOption.of("B", "B"), QuestionOption.of("C", "C")),
                        "[\"A\",\"Z\"]", Difficulty.EASY, null, null
                ));
    }

    /**
     * 校验创建 - 单个标签超过 20 字抛异常
     */
    @Test
    void validateAndCreate_shouldThrow_whenSingleTagTooLong() {
        QuestionDomainService service = new QuestionDomainService(questionRepository);
        String longTag = "a".repeat(21);

        assertThrows(BusinessException.class,
                () -> service.validateAndCreate(
                        QuestionType.TRUE_FALSE, "题目", null, "true",
                        Difficulty.EASY, null, List.of(longTag)
                ));
    }

    /**
     * 校验创建 - 标签恰好 10 个正常
     */
    @Test
    void validateAndCreate_shouldSucceed_whenExactly10Tags() {
        QuestionDomainService service = new QuestionDomainService(questionRepository);
        List<String> tags = IntStream.range(0, 10)
                .mapToObj(i -> "tag" + i).toList();

        var result = service.validateAndCreate(
                QuestionType.TRUE_FALSE, "题目", null, "true",
                Difficulty.EASY, null, tags
        );

        assertNotNull(result);
        assertEquals(10, result.getTags().size());
    }

    /**
     * 校验创建 - 单个标签恰好 20 字正常
     */
    @Test
    void validateAndCreate_shouldSucceed_whenTagExactly20() {
        QuestionDomainService service = new QuestionDomainService(questionRepository);
        String tag20 = "a".repeat(20);

        var result = service.validateAndCreate(
                QuestionType.TRUE_FALSE, "题目", null, "true",
                Difficulty.EASY, null, List.of(tag20)
        );

        assertNotNull(result);
        assertEquals(20, result.getTags().get(0).length());
    }

    /**
     * 校验创建 - tags 为 null 正常
     */
    @Test
    void validateAndCreate_shouldSucceed_whenTagsNull() {
        QuestionDomainService service = new QuestionDomainService(questionRepository);

        var result = service.validateAndCreate(
                QuestionType.TRUE_FALSE, "题目", null, "true",
                Difficulty.EASY, "解析", null
        );

        assertNotNull(result);
        assertNull(result.getTags());
    }

    // ==================== validateUpdate 测试 ====================

    /**
     * 校验更新 - 正常更新全部字段
     */
    @Test
    void validateUpdate_shouldSucceed_whenValidFields() {
        QuestionDomainService service = new QuestionDomainService(questionRepository);
        Question existing = Question.create(
                QuestionType.SINGLE_CHOICE, "旧题目",
                List.of(QuestionOption.of("A", "A"), QuestionOption.of("B", "B")),
                "A", Difficulty.EASY, null, null);

        service.validateUpdate(
                existing, "新内容",
                List.of(QuestionOption.of("A", "A"), QuestionOption.of("B", "B")),
                "A", List.of("标签")
        );
    }

    /**
     * 校验更新 - 所有字段为 null 不校验（不抛异常）
     */
    @Test
    void validateUpdate_shouldSucceed_whenAllNull() {
        QuestionDomainService service = new QuestionDomainService(questionRepository);
        Question existing = Question.create(
                QuestionType.SINGLE_CHOICE, "旧题目",
                List.of(QuestionOption.of("A", "A"), QuestionOption.of("B", "B")),
                "A", Difficulty.EASY, null, null);

        service.validateUpdate(
                existing, null, null, null, null
        );
    }

    /**
     * 校验更新 - 内容为空抛异常
     */
    @Test
    void validateUpdate_shouldThrow_whenContentBlank() {
        QuestionDomainService service = new QuestionDomainService(questionRepository);
        Question existing = Question.create(
                QuestionType.TRUE_FALSE, "旧题目", null, "true", Difficulty.EASY, null, null);

        assertThrows(BusinessException.class,
                () -> service.validateUpdate(
                        existing, "", null, null, null
                ));
    }

    /**
     * 校验更新 - 内容超长抛异常
     */
    @Test
    void validateUpdate_shouldThrow_whenContentTooLong() {
        QuestionDomainService service = new QuestionDomainService(questionRepository);
        Question existing = Question.create(
                QuestionType.TRUE_FALSE, "旧题目", null, "true", Difficulty.EASY, null, null);

        assertThrows(BusinessException.class,
                () -> service.validateUpdate(
                        existing, "a".repeat(501), null, null, null
                ));
    }

    /**
     * 校验更新 - 标签超过 10 个抛异常
     */
    @Test
    void validateUpdate_shouldThrow_whenTooManyTags() {
        QuestionDomainService service = new QuestionDomainService(questionRepository);
        Question existing = Question.create(
                QuestionType.TRUE_FALSE, "旧题目", null, "true", Difficulty.EASY, null, null);
        List<String> tags = IntStream.range(0, 11)
                .mapToObj(i -> "tag" + i).toList();

        assertThrows(BusinessException.class,
                () -> service.validateUpdate(
                        existing, null, null, null, tags
                ));
    }

    /**
     * 校验更新 - 单个标签超过 20 字抛异常
     */
    @Test
    void validateUpdate_shouldThrow_whenTagTooLong() {
        QuestionDomainService service = new QuestionDomainService(questionRepository);
        Question existing = Question.create(
                QuestionType.TRUE_FALSE, "旧题目", null, "true", Difficulty.EASY, null, null);

        assertThrows(BusinessException.class,
                () -> service.validateUpdate(
                        existing, null, null, null,
                        List.of("a".repeat(21))
                ));
    }

    /**
     * 校验更新 - options 不为 null 时触发类型校验（选项太少抛异常）
     */
    @Test
    void validateUpdate_shouldThrow_whenOptionsProvidedButTooFew() {
        QuestionDomainService service = new QuestionDomainService(questionRepository);
        Question existing = Question.create(
                QuestionType.SINGLE_CHOICE, "旧题目",
                List.of(QuestionOption.of("A", "A"), QuestionOption.of("B", "B")),
                "A", Difficulty.EASY, null, null);

        assertThrows(BusinessException.class,
                () -> service.validateUpdate(
                        existing, null,
                        List.of(QuestionOption.of("A", "A")),
                        "A", null
                ));
    }

    /**
     * 校验更新 - answer 不为 null 但 options 为 null 时，使用已有选项校验（正常通过）
     */
    @Test
    void validateUpdate_shouldSucceed_whenAnswerUpdatedWithExistingOptions() {
        QuestionDomainService service = new QuestionDomainService(questionRepository);
        Question existing = Question.create(
                QuestionType.SINGLE_CHOICE, "旧题目",
                List.of(QuestionOption.of("A", "A"), QuestionOption.of("B", "B")),
                "A", Difficulty.EASY, null, null);

        service.validateUpdate(
                existing, null, null, "B", null
        );
    }

    /**
     * 校验更新 - 判断题类型只提供 answer（options 为 null），正常通过
     */
    @Test
    void validateUpdate_shouldSucceed_whenTrueFalseWithAnswerOnly() {
        QuestionDomainService service = new QuestionDomainService(questionRepository);
        Question existing = Question.create(
                QuestionType.TRUE_FALSE, "旧题目", null, "true", Difficulty.EASY, null, null);

        service.validateUpdate(
                existing, null, null, "false", null
        );
    }

    /**
     * 校验更新 - 空标签列表正常通过
     */
    @Test
    void validateUpdate_shouldSucceed_whenEmptyTags() {
        QuestionDomainService service = new QuestionDomainService(questionRepository);
        Question existing = Question.create(
                QuestionType.TRUE_FALSE, "旧题目", null, "true", Difficulty.EASY, null, null);

        service.validateUpdate(
                existing, null, null, null, List.of()
        );
    }

    /**
     * 校验更新 - 仅更新 content，其他字段为 null 不触发类型校验
     */
    @Test
    void validateUpdate_shouldSucceed_whenOnlyContentProvided() {
        QuestionDomainService service = new QuestionDomainService(questionRepository);
        Question existing = Question.create(
                QuestionType.SINGLE_CHOICE, "旧题目",
                List.of(QuestionOption.of("A", "A"), QuestionOption.of("B", "B")),
                "A", Difficulty.EASY, null, null);

        service.validateUpdate(
                existing, "新内容", null, null, null
        );
    }

    /**
     * 校验更新 - 仅更新 tags，content 和 options/answer 为 null 不触发类型和内容校验
     */
    @Test
    void validateUpdate_shouldSucceed_whenOnlyTagsProvided() {
        QuestionDomainService service = new QuestionDomainService(questionRepository);
        Question existing = Question.create(
                QuestionType.SINGLE_CHOICE, "旧题目",
                List.of(QuestionOption.of("A", "A"), QuestionOption.of("B", "B")),
                "A", Difficulty.EASY, null, null);

        service.validateUpdate(
                existing, null, null, null, List.of("新标签")
        );
    }

    // ======================== validateActivatable ========================

    /**
     * validateActivatable：题目未关联任何分类时允许启用
     */
    @Test
    @DisplayName("validateActivatable 题目未关联任何分类时应通过")
    void validateActivatable_shouldPass_whenNoCategories() {
        QuestionDomainService service = new QuestionDomainService(questionRepository);
        service.validateActivatable("测试题", List.of(), Map.of());
        // 无异常即通过
    }

    /**
     * validateActivatable：全部关联分类为 ACTIVE 时允许启用（纯内存校验）
     */
    @Test
    @DisplayName("validateActivatable 全部关联分类为 ACTIVE 时应通过")
    void validateActivatable_shouldPass_whenAllCategoriesActive() {
        KnowledgeCategory cat1 = buildCategory(10L, "Java基础", KnowledgeCategoryStatus.ACTIVE);
        KnowledgeCategory cat2 = buildCategory(20L, "面向对象", KnowledgeCategoryStatus.ACTIVE);
        Map<Long, KnowledgeCategory> catMap = Map.of(10L, cat1, 20L, cat2);

        QuestionDomainService service = new QuestionDomainService(questionRepository);
        service.validateActivatable("测试题", List.of(10L, 20L), catMap);
        // 无异常即通过
    }

    /**
     * validateActivatable：存在 INACTIVE 分类时抛异常，消息列出全部 INACTIVE 名称
     */
    @Test
    @DisplayName("validateActivatable 存在 INACTIVE 分类时应抛异常（列出全部 INACTIVE 名称）")
    void validateActivatable_shouldThrow_whenHasInactiveCategories() {
        KnowledgeCategory cat1 = buildCategory(10L, "Java基础", KnowledgeCategoryStatus.INACTIVE);
        KnowledgeCategory cat2 = buildCategory(20L, "面向对象", KnowledgeCategoryStatus.INACTIVE);
        Map<Long, KnowledgeCategory> catMap = Map.of(10L, cat1, 20L, cat2);

        QuestionDomainService service = new QuestionDomainService(questionRepository);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateActivatable("测试题", List.of(10L, 20L), catMap));
        assertEquals("题目《测试题》关联的知识点分类《Java基础》、《面向对象》处于停用状态，请先启用对应分类再启用题目",
                ex.getMessage());
    }

    /**
     * validateActivatable：部分 INACTIVE 时消息只列出 INACTIVE 的分类名
     */
    @Test
    @DisplayName("validateActivatable 部分分类 INACTIVE 时只列出 INACTIVE 的名称")
    void validateActivatable_shouldListOnlyInactiveNames() {
        KnowledgeCategory cat1 = buildCategory(10L, "Java基础", KnowledgeCategoryStatus.ACTIVE);
        KnowledgeCategory cat2 = buildCategory(20L, "面向对象", KnowledgeCategoryStatus.INACTIVE);
        Map<Long, KnowledgeCategory> catMap = Map.of(10L, cat1, 20L, cat2);

        QuestionDomainService service = new QuestionDomainService(questionRepository);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateActivatable("测试题", List.of(10L, 20L), catMap));
        assertEquals("题目《测试题》关联的知识点分类《面向对象》处于停用状态，请先启用对应分类再启用题目",
                ex.getMessage());
    }

    /**
     * validateActivatable：categoryMap 中缺少某分类 ID 时视为已删除，报告异常
     */
    @Test
    @DisplayName("validateActivatable categoryMap 中缺少分类 ID 时应报 INACTIVE")
    void validateActivatable_shouldTreatMissingCategoryAsInactive() {
        KnowledgeCategory cat1 = buildCategory(10L, "Java基础", KnowledgeCategoryStatus.ACTIVE);
        Map<Long, KnowledgeCategory> catMap = Map.of(10L, cat1);

        QuestionDomainService service = new QuestionDomainService(questionRepository);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateActivatable("测试题", List.of(10L, 999L), catMap));
        assertEquals("题目《测试题》关联的知识点分类《(ID=999)》处于停用状态，请先启用对应分类再启用题目",
                ex.getMessage());
    }

    // ======================== 辅助方法 ========================

    private KnowledgeCategory buildCategory(Long id, String name, KnowledgeCategoryStatus status) {
        return KnowledgeCategory.reconstruct(id, null, name, null, null, null, null, 0,
                status, null, null);
    }
}
