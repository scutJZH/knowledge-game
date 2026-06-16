package com.knowledgegame.core.infrastructure.adapter.repoadapter;

import com.knowledgegame.core.domain.model.domainenum.KnowledgeCategoryStatus;
import com.knowledgegame.core.domain.model.entity.KnowledgeCategory;
import com.knowledgegame.core.infrastructure.db.converter.KnowledgeCategoryConverter;
import com.knowledgegame.core.infrastructure.db.entity.KnowledgeCategoryPO;
import com.knowledgegame.core.infrastructure.db.repository.KnowledgeCategoryJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KnowledgeCategoryRepositoryAdapter 单元测试
 * <p>
 * 覆盖 REQ-94 新增的 countActiveByParentId、findAllByIdIn 方法
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeCategoryRepositoryAdapterTest {

    @Mock
    private KnowledgeCategoryJpaRepository jpaRepository;

    @InjectMocks
    private KnowledgeCategoryRepositoryAdapter adapter;

    /**
     * countActiveByParentId 应委托给 JPA 并返回 ACTIVE 子分类数量
     */
    @Test
    @DisplayName("countActiveByParentId 应返回 JPA 查询的 ACTIVE 子分类数量")
    void countActiveByParentId_shouldReturnActiveChildCount() {
        when(jpaRepository.countActiveByParentId(1L)).thenReturn(3L);

        long count = adapter.countActiveByParentId(1L);

        assertEquals(3L, count);
        verify(jpaRepository).countActiveByParentId(1L);
    }

    /**
     * countActiveByParentId 无 ACTIVE 子分类时返回 0
     */
    @Test
    @DisplayName("countActiveByParentId 无 ACTIVE 子分类时应返回 0")
    void countActiveByParentId_shouldReturnZeroWhenNoActiveChildren() {
        when(jpaRepository.countActiveByParentId(99L)).thenReturn(0L);

        long count = adapter.countActiveByParentId(99L);

        assertEquals(0L, count);
    }

    /**
     * findAllByIdIn 正常返回分类列表
     */
    @Test
    @DisplayName("findAllByIdIn 应返回对应 ID 的分类列表")
    void findAllByIdIn_shouldReturnCategoryList() {
        KnowledgeCategoryPO po = buildCategoryPO(1L, "测试分类", KnowledgeCategoryStatus.ACTIVE);
        when(jpaRepository.findAllById(anyList())).thenReturn(List.of(po));

        List<KnowledgeCategory> result = adapter.findAllByIdIn(List.of(1L));

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals("测试分类", result.get(0).getName());
    }

    /**
     * findAllByIdIn 部分 ID 不存在时仅返回存在的
     */
    @Test
    @DisplayName("findAllByIdIn 部分 ID 不存在时应仅返回存在的")
    void findAllByIdIn_shouldReturnOnlyExisting() {
        KnowledgeCategoryPO po1 = buildCategoryPO(1L, "分类1", KnowledgeCategoryStatus.ACTIVE);
        when(jpaRepository.findAllById(anyList())).thenReturn(List.of(po1));

        List<KnowledgeCategory> result = adapter.findAllByIdIn(List.of(1L, 999L));

        assertEquals(1, result.size());
    }

    // --- 辅助方法 ---

    private KnowledgeCategoryPO buildCategoryPO(Long id, String name, KnowledgeCategoryStatus status) {
        KnowledgeCategoryPO po = new KnowledgeCategoryPO();
        po.setId(id);
        po.setParentId(null);
        po.setName(name);
        po.setDescription("测试描述");
        po.setIconUrl("https://example.com/icon.png");
        po.setColor("#FF0000");
        po.setCoverImageUrl("https://example.com/cover.png");
        po.setSortOrder(1);
        po.setStatus(status);
        po.setCreatedAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        po.setUpdatedAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        return po;
    }
}
