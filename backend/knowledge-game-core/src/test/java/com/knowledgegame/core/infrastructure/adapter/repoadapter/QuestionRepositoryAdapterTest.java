package com.knowledgegame.core.infrastructure.adapter.repoadapter;

import com.knowledgegame.core.domain.model.entity.Question;
import com.knowledgegame.core.infrastructure.db.converter.QuestionConverter;
import com.knowledgegame.core.infrastructure.db.entity.QuestionCategoryRelationPO;
import com.knowledgegame.core.infrastructure.db.repository.QuestionCategoryRelationJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.QuestionJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * QuestionRepositoryAdapter 单元测试
 * <p>
 * 覆盖 REQ-94 新增的两个方法：countActiveByCategoryId、findCategoryIdsByQuestionIds
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

    // --- 辅助方法 ---

    private QuestionCategoryRelationPO buildRelation(Long questionId, Long categoryId) {
        QuestionCategoryRelationPO po = new QuestionCategoryRelationPO();
        po.setId(questionId * 100 + categoryId);
        po.setQuestionId(questionId);
        po.setCategoryId(categoryId);
        return po;
    }
}
