package com.knowledgegame.core.infrastructure.adapter.repoadapter;

import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.domainenum.KnowledgeCategoryStatus;
import com.knowledgegame.core.domain.model.entity.KnowledgeCategory;
import com.knowledgegame.core.domain.model.vo.SortField;
import com.knowledgegame.core.infrastructure.db.converter.KnowledgeCategoryConverter;
import com.knowledgegame.core.infrastructure.db.entity.KnowledgeCategoryPO;
import com.knowledgegame.core.infrastructure.db.repository.KnowledgeCategoryJpaRepository;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KnowledgeCategoryRepositoryAdapter 单元测试
 * <p>
 * 覆盖：
 * <ul>
 *   <li>REQ-94：countActiveByParentId、findAllByIdIn</li>
 *   <li>REQ-86 ISSUE-5：findByConditions 排序白名单校验（双字段默认 + 单字段覆盖 + 非法字段抛 400）</li>
 * </ul>
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

    // --- findByConditions 排序白名单（REQ-86 ISSUE-5） ---
    //
    // Mock 策略：用 ArgumentCaptor<PageRequest> 捕获 adapter 内部调用
    // jpaRepository.findAll(spec, pageRequest) 时传入的 PageRequest，
    // 断言 captor.getValue().getSort() 的字段名与方向。
    // KnowledgeCategory 默认排序为双字段（sortOrder ASC, createdAt DESC），
    // 用户主动指定字段时覆盖为单字段。

    /**
     * sort=null 走双字段默认（sortOrder ASC, createdAt DESC）
     */
    @Test
    @DisplayName("findByConditions sort=null 时使用 sortOrder ASC, createdAt DESC 双字段默认")
    void findByConditions_sortNull_fallbackToDualFieldDefault() {
        when(jpaRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(emptyPage());

        adapter.findByConditions(null, null, null, null, 0, 20);

        PageRequest captured = capturePageRequest();
        Sort sort = captured.getSort();
        assertEquals(Sort.Direction.ASC, sort.getOrderFor("sortOrder").getDirection());
        assertEquals(Sort.Direction.DESC, sort.getOrderFor("createdAt").getDirection());
    }

    /**
     * sort=name&order=asc 走单字段覆盖
     */
    @Test
    @DisplayName("findByConditions sort=name&order=asc 时使用 name ASC 单字段覆盖默认")
    void findByConditions_sortNameAsc_returnsNameAscSort() {
        when(jpaRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(emptyPage());

        adapter.findByConditions(null, null, null,
                new SortField("name", SortField.Direction.ASC), 0, 20);

        PageRequest captured = capturePageRequest();
        Sort sort = captured.getSort();
        assertEquals(Sort.Direction.ASC, sort.getOrderFor("name").getDirection());
        assertNull(sort.getOrderFor("sortOrder"));
        assertNull(sort.getOrderFor("createdAt"));
    }

    /**
     * sort=foo 非法字段抛 BusinessException(400)
     */
    @Test
    @DisplayName("findByConditions sort=foo 时抛 BusinessException(400)")
    void findByConditions_sortInvalid_throws400() {
        SortField invalid = new SortField("foo", SortField.Direction.DESC);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                adapter.findByConditions(null, null, null, invalid, 0, 20));

        assertEquals(400, ex.getCode());
    }

    /**
     * 捕获 adapter 传给 findAll 的 PageRequest 参数
     */
    private PageRequest capturePageRequest() {
        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(jpaRepository).findAll(any(Specification.class), captor.capture());
        return captor.getValue();
    }

    /**
     * 构造空 Page，仅用于让 findAll 返回非 null 结果
     */
    @SuppressWarnings("unchecked")
    private Page<KnowledgeCategoryPO> emptyPage() {
        return (Page<KnowledgeCategoryPO>) org.mockito.Mockito.mock(Page.class);
    }

    // --- 辅助方法 ---

    private KnowledgeCategoryPO buildCategoryPO(Long id, String name, KnowledgeCategoryStatus status) {
        KnowledgeCategoryPO po = new KnowledgeCategoryPO();
        po.setId(id);
        po.setParentId(null);
        po.setName(name);
        po.setDescription("测试描述");
        po.setIconFileId(1L);
        po.setIconUrl("https://example.com/icon.png");
        po.setColor("#FF0000");
        po.setCoverImageFileId(2L);
        po.setCoverImageUrl("https://example.com/cover.png");
        po.setSortOrder(1);
        po.setStatus(status);
        po.setCreatedAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        po.setUpdatedAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        return po;
    }
}
