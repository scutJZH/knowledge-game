package com.knowledgegame.admin.application.service;

import com.knowledgegame.admin.api.dto.request.CreateQuestionRequest;
import com.knowledgegame.admin.api.dto.request.UpdateQuestionRequest;
import com.knowledgegame.admin.api.dto.response.QuestionResponse;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.domainenum.Difficulty;
import com.knowledgegame.core.domain.model.domainenum.QuestionStatus;
import com.knowledgegame.core.domain.model.domainenum.QuestionType;
import com.knowledgegame.core.domain.model.entity.Question;
import com.knowledgegame.core.domain.model.vo.PageResult;
import com.knowledgegame.core.domain.model.vo.QuestionOption;
import com.knowledgegame.core.domain.model.vo.SortField;
import com.knowledgegame.core.domain.model.entity.KnowledgeCategory;
import com.knowledgegame.core.domain.model.domainenum.KnowledgeCategoryStatus;
import com.knowledgegame.core.domain.port.outbound.KnowledgeCategoryRepositoryPort;
import com.knowledgegame.core.domain.port.outbound.QuestionRepository;
import com.knowledgegame.core.domain.service.QuestionDomainService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * QuestionAppService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class QuestionAppServiceTest {

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private QuestionDomainService questionDomainService;

    @Mock
    private KnowledgeCategoryRepositoryPort categoryRepositoryPort;

    @InjectMocks
    private QuestionAppService appService;

    private LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);

    /**
     * 创建题目 - 正常创建
     */
    @Test
    void create_shouldSucceed() {
        CreateQuestionRequest request = new CreateQuestionRequest();
        request.setType("SINGLE_CHOICE");
        request.setContent("Java 是什么语言？");
        request.setAnswer("A");
        request.setDifficulty(1);
        request.setOptions(List.of(
                buildOptionItem("A", "面向对象"),
                buildOptionItem("B", "面向过程")
        ));

        Question newQuestion = Question.create(
                QuestionType.SINGLE_CHOICE, "Java 是什么语言？",
                List.of(QuestionOption.of("A", "面向对象"), QuestionOption.of("B", "面向过程")),
                "A", Difficulty.EASY, null, null);
        when(questionDomainService.validateAndCreate(
                eq(QuestionType.SINGLE_CHOICE), eq("Java 是什么语言？"), any(), eq("A"),
                eq(Difficulty.EASY), eq(null), eq(null)))
                .thenReturn(newQuestion);

        Question saved = Question.reconstruct(1L, QuestionType.SINGLE_CHOICE, "Java 是什么语言？",
                List.of(QuestionOption.of("A", "面向对象"), QuestionOption.of("B", "面向过程")),
                "A", Difficulty.EASY, null, null, QuestionStatus.ACTIVE, now, now);
        when(questionRepository.save(any())).thenReturn(saved);
        when(questionRepository.findActiveCategoryIdsByQuestionId(1L)).thenReturn(List.of());

        QuestionResponse result = appService.create(request);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Java 是什么语言？", result.getContent());
    }

    /**
     * 创建题目 - 带分类 ID
     */
    @Test
    void create_shouldSucceed_withCategoryIds() {
        CreateQuestionRequest request = new CreateQuestionRequest();
        request.setType("TRUE_FALSE");
        request.setContent("地球是平的");
        request.setAnswer("false");
        request.setDifficulty(2);
        request.setCategoryIds(List.of(10L, 20L));

        Question newQuestion = Question.create(
                QuestionType.TRUE_FALSE, "地球是平的", null,
                "false", Difficulty.MEDIUM, null, null);
        when(questionDomainService.validateAndCreate(
                eq(QuestionType.TRUE_FALSE), eq("地球是平的"), eq(null), eq("false"),
                eq(Difficulty.MEDIUM), eq(null), eq(null)))
                .thenReturn(newQuestion);

        Question saved = Question.reconstruct(2L, QuestionType.TRUE_FALSE, "地球是平的",
                null, "false", Difficulty.MEDIUM, null, null, QuestionStatus.ACTIVE, now, now);
        when(questionRepository.save(any())).thenReturn(saved);
        when(questionRepository.findActiveCategoryIdsByQuestionId(2L)).thenReturn(List.of(10L, 20L));
        when(categoryRepositoryPort.findById(10L)).thenReturn(Optional.of(
                KnowledgeCategory.reconstruct(10L, null, "分类A", null, null, null, null, 0,
                        KnowledgeCategoryStatus.ACTIVE, now, now)));
        when(categoryRepositoryPort.findById(20L)).thenReturn(Optional.of(
                KnowledgeCategory.reconstruct(20L, null, "分类B", null, null, null, null, 0,
                        KnowledgeCategoryStatus.ACTIVE, now, now)));

        QuestionResponse result = appService.create(request);

        assertNotNull(result);
        verify(questionRepository).saveCategoryRelations(2L, List.of(10L, 20L));
        assertEquals(2, result.getCategoryIds().size());
    }

    /**
     * 查询详情 - 正常返回
     */
    @Test
    void getById_shouldReturn_whenExists() {
        Question question = Question.reconstruct(1L, QuestionType.SINGLE_CHOICE, "测试题目",
                List.of(QuestionOption.of("A", "选项A"), QuestionOption.of("B", "选项B")),
                "A", Difficulty.EASY, null, null, QuestionStatus.ACTIVE, now, now);
        when(questionRepository.findById(1L)).thenReturn(Optional.of(question));
        when(questionRepository.findActiveCategoryIdsByQuestionId(1L)).thenReturn(List.of(10L));

        QuestionResponse result = appService.getById(1L);

        assertEquals(1L, result.getId());
        assertEquals("测试题目", result.getContent());
        assertEquals(List.of(10L), result.getCategoryIds());
    }

    /**
     * 查询详情 - 不存在抛异常
     */
    @Test
    void getById_shouldThrow_whenNotFound() {
        when(questionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> appService.getById(999L));
    }

    /**
     * 分页查询 - 正常返回
     */
    @Test
    void list_shouldReturnPagedResult() {
        Question question = Question.reconstruct(1L, QuestionType.SINGLE_CHOICE, "题目1",
                List.of(QuestionOption.of("A", "A"), QuestionOption.of("B", "B")),
                "A", Difficulty.EASY, null, null, QuestionStatus.ACTIVE, now, now);
        PageResult<Question> mockPage = PageResult.<Question>builder()
                .content(List.of(question))
                .totalElements(1)
                .pageNumber(0)
                .pageSize(20)
                .totalPages(1)
                .build();
        when(questionRepository.findByConditions(
                eq(null), eq(null), eq(null), eq(null), eq(null), eq(null),
                any(SortField.class), eq(0), eq(20)))
                .thenReturn(mockPage);

        PageResult<QuestionResponse> result = appService.list(
                null, null, null, null, null, null, null, null, 0, 20);

        assertEquals(1, result.getContent().size());
        assertEquals(1L, result.getTotalElements());
    }

    /**
     * 分页查询 - 带排序参数
     */
    @Test
    void list_shouldPassSortParameters() {
        PageResult<Question> mockPage = PageResult.<Question>builder()
                .content(List.of())
                .totalElements(0)
                .pageNumber(0)
                .pageSize(20)
                .totalPages(0)
                .build();
        when(questionRepository.findByConditions(
                eq("关键字"), eq(null), eq(null), eq(null), eq(null), eq(null),
                any(SortField.class), eq(0), eq(20)))
                .thenReturn(mockPage);

        PageResult<QuestionResponse> result = appService.list(
                "关键字", null, null, null, null, null, "createdAt", "asc", 0, 20);

        assertEquals(0, result.getContent().size());
        verify(questionRepository).findByConditions(
                eq("关键字"), eq(null), eq(null), eq(null), eq(null), eq(null),
                argThat(sf -> sf != null && sf.getField().equals("createdAt")
                        && sf.getDirection() == SortField.Direction.ASC),
                eq(0), eq(20));
    }

    /**
     * 更新题目 - 正常更新
     */
    @Test
    void update_shouldSucceed() {
        Question existing = Question.reconstruct(1L, QuestionType.SINGLE_CHOICE, "旧题目",
                List.of(QuestionOption.of("A", "A"), QuestionOption.of("B", "B")),
                "A", Difficulty.EASY, null, null, QuestionStatus.ACTIVE, now, now);
        when(questionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(questionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(questionRepository.findActiveCategoryIdsByQuestionId(1L)).thenReturn(List.of());

        UpdateQuestionRequest request = new UpdateQuestionRequest();
        request.setContent("新题目");
        request.setDifficulty(2);

        QuestionResponse result = appService.update(1L, request);

        assertNotNull(result);
        verify(questionDomainService).validateUpdate(
                any(Question.class), eq("新题目"), eq(null), eq(null), eq(null));
        verify(questionRepository).save(any());
    }

    /**
     * 更新题目 - 不存在抛异常
     */
    @Test
    void update_shouldThrow_whenNotFound() {
        when(questionRepository.findById(999L)).thenReturn(Optional.empty());

        UpdateQuestionRequest request = new UpdateQuestionRequest();
        request.setContent("新内容");

        assertThrows(BusinessException.class, () -> appService.update(999L, request));
    }

    /**
     * 软删除 - status 变为 INACTIVE
     */
    @Test
    void delete_shouldDeactivate() {
        Question existing = Question.reconstruct(1L, QuestionType.SINGLE_CHOICE, "题目",
                List.of(QuestionOption.of("A", "A"), QuestionOption.of("B", "B")),
                "A", Difficulty.EASY, null, null, QuestionStatus.ACTIVE, now, now);
        when(questionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(questionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        appService.delete(1L);

        verify(questionRepository).save(argThat(q ->
                q.getStatus() == QuestionStatus.INACTIVE
        ));
    }

    /**
     * 软删除 - 不存在抛异常
     */
    @Test
    void delete_shouldThrow_whenNotFound() {
        when(questionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> appService.delete(999L));
    }

    /**
     * 查询分类 ID - 正常返回
     */
    @Test
    void getCategoryIds_shouldReturn_whenExists() {
        Question question = Question.reconstruct(1L, QuestionType.SINGLE_CHOICE, "题目",
                List.of(QuestionOption.of("A", "A"), QuestionOption.of("B", "B")),
                "A", Difficulty.EASY, null, null, QuestionStatus.ACTIVE, now, now);
        when(questionRepository.findById(1L)).thenReturn(Optional.of(question));
        when(questionRepository.findActiveCategoryIdsByQuestionId(1L)).thenReturn(List.of(10L, 20L));

        List<Long> result = appService.getCategoryIds(1L);

        assertEquals(2, result.size());
        assertEquals(List.of(10L, 20L), result);
    }

    /**
     * 查询分类 ID - 不存在抛异常
     */
    @Test
    void getCategoryIds_shouldThrow_whenNotFound() {
        when(questionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> appService.getCategoryIds(999L));
    }

    /**
     * 更新分类关联 - 正常更新
     */
    @Test
    void updateCategories_shouldSucceed_whenExists() {
        Question question = Question.reconstruct(1L, QuestionType.SINGLE_CHOICE, "题目",
                List.of(QuestionOption.of("A", "A"), QuestionOption.of("B", "B")),
                "A", Difficulty.EASY, null, null, QuestionStatus.ACTIVE, now, now);
        when(questionRepository.findById(1L)).thenReturn(Optional.of(question));
        when(categoryRepositoryPort.findById(10L)).thenReturn(Optional.of(
                KnowledgeCategory.reconstruct(10L, null, "分类A", null, null, null, null, 0,
                        KnowledgeCategoryStatus.ACTIVE, now, now)));
        when(categoryRepositoryPort.findById(20L)).thenReturn(Optional.of(
                KnowledgeCategory.reconstruct(20L, null, "分类B", null, null, null, null, 0,
                        KnowledgeCategoryStatus.ACTIVE, now, now)));

        appService.updateCategories(1L, List.of(10L, 20L));

        verify(questionRepository).saveCategoryRelations(1L, List.of(10L, 20L));
    }

    /**
     * 更新分类关联 - 不存在抛异常
     */
    @Test
    void updateCategories_shouldThrow_whenNotFound() {
        when(questionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> appService.updateCategories(999L, List.of(1L)));
    }

    /**
     * 批量启用 - 验证调用 batchUpdateStatus 传入 ACTIVE
     */
    @Test
    void batchActivate_shouldCallRepoWithActive() {
        List<Long> ids = List.of(1L, 2L, 3L);

        appService.batchActivate(ids);

        verify(questionRepository).batchUpdateStatus(ids, QuestionStatus.ACTIVE);
    }

    /**
     * 批量禁用 - 验证调用 batchUpdateStatus 传入 INACTIVE
     */
    @Test
    void batchDeactivate_shouldCallRepoWithInactive() {
        List<Long> ids = List.of(4L, 5L, 6L);

        appService.batchDeactivate(ids);

        verify(questionRepository).batchUpdateStatus(ids, QuestionStatus.INACTIVE);
    }

    /**
     * 构建选项 DTO 辅助方法
     */
    private CreateQuestionRequest.OptionItem buildOptionItem(String key, String content) {
        CreateQuestionRequest.OptionItem item = new CreateQuestionRequest.OptionItem();
        item.setKey(key);
        item.setContent(content);
        return item;
    }
}
