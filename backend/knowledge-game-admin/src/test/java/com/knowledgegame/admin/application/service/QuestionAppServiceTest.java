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
import com.knowledgegame.auth.security.SecurityUtils;
import com.knowledgegame.core.domain.service.recyclebin.RecycleBinItemStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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

    @Mock
    private RecycleBinItemStrategy<Question> recycleBinStrategy;

    @InjectMocks
    private QuestionAppService appService;

    private MockedStatic<SecurityUtils> securityUtilsMock;

    private LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);

    @BeforeEach
    void setUp() {
        securityUtilsMock = org.mockito.Mockito.mockStatic(SecurityUtils.class);
        securityUtilsMock.when(SecurityUtils::getCurrentUsername).thenReturn("admin");
    }

    @AfterEach
    void tearDown() {
        if (securityUtilsMock != null) {
            securityUtilsMock.close();
        }
    }

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
     * 分页查询 - 正常返回（sort=null 时 SortField.parse 返回 null，由 Adapter 决定默认排序）
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
                isNull(), eq(0), eq(20)))
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
     * 删除 - 委托策略 validateDeletable + moveToRecycleBin
     */
    @Test
    void delete_shouldDelegateToStrategy() {
        appService.delete(1L);

        verify(recycleBinStrategy).validateDeletable(1L);
        verify(recycleBinStrategy).moveToRecycleBin(eq(1L), any());
    }

    /**
     * 删除 - 不存在抛异常
     */
    @Test
    void delete_shouldThrow_whenNotFound() {
        doThrow(new BusinessException("题目不存在: 999"))
                .when(recycleBinStrategy).validateDeletable(999L);

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
     * 批量启用 - 完整调用链：加载题目 → 查分类关联 → 批量加载分类 → 逐题校验 → 更新
     */
    @Test
    void batchActivate_shouldValidateBeforeUpdate() {
        List<Long> ids = List.of(1L, 2L);
        Question q1 = buildTestQuestion(1L, "题目一");
        Question q2 = buildTestQuestion(2L, "题目二");
        when(questionRepository.findByIds(ids)).thenReturn(List.of(q1, q2));
        Map<Long, List<Long>> catRelMap = Map.of(1L, List.of(10L), 2L, List.of(20L));
        when(questionRepository.findCategoryIdsByQuestionIds(ids)).thenReturn(catRelMap);
        KnowledgeCategory cat1 = buildCategory(10L, "分类1", KnowledgeCategoryStatus.ACTIVE);
        KnowledgeCategory cat2 = buildCategory(20L, "分类2", KnowledgeCategoryStatus.ACTIVE);
        when(categoryRepositoryPort.findAllByIdIn(anyList())).thenReturn(List.of(cat1, cat2));

        appService.batchActivate(ids);

        verify(questionRepository).findByIds(ids);
        verify(questionRepository).findCategoryIdsByQuestionIds(ids);
        verify(categoryRepositoryPort).findAllByIdIn(anyList());
        verify(questionDomainService).validateActivatable(eq("题目一"), eq(List.of(10L)), any());
        verify(questionDomainService).validateActivatable(eq("题目二"), eq(List.of(20L)), any());
        verify(questionRepository).batchUpdateStatus(ids, QuestionStatus.ACTIVE);
    }

    /**
     * 批量启用 - 校验失败时不调用 batchUpdateStatus
     */
    @Test
    void batchActivate_shouldNotUpdate_whenValidationFails() {
        List<Long> ids = List.of(1L, 2L);
        Question q1 = buildTestQuestion(1L, "题目一");
        Question q2 = buildTestQuestion(2L, "题目二");
        when(questionRepository.findByIds(ids)).thenReturn(List.of(q1, q2));
        Map<Long, List<Long>> catRelMap = Map.of(1L, List.of(10L), 2L, List.of(20L));
        when(questionRepository.findCategoryIdsByQuestionIds(ids)).thenReturn(catRelMap);
        KnowledgeCategory cat1 = buildCategory(10L, "分类1", KnowledgeCategoryStatus.ACTIVE);
        KnowledgeCategory cat2 = buildCategory(20L, "分类2", KnowledgeCategoryStatus.ACTIVE);
        when(categoryRepositoryPort.findAllByIdIn(anyList())).thenReturn(List.of(cat1, cat2));
        doThrow(new BusinessException("题目《题目一》关联的知识点分类《已停用分类》处于停用状态，请先启用对应分类再启用题目"))
                .when(questionDomainService).validateActivatable(eq("题目一"), any(), any());

        assertThrows(BusinessException.class, () -> appService.batchActivate(ids));

        verify(questionDomainService).validateActivatable(eq("题目一"), any(), any());
        verify(questionDomainService, never()).validateActivatable(eq("题目二"), any(), any());
        verify(questionRepository, never()).batchUpdateStatus(any(), any());
    }

    /**
     * 批量启用 - 部分 ID 不存在时抛异常
     */
    @Test
    void batchActivate_shouldThrow_whenSomeIdsNotFound() {
        List<Long> ids = List.of(1L, 999L);
        Question q1 = buildTestQuestion(1L, "题目一");
        when(questionRepository.findByIds(ids)).thenReturn(List.of(q1));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> appService.batchActivate(ids));
        assertEquals("部分题目 ID 不存在", ex.getMessage());
        verify(questionRepository, never()).batchUpdateStatus(any(), any());
    }

    /**
     * 批量禁用 - 验证调用 batchUpdateStatus 传入 INACTIVE（含去重）
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

    private Question buildTestQuestion(Long id, String content) {
        return Question.reconstruct(id, QuestionType.SINGLE_CHOICE, content, List.of(),
                "A", Difficulty.EASY, null, List.of(), QuestionStatus.INACTIVE,
                LocalDateTime.now(), LocalDateTime.now());
    }

    private KnowledgeCategory buildCategory(Long id, String name, KnowledgeCategoryStatus status) {
        return KnowledgeCategory.reconstruct(id, null, name, null, null, null, null, 0,
                status, null, null);
    }
}
