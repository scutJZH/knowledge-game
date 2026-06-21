package com.knowledgegame.admin.application.service;

import com.knowledgegame.admin.api.dto.request.BatchSortItem;
import com.knowledgegame.admin.api.dto.request.UpdateKnowledgeCategoryRequest;
import com.knowledgegame.admin.api.dto.response.KnowledgeCategoryResponse;
import com.knowledgegame.admin.api.dto.response.KnowledgeCategoryTreeResponse;
import com.knowledgegame.components.feign.client.FileServiceClient;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.domainenum.KnowledgeCategoryStatus;
import com.knowledgegame.core.domain.model.entity.KnowledgeCategory;
import com.knowledgegame.core.domain.model.vo.FileRef;
import com.knowledgegame.core.domain.model.vo.PageResult;
import com.knowledgegame.core.domain.model.vo.SortField;
import com.knowledgegame.core.domain.port.outbound.KnowledgeCategoryRepositoryPort;
import com.knowledgegame.core.domain.service.KnowledgeCategoryDomainService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openapitools.jackson.nullable.JsonNullable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KnowledgeCategoryAppService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeCategoryAppServiceTest {

    @Mock
    private KnowledgeCategoryRepositoryPort categoryRepositoryPort;

    @Mock
    private KnowledgeCategoryDomainService categoryDomainService;

    @Mock
    private FileServiceClient fileServiceClient;

    @InjectMocks
    private KnowledgeCategoryAppService appService;

    private LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);

    /**
     * 创建 - 正常创建
     */
    @Test
    void create_shouldSucceed() {
        KnowledgeCategory newCategory = KnowledgeCategory.create(
                "编程", null, null, null, null, null, 0);
        when(categoryDomainService.validateAndCreate(
                "编程", null, null, null, null, null, 0)).thenReturn(newCategory);
        KnowledgeCategory saved = KnowledgeCategory.reconstruct(
                1L, null, "编程", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        when(categoryRepositoryPort.save(any())).thenReturn(saved);

        KnowledgeCategoryResponse result = appService.create(
                "编程", null, null, null, null, null, 0);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("编程", result.getName());
        assertEquals("ACTIVE", result.getStatus());
    }

    /**
     * 查询 - 正常返回
     */
    @Test
    void getById_shouldReturn_whenExists() {
        KnowledgeCategory category = KnowledgeCategory.reconstruct(
                1L, null, "编程", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        when(categoryRepositoryPort.findById(1L)).thenReturn(Optional.of(category));

        KnowledgeCategoryResponse result = appService.getById(1L);

        assertEquals(1L, result.getId());
        assertEquals("编程", result.getName());
    }

    /**
     * 查询 - 不存在抛异常
     */
    @Test
    void getById_shouldThrow_whenNotFound() {
        when(categoryRepositoryPort.findById(999L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> appService.getById(999L));
    }

    /**
     * 分页查询 - 正常返回
     */
    @Test
    void list_shouldReturnPagedResult() {
        KnowledgeCategory cat = KnowledgeCategory.reconstruct(
                1L, null, "编程", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        PageResult<KnowledgeCategory> mockPage = PageResult.<KnowledgeCategory>builder()
                .content(List.of(cat))
                .totalElements(1)
                .pageNumber(0)
                .pageSize(20)
                .totalPages(1)
                .build();
        when(categoryRepositoryPort.findByConditions(eq(null), eq(null), eq(null), eq(null), eq(0), eq(20)))
                .thenReturn(mockPage);

        PageResult<KnowledgeCategoryResponse> result = appService.list(null, null, null, null, null, 0, 20);

        assertEquals(1, result.getContent().size());
        assertEquals(1L, result.getTotalElements());
    }

    /**
     * 分页查询 - sort/order 透传到 Port 的 SortField 参数
     */
    @Test
    void list_shouldPassSortFieldToPort() {
        KnowledgeCategory cat = KnowledgeCategory.reconstruct(
                1L, null, "编程", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        PageResult<KnowledgeCategory> mockPage = PageResult.<KnowledgeCategory>builder()
                .content(List.of(cat))
                .totalElements(1)
                .pageNumber(0)
                .pageSize(20)
                .totalPages(1)
                .build();
        SortField expected = new SortField("name", SortField.Direction.ASC);
        when(categoryRepositoryPort.findByConditions(eq(null), eq(null), eq(null),
                argThat(sf -> sf != null && sf.getField().equals("name") && sf.getDirection() == SortField.Direction.ASC),
                eq(0), eq(20)))
                .thenReturn(mockPage);

        appService.list(null, null, null, "name", "asc", 0, 20);

        verify(categoryRepositoryPort).findByConditions(eq(null), eq(null), eq(null),
                argThat(sf -> sf != null && sf.getField().equals("name") && sf.getDirection() == SortField.Direction.ASC),
                eq(0), eq(20));
    }

    /**
     * 分类树 - 正常构建
     */
    @Test
    void tree_shouldBuildTree() {
        KnowledgeCategory parent = KnowledgeCategory.reconstruct(
                1L, null, "编程", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        KnowledgeCategory child = KnowledgeCategory.reconstruct(
                2L, 1L, "Java", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        when(categoryRepositoryPort.findAll()).thenReturn(List.of(parent, child));

        List<KnowledgeCategoryTreeResponse> tree = appService.tree();

        assertEquals(1, tree.size());
        assertEquals("编程", tree.get(0).getName());
        assertEquals(1, tree.get(0).getChildren().size());
        assertEquals("Java", tree.get(0).getChildren().get(0).getName());
    }

    /**
     * 分类树 - 空列表返回空树
     */
    @Test
    void tree_shouldReturnEmpty_whenNoCategories() {
        when(categoryRepositoryPort.findAll()).thenReturn(Collections.emptyList());

        List<KnowledgeCategoryTreeResponse> tree = appService.tree();

        assertNotNull(tree);
        assertTrue(tree.isEmpty());
    }

    // ============ update 三态分派 ============

    /**
     * 更新 - 名称不变时正常更新（新签名）
     */
    @Test
    void update_shouldSucceed_whenNameNotChanged() {
        KnowledgeCategory existing = KnowledgeCategory.reconstruct(
                1L, null, "编程", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        when(categoryRepositoryPort.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateKnowledgeCategoryRequest req = new UpdateKnowledgeCategoryRequest();
        req.setName("编程");

        KnowledgeCategoryResponse result = appService.update(1L, req);

        assertNotNull(result);
        verify(categoryRepositoryPort).save(any());
    }

    /**
     * 更新 - 同名已存在抛异常
     */
    @Test
    void update_shouldThrow_whenNameDuplicate() {
        KnowledgeCategory existing = KnowledgeCategory.reconstruct(
                1L, null, "编程", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        when(categoryRepositoryPort.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepositoryPort.existsByNameAndParentId("重复名", null)).thenReturn(true);

        UpdateKnowledgeCategoryRequest req = new UpdateKnowledgeCategoryRequest();
        req.setName("重复名");

        assertThrows(BusinessException.class, () -> appService.update(1L, req));
    }

    /**
     * 三态场景 1：所有可清空字段 undefined → 不调任何 clear/update 方法
     */
    @Test
    void update_shouldSkipAllClearableFields_whenAllUndefined() {
        KnowledgeCategory existing = KnowledgeCategory.reconstruct(
                1L, null, "编程", "原描述", FileRef.of(1L, "u1"), "#FF5500",
                FileRef.of(2L, "u2"), 0, KnowledgeCategoryStatus.ACTIVE, now, now);
        when(categoryRepositoryPort.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateKnowledgeCategoryRequest req = new UpdateKnowledgeCategoryRequest();
        // 所有 JsonNullable 字段未设置，保持 undefined()

        KnowledgeCategoryResponse result = appService.update(1L, req);

        assertNotNull(result);
        // 字段保持原值
        assertEquals("原描述", existing.getDescription());
        assertEquals(FileRef.of(1L, "u1"), existing.getIcon());
        assertEquals("#FF5500", existing.getColor());
        assertEquals(FileRef.of(2L, "u2"), existing.getCoverImage());
    }

    /**
     * 三态场景 2：JsonNullable.of(null) → 调用 clearXxx()
     */
    @Test
    void update_shouldCallClear_whenFieldsAreNull() {
        KnowledgeCategory existing = KnowledgeCategory.reconstruct(
                1L, null, "编程", "原描述", FileRef.of(1L, "u1"), "#FF5500",
                FileRef.of(2L, "u2"), 0, KnowledgeCategoryStatus.ACTIVE, now, now);
        when(categoryRepositoryPort.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateKnowledgeCategoryRequest req = new UpdateKnowledgeCategoryRequest();
        req.setDescription(JsonNullable.of(null));
        req.setIconFileId(JsonNullable.of(null));
        req.setColor(JsonNullable.of(null));
        req.setCoverImageFileId(JsonNullable.of(null));

        appService.update(1L, req);

        assertNull(existing.getDescription());
        assertNull(existing.getIcon());
        assertNull(existing.getColor());
        assertNull(existing.getCoverImage());
    }

    /**
     * 三态场景 3：JsonNullable.of(value) String 字段 → 调用 updateXxx(value)
     */
    @Test
    void update_shouldCallUpdate_whenStringFieldsHaveValue() {
        KnowledgeCategory existing = KnowledgeCategory.reconstruct(
                1L, null, "编程", "原描述", null, "#FF5500",
                null, 0, KnowledgeCategoryStatus.ACTIVE, now, now);
        when(categoryRepositoryPort.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateKnowledgeCategoryRequest req = new UpdateKnowledgeCategoryRequest();
        req.setDescription(JsonNullable.of("新描述"));
        req.setColor(JsonNullable.of("#00AA00"));

        appService.update(1L, req);

        assertEquals("新描述", existing.getDescription());
        assertEquals("#00AA00", existing.getColor());
    }

    /**
     * 三态场景 4：JsonNullable.of(value) FileRef 字段 → verifyFileRef + 调用 updateXxx(FileRef)
     */
    @Test
    void update_shouldVerifyAndUpdate_whenFileRefHasValue() {
        KnowledgeCategory existing = KnowledgeCategory.reconstruct(
                1L, null, "编程", null, null, null, null,
                0, KnowledgeCategoryStatus.ACTIVE, now, now);
        when(categoryRepositoryPort.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // mock verifyFileRef 链路：fileServiceClient.getFileInfo 返回合法 metadata
        com.knowledgegame.components.feign.dto.FileInfoResponse fileInfo =
                com.knowledgegame.components.feign.dto.FileInfoResponse.builder()
                        .fileId(100L)
                        .url("/static/new.png")
                        .metadata(java.util.Map.of("bizType", "CATEGORY_ICON", "userId", 1L))
                        .build();
        when(fileServiceClient.getFileInfo(100L))
                .thenReturn(com.knowledgegame.core.common.result.Result.success(fileInfo));
        // mock SecurityUtils.getCurrentUserId 返回 1（与 metadata.userId 一致）
        try (org.mockito.MockedStatic<com.knowledgegame.auth.security.SecurityUtils> mocked =
                     org.mockito.Mockito.mockStatic(com.knowledgegame.auth.security.SecurityUtils.class)) {
            mocked.when(com.knowledgegame.auth.security.SecurityUtils::getCurrentUserId).thenReturn(1L);

            UpdateKnowledgeCategoryRequest req = new UpdateKnowledgeCategoryRequest();
            req.setIconFileId(JsonNullable.of(100L));

            appService.update(1L, req);

            assertEquals(FileRef.of(100L, "/static/new.png"), existing.getIcon());
        }
    }

    /**
     * 三态场景 5：verifyFileRef 失败（bizType 不匹配）→ 抛 BusinessException，不调用领域方法
     */
    @Test
    void update_shouldThrow_whenFileBizTypeMismatch() {
        KnowledgeCategory existing = KnowledgeCategory.reconstruct(
                1L, null, "编程", null, FileRef.of(1L, "old"), null, null,
                0, KnowledgeCategoryStatus.ACTIVE, now, now);
        when(categoryRepositoryPort.findById(1L)).thenReturn(Optional.of(existing));
        com.knowledgegame.components.feign.dto.FileInfoResponse wrongBizTypeInfo =
                com.knowledgegame.components.feign.dto.FileInfoResponse.builder()
                        .fileId(100L)
                        .url("/static/x.png")
                        .metadata(java.util.Map.of("bizType", "CARD_TEMPLATE", "userId", 1L))
                        .build();
        when(fileServiceClient.getFileInfo(100L))
                .thenReturn(com.knowledgegame.core.common.result.Result.success(wrongBizTypeInfo));

        UpdateKnowledgeCategoryRequest req = new UpdateKnowledgeCategoryRequest();
        req.setIconFileId(JsonNullable.of(100L));

        BusinessException ex = assertThrows(BusinessException.class, () -> appService.update(1L, req));
        assertTrue(ex.getMessage().contains("文件类型不匹配"));
        // 原 icon 保持不变
        assertEquals(FileRef.of(1L, "old"), existing.getIcon());
    }

    // ============ update status 变更分支 ============

    /**
     * 更新 - status → INACTIVE 时应调 validateDelete 再 updateStatus
     */
    @Test
    void update_shouldCallValidateDelete_whenStatusToInactive() {
        KnowledgeCategory existing = KnowledgeCategory.reconstruct(
                1L, null, "编程", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        when(categoryRepositoryPort.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Mockito.doNothing().when(categoryDomainService).validateDelete(1L);

        UpdateKnowledgeCategoryRequest req = new UpdateKnowledgeCategoryRequest();
        req.setStatus(KnowledgeCategoryStatus.INACTIVE);

        KnowledgeCategoryResponse result = appService.update(1L, req);

        assertNotNull(result);
        verify(categoryDomainService).validateDelete(1L);
        assertEquals(KnowledgeCategoryStatus.INACTIVE, existing.getStatus());
    }

    /**
     * 更新 - status → ACTIVE 时应调 validateActivate 再 updateStatus
     */
    @Test
    void update_shouldCallValidateActivate_whenStatusToActive() {
        KnowledgeCategory existing = KnowledgeCategory.reconstruct(
                1L, null, "编程", null, null, null, null, 0,
                KnowledgeCategoryStatus.INACTIVE, now, now);
        when(categoryRepositoryPort.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Mockito.doNothing().when(categoryDomainService).validateActivate(any(KnowledgeCategory.class));

        UpdateKnowledgeCategoryRequest req = new UpdateKnowledgeCategoryRequest();
        req.setStatus(KnowledgeCategoryStatus.ACTIVE);

        KnowledgeCategoryResponse result = appService.update(1L, req);

        assertNotNull(result);
        verify(categoryDomainService).validateActivate(existing);
        assertEquals(KnowledgeCategoryStatus.ACTIVE, existing.getStatus());
    }

    /**
     * 更新 - status 不变（同为 ACTIVE）时不触发校验
     */
    @Test
    void update_shouldSkipValidation_whenStatusUnchanged() {
        KnowledgeCategory existing = KnowledgeCategory.reconstruct(
                1L, null, "编程", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        when(categoryRepositoryPort.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateKnowledgeCategoryRequest req = new UpdateKnowledgeCategoryRequest();
        req.setStatus(KnowledgeCategoryStatus.ACTIVE); // 同枚举值

        appService.update(1L, req);

        verify(categoryDomainService, Mockito.never()).validateDelete(Mockito.anyLong());
        verify(categoryDomainService, Mockito.never()).validateActivate(any(KnowledgeCategory.class));
    }

    // ============ 其他不变 ============

    /**
     * 移动 - 正常移动并自动设置 sortOrder
     */
    @Test
    void move_shouldSucceed() {
        KnowledgeCategory existing = KnowledgeCategory.reconstruct(
                2L, 1L, "Java", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        when(categoryRepositoryPort.findById(2L)).thenReturn(Optional.of(existing));
        when(categoryRepositoryPort.findMaxSortOrderByParentId(5L)).thenReturn(3);
        when(categoryRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        KnowledgeCategoryResponse result = appService.move(2L, 5L);

        assertNotNull(result);
        verify(categoryRepositoryPort).save(argThat(cat ->
                cat.getParentId().equals(5L) && cat.getSortOrder() == 4));
    }

    /**
     * 移动 - 移到顶级（newParentId=null）并自动设置 sortOrder
     */
    @Test
    void move_shouldSetParentIdToNull_whenMovingToRoot() {
        KnowledgeCategory existing = KnowledgeCategory.reconstruct(
                2L, 1L, "Java", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        when(categoryRepositoryPort.findById(2L)).thenReturn(Optional.of(existing));
        when(categoryRepositoryPort.findMaxSortOrderForRoot()).thenReturn(null);
        when(categoryRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        appService.move(2L, null);

        verify(categoryRepositoryPort).save(argThat(cat ->
                cat.getParentId() == null && cat.getSortOrder() == 0));
    }

    /**
     * 软删除 - status 变为 INACTIVE
     */
    @Test
    void delete_shouldDeactivate() {
        KnowledgeCategory existing = KnowledgeCategory.reconstruct(
                1L, null, "编程", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        when(categoryRepositoryPort.findById(1L)).thenReturn(Optional.of(existing));
        Mockito.doNothing().when(categoryDomainService).validateDelete(1L);
        when(categoryRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        appService.delete(1L);

        verify(categoryDomainService).validateDelete(1L);
        verify(categoryRepositoryPort).save(argThat(cat ->
                cat.getStatus() == KnowledgeCategoryStatus.INACTIVE
        ));
    }

    /**
     * 软删除 - 不存在抛异常
     */
    @Test
    void delete_shouldThrow_whenNotFound() {
        when(categoryRepositoryPort.findById(999L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> appService.delete(999L));
    }

    /**
     * 软删除 - 有子分类时抛异常
     */
    @Test
    void delete_shouldThrow_whenHasChildren() {
        KnowledgeCategory existing = KnowledgeCategory.reconstruct(
                1L, null, "编程", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        when(categoryRepositoryPort.findById(1L)).thenReturn(Optional.of(existing));
        Mockito.doThrow(new BusinessException("该分类下存在 2 个子分类（含已停用），无法删除"))
                .when(categoryDomainService).validateDelete(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> appService.delete(1L));
        assertEquals("该分类下存在 2 个子分类（含已停用），无法删除", ex.getMessage());
    }

    /**
     * 批量排序 - 正常排序
     */
    @Test
    void batchSort_shouldSucceed() {
        KnowledgeCategory cat1 = KnowledgeCategory.reconstruct(
                1L, null, "编程", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        KnowledgeCategory cat2 = KnowledgeCategory.reconstruct(
                2L, null, "数学", null, null, null, null, 1,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        when(categoryRepositoryPort.findById(1L)).thenReturn(Optional.of(cat1));
        when(categoryRepositoryPort.findById(2L)).thenReturn(Optional.of(cat2));
        when(categoryRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<BatchSortItem> items = new ArrayList<>();
        BatchSortItem item1 = new BatchSortItem();
        item1.setId(1L);
        item1.setSortOrder(1);
        BatchSortItem item2 = new BatchSortItem();
        item2.setId(2L);
        item2.setSortOrder(0);
        items.add(item1);
        items.add(item2);

        appService.batchSort(items);

        verify(categoryRepositoryPort).save(argThat(cat -> cat.getId().equals(1L) && cat.getSortOrder() == 1));
        verify(categoryRepositoryPort).save(argThat(cat -> cat.getId().equals(2L) && cat.getSortOrder() == 0));
    }

    /**
     * 批量排序 - ID 不存在抛异常
     */
    @Test
    void batchSort_shouldThrow_whenIdNotFound() {
        when(categoryRepositoryPort.findById(999L)).thenReturn(Optional.empty());

        List<BatchSortItem> items = new ArrayList<>();
        BatchSortItem item = new BatchSortItem();
        item.setId(999L);
        item.setSortOrder(0);
        items.add(item);

        BusinessException ex = assertThrows(BusinessException.class, () -> appService.batchSort(items));
        assertEquals("知识点分类不存在: 999", ex.getMessage());
    }

    /**
     * 批量排序 - 非同父级抛异常
     */
    @Test
    void batchSort_shouldThrow_whenDifferentParent() {
        KnowledgeCategory cat1 = KnowledgeCategory.reconstruct(
                1L, null, "编程", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        KnowledgeCategory cat2 = KnowledgeCategory.reconstruct(
                2L, 10L, "数学", null, null, null, null, 0,
                KnowledgeCategoryStatus.ACTIVE, now, now);
        when(categoryRepositoryPort.findById(1L)).thenReturn(Optional.of(cat1));
        when(categoryRepositoryPort.findById(2L)).thenReturn(Optional.of(cat2));

        List<BatchSortItem> items = new ArrayList<>();
        BatchSortItem item1 = new BatchSortItem();
        item1.setId(1L);
        item1.setSortOrder(0);
        BatchSortItem item2 = new BatchSortItem();
        item2.setId(2L);
        item2.setSortOrder(1);
        items.add(item1);
        items.add(item2);

        BusinessException ex = assertThrows(BusinessException.class, () -> appService.batchSort(items));
        assertEquals("所有分类必须属于同一父级", ex.getMessage());
    }
}
