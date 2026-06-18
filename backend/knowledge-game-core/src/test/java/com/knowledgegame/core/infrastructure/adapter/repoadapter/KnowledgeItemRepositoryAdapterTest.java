package com.knowledgegame.core.infrastructure.adapter.repoadapter;

import com.knowledgegame.core.domain.model.domainenum.KnowledgeItemStatus;
import com.knowledgegame.core.domain.model.entity.KnowledgeItem;
import com.knowledgegame.core.domain.model.vo.FileRef;
import com.knowledgegame.core.domain.model.vo.PageResult;
import com.knowledgegame.core.domain.model.vo.SortField;
import com.knowledgegame.core.infrastructure.db.converter.KnowledgeItemConverter;
import com.knowledgegame.core.infrastructure.db.entity.KnowledgeItemCategoryRelationPO;
import com.knowledgegame.core.infrastructure.db.entity.KnowledgeItemPO;
import com.knowledgegame.core.infrastructure.db.repository.KnowledgeItemCategoryRelationJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.KnowledgeItemJpaRepository;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
     * findByConditions - 默认排序为 sortOrder ASC + createdAt DESC
     */
    @Test
    void findByConditions_shouldUseCompoundSort_whenSortFieldNull() {
        Page<KnowledgeItemPO> springPage = new PageImpl<>(
                List.of(), PageRequest.of(0, 20), 0
        );
        when(itemJpaRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(springPage);

        adapter.findByConditions(null, null, null, null, null, 0, 20);

        ArgumentCaptor<PageRequest> captor = ArgumentCaptor.forClass(PageRequest.class);
        verify(itemJpaRepository).findAll(any(Specification.class), captor.capture());
        Sort sort = captor.getValue().getSort();
        assertEquals("sortOrder", sort.get().toList().get(0).getProperty());
    }

    /**
     * findByConditions - sortOrder 字段使用复合排序
     */
    @Test
    void findByConditions_shouldUseCompoundSort_whenSortFieldIsSortOrder() {
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
     * findByConditions - 非 sortOrder 字段使用 FIELD_MAPPING
     */
    @Test
    void findByConditions_shouldUseMapping_whenOtherField() {
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
        assertEquals("createdAt", sort.get().toList().get(0).getProperty());
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
