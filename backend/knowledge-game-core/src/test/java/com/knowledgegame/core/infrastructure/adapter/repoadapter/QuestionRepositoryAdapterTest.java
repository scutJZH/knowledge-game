package com.knowledgegame.core.infrastructure.adapter.repoadapter;

import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.vo.SortField;
import com.knowledgegame.core.infrastructure.db.entity.QuestionCategoryRelationPO;
import com.knowledgegame.core.infrastructure.db.entity.QuestionPO;
import com.knowledgegame.core.infrastructure.db.repository.QuestionCategoryRelationJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.QuestionJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * QuestionRepositoryAdapter 单元测试
 * <p>
 * 覆盖：
 * <ul>
 *   <li>REQ-94：countActiveByCategoryId、findCategoryIdsByQuestionIds</li>
 *   <li>REQ-86 ISSUE-2：findByConditions 排序白名单校验（ArgumentCaptor 抓 PageRequest.getSort 断言）</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class QuestionRepositoryAdapterTest {

    @Mock
    private QuestionJpaRepository questionJpaRepository;

    @Mock
    private QuestionCategoryRelationJpaRepository relationJpaRepository;

    @InjectMocks
    private QuestionRepositoryAdapter adapter;

    /**
     * countActiveByCategoryId 应委托给关联表 JPA 查询
     */
    @Test
    @DisplayName("countActiveByCategoryId 应返回关联表查询的 ACTIVE 题目数量")
    void countActiveByCategoryId_shouldReturnActiveQuestionCount() {
        when(relationJpaRepository.countActiveQuestionsByCategoryId(5L)).thenReturn(3L);

        long count = adapter.countActiveByCategoryId(5L);

        assertEquals(3L, count);
        verify(relationJpaRepository).countActiveQuestionsByCategoryId(5L);
    }

    /**
     * countActiveByCategoryId 无 ACTIVE 题目时返回 0
     */
    @Test
    @DisplayName("countActiveByCategoryId 无 ACTIVE 题目时应返回 0")
    void countActiveByCategoryId_shouldReturnZeroWhenNoActiveQuestions() {
        when(relationJpaRepository.countActiveQuestionsByCategoryId(99L)).thenReturn(0L);

        long count = adapter.countActiveByCategoryId(99L);

        assertEquals(0L, count);
    }

    /**
     * findCategoryIdsByQuestionIds 正常返回分组 Map
     */
    @Test
    @DisplayName("findCategoryIdsByQuestionIds 应返回 questionId→categoryIds 分组 Map")
    void findCategoryIdsByQuestionIds_shouldReturnGroupedMap() {
        List<QuestionCategoryRelationPO> relations = List.of(
                buildRelation(1L, 10L),
                buildRelation(1L, 20L),
                buildRelation(2L, 30L)
        );
        when(relationJpaRepository.findAllByQuestionIdIn(anyList())).thenReturn(relations);

        Map<Long, List<Long>> result = adapter.findCategoryIdsByQuestionIds(List.of(1L, 2L));

        assertEquals(2, result.size());
        assertEquals(List.of(10L, 20L), result.get(1L));
        assertEquals(List.of(30L), result.get(2L));
    }

    /**
     * findCategoryIdsByQuestionIds 无关联时返回空 Map
     */
    @Test
    @DisplayName("findCategoryIdsByQuestionIds 无关联时应返回空 Map")
    void findCategoryIdsByQuestionIds_shouldReturnEmptyMapWhenNoRelations() {
        when(relationJpaRepository.findAllByQuestionIdIn(anyList())).thenReturn(List.of());

        Map<Long, List<Long>> result = adapter.findCategoryIdsByQuestionIds(List.of(1L));

        assertTrue(result.isEmpty());
    }

    /**
     * findCategoryIdsByQuestionIds 某题目无关联时，该题目不出现在 Map 中
     */
    @Test
    @DisplayName("findCategoryIdsByQuestionIds 某题目无关联时不应出现在结果 Map 中")
    void findCategoryIdsByQuestionIds_shouldNotContainQuestionWithNoRelations() {
        List<QuestionCategoryRelationPO> relations = List.of(
                buildRelation(1L, 10L)
        );
        when(relationJpaRepository.findAllByQuestionIdIn(anyList())).thenReturn(relations);

        // 查询 ID 1 和 2，但只有 1 有关联
        Map<Long, List<Long>> result = adapter.findCategoryIdsByQuestionIds(List.of(1L, 2L));

        assertEquals(1, result.size());
        assertTrue(result.containsKey(1L));
    }

    // --- findByConditions 排序白名单（REQ-86 ISSUE-2） ---
    //
    // Mock 策略：用 ArgumentCaptor<PageRequest> 捕获 adapter 内部调用
    // questionJpaRepository.findAll(spec, pageRequest) 时传入的 PageRequest，
    // 断言 captor.getValue().getSort() 的字段名与方向。

    /**
     * sort=null 走默认 createdAt DESC（行为等价：旧路径 defaultSort 也输出 createdAt DESC）
     */
    @Test
    @DisplayName("findByConditions sort=null 时使用默认 createdAt DESC")
    void findByConditions_sortNull_fallbackToCreatedAtDesc() {
        when(questionJpaRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(emptyPage());

        adapter.findByConditions(null, null, null, null, null, null, null, 0, 20);

        PageRequest captured = capturePageRequest();
        Sort sort = captured.getSort();
        assertEquals(Sort.Direction.DESC, sort.getOrderFor("createdAt").getDirection());
    }

    /**
     * sort=createdAt&order=asc 走 ASC
     */
    @Test
    @DisplayName("findByConditions sort=createdAt&order=asc 时使用 ASC")
    void findByConditions_sortCreatedAtAsc_returnsAscSort() {
        when(questionJpaRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(emptyPage());

        adapter.findByConditions(null, null, null, null, null, null,
                new SortField("createdAt", SortField.Direction.ASC), 0, 20);

        PageRequest captured = capturePageRequest();
        Sort.Order order = captured.getSort().getOrderFor("createdAt");
        assertEquals(Sort.Direction.ASC, order.getDirection());
    }

    /**
     * sort=difficulty&order=desc 走 difficulty DESC
     */
    @Test
    @DisplayName("findByConditions sort=difficulty&order=desc 时使用 difficulty DESC")
    void findByConditions_sortDifficultyDesc_returnsDifficultyDescSort() {
        when(questionJpaRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(emptyPage());

        adapter.findByConditions(null, null, null, null, null, null,
                new SortField("difficulty", SortField.Direction.DESC), 0, 20);

        PageRequest captured = capturePageRequest();
        Sort.Order order = captured.getSort().getOrderFor("difficulty");
        assertEquals(Sort.Direction.DESC, order.getDirection());
    }

    /**
     * sort=foo 非法字段抛 BusinessException(400)，不再静默回退
     */
    @Test
    @DisplayName("findByConditions sort=foo 时抛 BusinessException(400)")
    void findByConditions_sortInvalid_throws400() {
        SortField invalid = new SortField("foo", SortField.Direction.DESC);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                adapter.findByConditions(null, null, null, null, null, null, invalid, 0, 20));

        assertEquals(400, ex.getCode());
    }

    /**
     * 捕获 adapter 传给 findAll 的 PageRequest 参数
     */
    private PageRequest capturePageRequest() {
        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(questionJpaRepository).findAll(any(Specification.class), captor.capture());
        return captor.getValue();
    }

    /**
     * 构造空 Page，仅用于让 findAll 返回非 null 结果
     */
    @SuppressWarnings("unchecked")
    private Page<QuestionPO> emptyPage() {
        return (Page<QuestionPO>) org.mockito.Mockito.mock(Page.class);
    }

    // --- 辅助方法 ---

    private QuestionCategoryRelationPO buildRelation(Long questionId, Long categoryId) {
        QuestionCategoryRelationPO po = new QuestionCategoryRelationPO();
        po.setId(questionId * 100 + categoryId);
        po.setQuestionId(questionId);
        po.setCategoryId(categoryId);
        return po;
    }
}
