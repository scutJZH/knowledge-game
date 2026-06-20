package com.knowledgegame.core.infrastructure.adapter.repoadapter;

import com.knowledgegame.core.domain.model.domainenum.KnowledgeItemStatus;
import com.knowledgegame.core.domain.model.entity.KnowledgeItem;
import com.knowledgegame.core.domain.model.vo.FileRef;
import com.knowledgegame.core.domain.model.vo.PageResult;
import com.knowledgegame.core.domain.model.vo.SortField;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.infrastructure.db.converter.KnowledgeItemConverter;
import com.knowledgegame.core.infrastructure.db.entity.KnowledgeItemCategoryRelationPO;
import com.knowledgegame.core.infrastructure.db.entity.KnowledgeItemPO;
import com.knowledgegame.core.infrastructure.db.repository.KnowledgeItemCategoryRelationJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.KnowledgeItemJpaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KnowledgeItemRepositoryAdapter 单元测试
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeItemRepositoryAdapterTest {

    @Mock
    private KnowledgeItemJpaRepository itemJpaRepository;

    @Mock
    private KnowledgeItemCategoryRelationJpaRepository relationJpaRepository;

    @Mock
    private EntityManager em;

    @InjectMocks
    private KnowledgeItemRepositoryAdapter adapter;

    @Captor
    private ArgumentCaptor<List<KnowledgeItemCategoryRelationPO>> relationsCaptor;

    /**
     * save - 新建（id 为 null）
     */
    @Test
    void save_shouldInsert_whenIdNull() {
        KnowledgeItem item = KnowledgeItem.create(
                "标题", "内容",
                FileRef.of(1L, "https://example.com/cover.png"),
                List.of("Java"), 0
        );
        KnowledgeItemPO savedPO = buildPO(1L);
        when(itemJpaRepository.save(any())).thenReturn(savedPO);

        KnowledgeItem result = adapter.save(item);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        verify(itemJpaRepository).save(any());
    }

    /**
     * save - 更新（id 非 null）
     */
    @Test
    void save_shouldUpdate_whenIdNotNull() {
        KnowledgeItem item = KnowledgeItem.reconstruct(
                1L, "新标题", "新内容", null,
                null, List.of("Java"), 5,
                KnowledgeItemStatus.ACTIVE,
                LocalDateTime.now(), LocalDateTime.now()
        );
        KnowledgeItemPO existingPO = buildPO(1L);
        when(itemJpaRepository.findById(1L)).thenReturn(Optional.of(existingPO));
        when(itemJpaRepository.save(any())).thenReturn(existingPO);

        KnowledgeItem result = adapter.save(item);

        assertNotNull(result);
        verify(itemJpaRepository).findById(1L);
        verify(itemJpaRepository).save(any());
    }

    /**
     * findById - 存在
     */
    @Test
    void findById_shouldReturn_whenExists() {
        KnowledgeItemPO po = buildPO(1L);
        when(itemJpaRepository.findById(1L)).thenReturn(Optional.of(po));

        Optional<KnowledgeItem> result = adapter.findById(1L);

        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getId());
    }

    /**
     * findById - 不存在
     */
    @Test
    void findById_shouldReturnEmpty_whenNotExists() {
        when(itemJpaRepository.findById(999L)).thenReturn(Optional.empty());

        Optional<KnowledgeItem> result = adapter.findById(999L);

        assertTrue(result.isEmpty());
    }

    /**
     * findByConditions - 基本分页
     */
    @Test
    void findByConditions_shouldReturnPage() {
        Page<KnowledgeItemPO> springPage = new PageImpl<>(
                List.of(buildPO(1L)),
                PageRequest.of(0, 20),
                1
        );
        when(itemJpaRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(springPage);

        PageResult<KnowledgeItem> result = adapter.findByConditions(
                null, null, null, null, null, 0, 20
        );

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());
    }

    /**
     * sort=null → 默认复合排序：sortOrder ASC + createdAt DESC
     */
    @Test
    void findByConditions_shouldUseDefaultSort_whenSortFieldNull() {
        Page<KnowledgeItemPO> springPage = new PageImpl<>(
                List.of(), PageRequest.of(0, 20), 0
        );
        when(itemJpaRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(springPage);

        adapter.findByConditions(null, null, null, null, null, 0, 20);

        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(itemJpaRepository).findAll(any(Specification.class), captor.capture());
        Sort sort = captor.getValue().getSort();
        List<Sort.Order> orders = sort.get().toList();
        assertEquals(2, orders.size());
        assertEquals(Sort.Direction.ASC, orders.get(0).getDirection());
        assertEquals("sortOrder", orders.get(0).getProperty());
        assertEquals(Sort.Direction.DESC, orders.get(1).getDirection());
        assertEquals("createdAt", orders.get(1).getProperty());
    }

    /**
     * sort="sortOrder" → 复合排序 sortOrder ASC + createdAt DESC（忽略 order 参数）
     */
    @Test
    void findByConditions_shouldUseCompoundSort_whenSortOrder() {
        Page<KnowledgeItemPO> springPage = new PageImpl<>(
                List.of(), PageRequest.of(0, 20), 0
        );
        when(itemJpaRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(springPage);

        adapter.findByConditions(
                null, null, null, null,
                new SortField("sortOrder", SortField.Direction.ASC),
                0, 20
        );

        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(itemJpaRepository).findAll(any(Specification.class), captor.capture());
        Sort sort = captor.getValue().getSort();
        assertEquals(2, sort.get().toList().size());
    }

    /**
     * sort="createdAt" order="desc" → Sort 含 createdAt DESC
     */
    @Test
    void findByConditions_shouldUseSortFields_whenOtherField() {
        Page<KnowledgeItemPO> springPage = new PageImpl<>(
                List.of(), PageRequest.of(0, 20), 0
        );
        when(itemJpaRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(springPage);

        adapter.findByConditions(
                null, null, null, null,
                new SortField("createdAt", SortField.Direction.DESC),
                0, 20
        );

        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(itemJpaRepository).findAll(any(Specification.class), captor.capture());
        Sort sort = captor.getValue().getSort();
        assertEquals(Sort.Direction.DESC, sort.get().toList().get(0).getDirection());
        assertEquals("createdAt", sort.get().toList().get(0).getProperty());
    }

    /**
     * sort="id" order="asc" → Sort 含 id ASC
     */
    @Test
    void findByConditions_shouldSortByIdAsc() {
        Page<KnowledgeItemPO> springPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(itemJpaRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(springPage);

        adapter.findByConditions(null, null, null, null,
                new SortField("id", SortField.Direction.ASC), 0, 20);

        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(itemJpaRepository).findAll(any(Specification.class), captor.capture());
        Sort sort = captor.getValue().getSort();
        assertEquals(Sort.Direction.ASC, sort.get().toList().get(0).getDirection());
        assertEquals("id", sort.get().toList().get(0).getProperty());
    }

    /**
     * sort="title" order="desc" → Sort 含 title DESC
     */
    @Test
    void findByConditions_shouldSortByTitleDesc() {
        Page<KnowledgeItemPO> springPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(itemJpaRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(springPage);

        adapter.findByConditions(null, null, null, null,
                new SortField("title", SortField.Direction.DESC), 0, 20);

        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(itemJpaRepository).findAll(any(Specification.class), captor.capture());
        Sort sort = captor.getValue().getSort();
        assertEquals(Sort.Direction.DESC, sort.get().toList().get(0).getDirection());
        assertEquals("title", sort.get().toList().get(0).getProperty());
    }

    /**
     * sort="status"（order 缺失即 DESC by default）→ Sort 含 status DESC
     */
    @Test
    void findByConditions_shouldSortByStatusDefaultDesc() {
        Page<KnowledgeItemPO> springPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(itemJpaRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(springPage);

        adapter.findByConditions(null, null, null, null,
                new SortField("status", SortField.Direction.DESC), 0, 20);

        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(itemJpaRepository).findAll(any(Specification.class), captor.capture());
        Sort sort = captor.getValue().getSort();
        assertEquals("status", sort.get().toList().get(0).getProperty());
    }

    /**
     * sort="createdAt" order="asc" → Sort 含 createdAt ASC
     */
    @Test
    void findByConditions_shouldSortByCreatedAtAsc() {
        Page<KnowledgeItemPO> springPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(itemJpaRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(springPage);

        adapter.findByConditions(null, null, null, null,
                new SortField("createdAt", SortField.Direction.ASC), 0, 20);

        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(itemJpaRepository).findAll(any(Specification.class), captor.capture());
        Sort sort = captor.getValue().getSort();
        assertEquals("createdAt", sort.get().toList().get(0).getProperty());
    }

    /**
     * sort="updatedAt" order="desc" → Sort 含 updatedAt DESC
     */
    @Test
    void findByConditions_shouldSortByUpdatedAtDesc() {
        Page<KnowledgeItemPO> springPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(itemJpaRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(springPage);

        adapter.findByConditions(null, null, null, null,
                new SortField("updatedAt", SortField.Direction.DESC), 0, 20);

        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(itemJpaRepository).findAll(any(Specification.class), captor.capture());
        Sort sort = captor.getValue().getSort();
        assertEquals("updatedAt", sort.get().toList().get(0).getProperty());
    }

    /**
     * sort="categoryName" → 走 EntityManager 路径，不调 itemJpaRepository.findAll(Spec, Pageable)
     * 由于完整 mock EntityManager + CriteriaBuilder 链过于臃肿，此用例仅验证入口路由正确：
     * categoryName 时不会调用 itemJpaRepository.findAll(Spec, Pageable)。完整行为由集成测试覆盖。
     */
    @Test
    void findByConditions_shouldRouteToEntityManager_whenCategoryName() {
        // 通过 mock em.getCriteriaBuilder() 触发 NullPointerException 来验证走了 EntityManager 路径
        // 若走了 toSpringSort → itemJpaRepository.findAll，则 mock 不会触发 NPE，verify 会失败
        try {
            adapter.findByConditions(null, null, null, null,
                    new SortField("categoryName", SortField.Direction.ASC), 0, 20);
        } catch (NullPointerException expected) {
            // EntityManager mock 返回 null → CriteriaBuilder 调用时抛 NPE，证明走了 EntityManager 路径
        }
        verify(itemJpaRepository, never()).findAll(any(Specification.class), any(PageRequest.class));
    }

    /**
     * sort="invalidField" → BusinessException(400)，消息含允许字段中文名列表
     */
    @Test
    void findByConditions_shouldThrowBusinessException_whenInvalidField() {
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> adapter.findByConditions(null, null, null, null,
                        new SortField("invalidField", SortField.Direction.ASC), 0, 20)
        );
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("不支持的排序字段"));
        assertTrue(ex.getMessage().contains("invalidField"));
        assertTrue(ex.getMessage().contains("ID") || ex.getMessage().contains("标题"));
    }

    /**
     * findByIds - 批量查询
     */
    @Test
    void findByIds_shouldReturnList() {
        when(itemJpaRepository.findAllById(List.of(1L, 2L)))
                .thenReturn(List.of(buildPO(1L), buildPO(2L)));

        List<KnowledgeItem> result = adapter.findByIds(List.of(1L, 2L));

        assertEquals(2, result.size());
    }

    /**
     * saveCategoryRelations - 空列表
     */
    @Test
    void saveCategoryRelations_shouldDeleteOnly_whenEmptyList() {
        adapter.saveCategoryRelations(1L, List.of());

        verify(relationJpaRepository).deleteByItemId(1L);
    }

    /**
     * saveCategoryRelations - 有分类
     */
    @Test
    void saveCategoryRelations_shouldDeleteAndInsert() {
        adapter.saveCategoryRelations(1L, List.of(10L, 20L));

        verify(relationJpaRepository).deleteByItemId(1L);
        verify(relationJpaRepository).saveAll(anyList());
    }

    /**
     * findActiveCategoryIdsByItemId
     */
    @Test
    void findActiveCategoryIdsByItemId_shouldReturnIds() {
        when(itemJpaRepository.findActiveCategoryIdsByItemId(1L))
                .thenReturn(List.of(10L, 20L));

        List<Long> result = adapter.findActiveCategoryIdsByItemId(1L);

        assertEquals(List.of(10L, 20L), result);
    }

    /**
     * countActiveByCategoryId
     */
    @Test
    void countActiveByCategoryId_shouldReturnCount() {
        when(relationJpaRepository.countActiveItemsByCategoryId(10L)).thenReturn(5L);

        long result = adapter.countActiveByCategoryId(10L);

        assertEquals(5L, result);
    }

    /**
     * findCategoryIdsByItemIds - 分组
     */
    @Test
    void findCategoryIdsByItemIds_shouldReturnMap() {
        List<KnowledgeItemCategoryRelationPO> relations = List.of(
                KnowledgeItemCategoryRelationPO.builder().id(1L).itemId(1L).categoryId(10L).build(),
                KnowledgeItemCategoryRelationPO.builder().id(2L).itemId(1L).categoryId(20L).build(),
                KnowledgeItemCategoryRelationPO.builder().id(3L).itemId(2L).categoryId(30L).build()
        );
        when(relationJpaRepository.findAllByItemIdIn(List.of(1L, 2L)))
                .thenReturn(relations);

        Map<Long, List<Long>> result = adapter.findCategoryIdsByItemIds(List.of(1L, 2L));

        assertEquals(2, result.size());
        assertEquals(List.of(10L, 20L), result.get(1L));
        assertEquals(List.of(30L), result.get(2L));
    }

    /**
     * batchUpdateStatus
     */
    @Test
    void batchUpdateStatus_shouldDelegate() {
        adapter.batchUpdateStatus(List.of(1L, 2L), KnowledgeItemStatus.INACTIVE);

        verify(itemJpaRepository).batchUpdateStatus(List.of(1L, 2L), KnowledgeItemStatus.INACTIVE);
    }

    private KnowledgeItemPO buildPO(Long id) {
        return KnowledgeItemPO.builder()
                .id(id)
                .title("标题" + id)
                .content("内容")
                .sortOrder(0)
                .status(KnowledgeItemStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
