package com.knowledgegame.admin.application.service;

import com.knowledgegame.admin.api.dto.request.BatchSortItem;
import com.knowledgegame.admin.api.dto.request.BatchSortRequest;
import com.knowledgegame.admin.api.dto.request.CreateKnowledgeItemRequest;
import com.knowledgegame.admin.api.dto.request.UpdateKnowledgeItemRequest;
import com.knowledgegame.admin.api.dto.response.ImportFailDetail;
import com.knowledgegame.admin.api.dto.response.KnowledgeItemImportResult;
import com.knowledgegame.admin.api.dto.response.KnowledgeItemListResponse;
import com.knowledgegame.admin.api.dto.response.KnowledgeItemResponse;
import com.knowledgegame.components.feign.client.FileServiceClient;
import com.knowledgegame.components.feign.dto.FileInfoResponse;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.common.result.Result;
import com.knowledgegame.core.domain.model.domainenum.KnowledgeCategoryStatus;
import com.knowledgegame.core.domain.model.domainenum.KnowledgeItemStatus;
import com.knowledgegame.core.domain.model.entity.KnowledgeCategory;
import com.knowledgegame.core.domain.model.entity.KnowledgeItem;
import com.knowledgegame.core.domain.model.vo.FileRef;
import com.knowledgegame.core.domain.model.vo.KnowledgeItemSummary;
import com.knowledgegame.core.domain.model.vo.PageResult;
import com.knowledgegame.core.domain.model.vo.SortField;
import com.knowledgegame.auth.security.SecurityUtils;
import com.knowledgegame.core.domain.port.outbound.KnowledgeCategoryRepositoryPort;
import com.knowledgegame.core.domain.port.outbound.KnowledgeItemRepository;
import com.knowledgegame.core.domain.service.KnowledgeItemDomainService;
import com.knowledgegame.core.domain.service.recyclebin.RecycleBinItemStrategy;
import com.knowledgegame.core.infrastructure.markdown.MarkdownRenderer;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeItemAppServiceTest {

    @Mock
    private KnowledgeItemRepository itemRepository;

    @Mock
    private KnowledgeItemDomainService itemDomainService;

    @Mock
    private KnowledgeCategoryRepositoryPort categoryRepositoryPort;

    @Mock
    private FileServiceClient fileServiceClient;

    @Mock
    private MarkdownRenderer markdownRenderer;

    @Mock
    private RecycleBinItemStrategy<KnowledgeItem> recycleBinStrategy;

    @InjectMocks
    private KnowledgeItemAppService appService;

    private MockedStatic<SecurityUtils> securityUtilsMock;

    @BeforeEach
    void setUp() {
        securityUtilsMock = mockStatic(SecurityUtils.class);
        securityUtilsMock.when(SecurityUtils::getCurrentUsername).thenReturn("admin");
        lenient().when(categoryRepositoryPort.findAll()).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        securityUtilsMock.close();
    }

    /**
     * create - 正常创建
     */
    @Test
    void create_shouldSucceed() {
        CreateKnowledgeItemRequest req = buildCreateRequest();
        KnowledgeItem item = buildItem(1L);
        when(categoryRepositoryPort.findById(1L))
                .thenReturn(Optional.of(buildCategory(1L, "分类", KnowledgeCategoryStatus.ACTIVE)));
        when(markdownRenderer.render(anyString())).thenReturn("<p>内容</p>");
        when(itemDomainService.validateAndCreate(anyString(), anyString(), any(), anyList(), anyInt(), anyList()))
                .thenReturn(item);
        when(itemRepository.save(any())).thenReturn(item);
        when(itemRepository.findCategoryIdsByItemId(1L)).thenReturn(List.of(1L));

        KnowledgeItemResponse response = appService.create(req);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals(List.of(1L), response.getCategoryIds());
        verify(itemRepository).saveCategoryRelations(anyLong(), anyList());
    }

    /**
     * create - Markdown 渲染调用
     */
    @Test
    void create_shouldRenderMarkdown() {
        CreateKnowledgeItemRequest req = buildCreateRequest();
        KnowledgeItem item = buildItem(1L);
        when(categoryRepositoryPort.findById(1L))
                .thenReturn(Optional.of(buildCategory(1L, "分类", KnowledgeCategoryStatus.ACTIVE)));
        when(markdownRenderer.render("内容")).thenReturn("<p>内容</p>");
        when(itemDomainService.validateAndCreate(anyString(), anyString(), any(), anyList(), anyInt(), anyList()))
                .thenReturn(item);
        when(itemRepository.save(any())).thenReturn(item);
        when(itemRepository.findCategoryIdsByItemId(1L)).thenReturn(List.of());

        appService.create(req);

        verify(markdownRenderer).render("内容");
    }

    /**
     * getById - 存在
     */
    @Test
    void getById_shouldReturn_whenExists() {
        KnowledgeItem item = buildItem(1L);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(itemRepository.findCategoryIdsByItemId(1L)).thenReturn(List.of(1L, 2L));

        KnowledgeItemResponse response = appService.getById(1L);

        assertNotNull(response);
        assertEquals(List.of(1L, 2L), response.getCategoryIds());
    }

    /**
     * getById - 不存在抛异常
     */
    @Test
    void getById_shouldThrow_whenNotExists() {
        when(itemRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> appService.getById(999L));
    }

    /**
     * delete - 委托 strategy.validateDeletable + moveToRecycleBin，顺序正确
     */
    @Test
    void delete_shouldCallStrategy() {
        appService.delete(1L);

        InOrder inOrder = inOrder(recycleBinStrategy);
        inOrder.verify(recycleBinStrategy).validateDeletable(1L);
        inOrder.verify(recycleBinStrategy).moveToRecycleBin(eq(1L), eq("admin"));
    }

    /**
     * delete - validateDeletable 抛异常 → 传播，不调用 moveToRecycleBin
     */
    @Test
    void delete_shouldThrow_whenValidationFails() {
        doThrow(new BusinessException("知识条目不存在: 1"))
                .when(recycleBinStrategy).validateDeletable(1L);

        assertThrows(BusinessException.class, () -> appService.delete(1L));
        verify(recycleBinStrategy, never()).moveToRecycleBin(anyLong(), anyString());
    }

    /**
     * batchActivate - 全部 ACTIVE 通过
     */
    @Test
    void batchActivate_shouldSucceed() {
        KnowledgeItem item = buildItem(1L);
        when(itemRepository.findByIds(List.of(1L))).thenReturn(List.of(item));
        when(itemRepository.findCategoryIdsByItemIds(List.of(1L))).thenReturn(Map.of(1L, List.of(10L)));
        KnowledgeCategory cat = buildCategory(10L, "分类", KnowledgeCategoryStatus.ACTIVE);
        when(categoryRepositoryPort.findAllByIdIn(List.of(10L))).thenReturn(List.of(cat));

        appService.batchActivate(List.of(1L));

        verify(itemRepository).batchUpdateStatus(List.of(1L), KnowledgeItemStatus.ACTIVE);
    }

    /**
     * batchActivate - ID 不匹配抛异常
     */
    @Test
    void batchActivate_shouldThrow_whenIdMismatch() {
        when(itemRepository.findByIds(List.of(1L, 2L))).thenReturn(List.of(buildItem(1L)));

        assertThrows(BusinessException.class, () -> appService.batchActivate(List.of(1L, 2L)));
    }

    /**
     * batchDeactivate
     */
    @Test
    void batchDeactivate_shouldSucceed() {
        appService.batchDeactivate(List.of(1L, 2L));

        verify(itemRepository).batchUpdateStatus(List.of(1L, 2L), KnowledgeItemStatus.INACTIVE);
    }

    /**
     * batchSort - 逐条更新排序号
     */
    @Test
    void batchSort_shouldUpdateSortOrder() {
        KnowledgeItem item1 = buildItem(1L);
        KnowledgeItem item2 = buildItem(2L);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item1));
        when(itemRepository.findById(2L)).thenReturn(Optional.of(item2));
        when(itemRepository.save(any())).thenReturn(item1, item2);

        BatchSortItem si1 = new BatchSortItem();
        si1.setId(1L);
        si1.setSortOrder(3);
        BatchSortItem si2 = new BatchSortItem();
        si2.setId(2L);
        si2.setSortOrder(5);
        BatchSortRequest req = new BatchSortRequest();
        req.setItems(List.of(si1, si2));
        appService.batchSort(req);

        assertEquals(3, item1.getSortOrder());
        assertEquals(5, item2.getSortOrder());
        verify(itemRepository).save(item1);
        verify(itemRepository).save(item2);
    }

    /**
     * batchSort - 不存在抛异常
     */
    @Test
    void batchSort_shouldThrow_whenNotExists() {
        when(itemRepository.findById(1L)).thenReturn(Optional.empty());

        BatchSortItem si = new BatchSortItem();
        si.setId(1L);
        si.setSortOrder(1);
        BatchSortRequest req = new BatchSortRequest();
        req.setItems(List.of(si));
        assertThrows(BusinessException.class, () -> appService.batchSort(req));
    }

    /**
     * list - sort 不传 → sortField 应为 null（由 adapter 默认排序接管）
     */
    @Test
    void list_shouldPassNullSortField_whenSortNotProvided() {
        KnowledgeItemSummary summary = buildSummary(1L);
        PageResult<KnowledgeItemSummary> domainPage = PageResult.<KnowledgeItemSummary>builder()
                .content(List.of(summary)).totalElements(1).pageNumber(0).pageSize(20).totalPages(1).build();
        when(itemRepository.findByConditionsSummary(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(domainPage);
        when(itemRepository.findCategoryIdsByItemIds(List.of(1L)))
                .thenReturn(Map.of(1L, List.of(10L)));

        appService.list(null, null, null, null, null, null, 0, 20);

        ArgumentCaptor<SortField> captor = ArgumentCaptor.forClass(SortField.class);
        verify(itemRepository).findByConditionsSummary(any(), any(), any(), any(), captor.capture(), anyInt(), anyInt());
        assertNull(captor.getValue(), "sort 不传时期望 sortField 为 null");
    }

    /**
     * list - sort="title" order="asc" → sortField 为 SortField("title", ASC)
     */
    @Test
    void list_shouldPassSortField_whenSortProvided() {
        KnowledgeItemSummary summary = buildSummary(1L);
        PageResult<KnowledgeItemSummary> domainPage = PageResult.<KnowledgeItemSummary>builder()
                .content(List.of(summary)).totalElements(1).pageNumber(0).pageSize(20).totalPages(1).build();
        when(itemRepository.findByConditionsSummary(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(domainPage);
        when(itemRepository.findCategoryIdsByItemIds(List.of(1L)))
                .thenReturn(Map.of(1L, List.of(10L)));

        appService.list(null, null, null, null, "title", "asc", 0, 20);

        ArgumentCaptor<SortField> captor = ArgumentCaptor.forClass(SortField.class);
        verify(itemRepository).findByConditionsSummary(any(), any(), any(), any(), captor.capture(), anyInt(), anyInt());
        SortField captured = captor.getValue();
        assertNotNull(captured);
        assertEquals("title", captured.getField());
        assertEquals(SortField.Direction.ASC, captured.getDirection());
    }

    /**
     * list - 分页查询返回 KnowledgeItemListResponse
     */
    @Test
    void list_shouldReturnPage() {
        KnowledgeItemSummary summary = buildSummary(1L);
        PageResult<KnowledgeItemSummary> domainPage = PageResult.<KnowledgeItemSummary>builder()
                .content(List.of(summary)).totalElements(1).pageNumber(0).pageSize(20).totalPages(1).build();
        when(itemRepository.findByConditionsSummary(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(domainPage);
        when(itemRepository.findCategoryIdsByItemIds(List.of(1L)))
                .thenReturn(Map.of(1L, List.of(10L)));

        PageResult<KnowledgeItemListResponse> result = appService.list(
                null, null, null, null, null, null, 0, 20);

        assertEquals(1, result.getTotalElements());
        KnowledgeItemListResponse first = result.getContent().get(0);
        assertEquals(1L, first.getId());
        assertEquals("标题", first.getTitle());
        assertEquals(List.of(10L), first.getCategoryIds());
    }

    /**
     * update - content 变更时重新渲染
     */
    @Test
    void update_shouldReRender_whenContentChanged() {
        UpdateKnowledgeItemRequest req = new UpdateKnowledgeItemRequest();
        req.setContent("新内容");
        KnowledgeItem item = buildItem(1L);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(markdownRenderer.render("新内容")).thenReturn("<p>新</p>");
        when(itemRepository.save(any())).thenReturn(item);
        when(itemRepository.findCategoryIdsByItemId(1L)).thenReturn(List.of());

        appService.update(1L, req);

        verify(markdownRenderer).render("新内容");
    }

    /**
     * update - content 不变时不重新渲染
     */
    @Test
    void update_shouldNotReRender_whenContentNull() {
        UpdateKnowledgeItemRequest req = new UpdateKnowledgeItemRequest();
        req.setTitle("新标题");
        KnowledgeItem item = buildItem(1L);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(itemRepository.save(any())).thenReturn(item);
        when(itemRepository.findCategoryIdsByItemId(1L)).thenReturn(List.of());

        appService.update(1L, req);

        verify(markdownRenderer, never()).render(anyString());
    }

    /**
     * getCategoryIds
     */
    @Test
    void getCategoryIds_shouldReturn() {
        when(itemRepository.findById(1L)).thenReturn(Optional.of(buildItem(1L)));
        when(itemRepository.findCategoryIdsByItemId(1L)).thenReturn(List.of(10L, 20L));

        List<Long> result = appService.getCategoryIds(1L);

        assertEquals(List.of(10L, 20L), result);
    }

    /**
     * updateCategories
     */
    @Test
    void updateCategories_shouldReplace() {
        when(itemRepository.findById(1L)).thenReturn(Optional.of(buildItem(1L)));
        KnowledgeCategory cat = buildCategory(10L, "分类", KnowledgeCategoryStatus.ACTIVE);
        when(categoryRepositoryPort.findById(10L)).thenReturn(Optional.of(cat));

        appService.updateCategories(1L, List.of(10L));

        verify(itemRepository).saveCategoryRelations(1L, List.of(10L));
    }

    /**
     * updateCategories — 同时选择父分类和子分类 → BusinessException
     */
    @Test
    void updateCategories_shouldThrow_whenAncestorAndDescendant() {
        when(itemRepository.findById(1L)).thenReturn(Optional.of(buildItem(1L)));
        KnowledgeCategory parent = buildCategory(10L, "父分类", KnowledgeCategoryStatus.ACTIVE);
        KnowledgeCategory child = KnowledgeCategory.reconstruct(20L, 10L, "子分类",
                null, null, null, null, 0, KnowledgeCategoryStatus.ACTIVE, null, null);
        when(categoryRepositoryPort.findById(10L)).thenReturn(Optional.of(parent));
        when(categoryRepositoryPort.findById(20L)).thenReturn(Optional.of(child));
        when(categoryRepositoryPort.findAll()).thenReturn(List.of(parent, child));

        assertThrows(BusinessException.class,
                () -> appService.updateCategories(1L, List.of(10L, 20L)));
    }

    // ========== Excel 导入测试 ==========

    /**
     * 场景 a: 正常导入（5 行有效数据，含标签和分类名称）→ 全部成功
     */
    @Test
    @DisplayName("导入 - 正常导入 5 行全部成功")
    void importExcel_shouldSucceed() throws IOException {
        byte[] excel = createTestExcel(new String[][]{
                {"标题1", "内容1", "Java", "Java基础", "启用", "0"},
                {"标题2", "内容2", "Python", "Java基础,Python进阶", "停用", "1"},
                {"标题3", "内容3", "", "Java基础", "", ""},
                {"标题4", "内容4", "Go,Rust", "Java基础", "启用", "10"},
                {"标题5", "内容5", "JS", "Python进阶", "停用", "2"},
        });
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excel);

        KnowledgeCategory javaCat = buildCategory(1L, "Java基础", KnowledgeCategoryStatus.ACTIVE);
        KnowledgeCategory pyCat = buildCategory(2L, "Python进阶", KnowledgeCategoryStatus.ACTIVE);
        when(categoryRepositoryPort.findAll()).thenReturn(List.of(javaCat, pyCat));
        when(markdownRenderer.render(anyString())).thenReturn("<p>rendered</p>");
        KnowledgeItem item = buildItem(1L);
        when(itemDomainService.validateAndCreate(anyString(), anyString(), any(), anyList(), anyInt(), anyList()))
                .thenReturn(item);
        when(itemRepository.save(any())).thenReturn(item);

        KnowledgeItemImportResult result = appService.importExcel(file);

        assertEquals(5, result.getTotalCount());
        assertEquals(5, result.getSuccessCount());
        assertEquals(0, result.getFailCount());
        verify(itemRepository, org.mockito.Mockito.times(5)).save(any());
        verify(itemRepository, org.mockito.Mockito.times(5)).saveCategoryRelations(anyLong(), anyList());
    }

    /**
     * 场景 b: 分类名称不存在 → 1 行失败
     */
    @Test
    @DisplayName("导入 - 分类名称不存在返回失败")
    void importExcel_categoryNotExist_shouldFail() throws IOException {
        byte[] excel = createTestExcel(new String[][]{
                {"标题", "内容", "", "不存在分类", "启用", "0"},
        });
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excel);

        when(categoryRepositoryPort.findAll()).thenReturn(List.of());

        KnowledgeItemImportResult result = appService.importExcel(file);

        assertEquals(1, result.getTotalCount());
        assertEquals(0, result.getSuccessCount());
        assertEquals(1, result.getFailCount());
        assertTrue(result.getFailDetails().get(0).getReason().contains("分类名称不存在或已停用"));
    }

    /**
     * 场景 c: 状态格式错误 → 1 行失败
     */
    @Test
    @DisplayName("导入 - 状态格式错误返回失败")
    void importExcel_invalidStatus_shouldFail() throws IOException {
        byte[] excel = createTestExcel(new String[][]{
                {"标题", "内容", "", "Java基础", "无效状态", "0"},
        });
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excel);

        KnowledgeCategory cat = buildCategory(1L, "Java基础", KnowledgeCategoryStatus.ACTIVE);
        when(categoryRepositoryPort.findAll()).thenReturn(List.of(cat));

        KnowledgeItemImportResult result = appService.importExcel(file);

        assertEquals(1, result.getTotalCount());
        assertEquals(0, result.getSuccessCount());
        assertEquals(1, result.getFailCount());
        assertTrue(result.getFailDetails().get(0).getReason().contains("状态格式错误"));
    }

    /**
     * 场景 d: 行数超限（201 行）→ throw BusinessException
     */
    @Test
    @DisplayName("导入 - 超过 200 行抛异常")
    void importExcel_exceeds200_shouldThrow() throws IOException {
        String[][] data = new String[201][];
        for (int i = 0; i < 201; i++) {
            data[i] = new String[]{"标题" + i, "内容" + i, "", "Java基础", "", ""};
        }
        byte[] excel = createTestExcel(data);
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excel);

        KnowledgeCategory cat = buildCategory(1L, "Java基础", KnowledgeCategoryStatus.ACTIVE);
        when(categoryRepositoryPort.findAll()).thenReturn(List.of(cat));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> appService.importExcel(file));
        assertTrue(exception.getMessage().contains("单次导入上限 200 行"));
    }

    /**
     * 场景 e: 空模板（仅表头 + 填写说明行）→ 返回零统计
     */
    @Test
    @DisplayName("导入 - 空模板返回零统计")
    void importExcel_emptyTemplate_shouldReturnZero() throws IOException {
        byte[] excel = createTestExcel(new String[][]{});
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excel);

        KnowledgeItemImportResult result = appService.importExcel(file);

        assertEquals(0, result.getTotalCount());
        assertEquals(0, result.getSuccessCount());
        assertEquals(0, result.getFailCount());
    }

    /**
     * 场景 f: domainService.validateAndCreate() 抛异常 → 失败记入 failDetails
     */
    @Test
    @DisplayName("导入 - domainService 校验失败返回失败明细")
    void importExcel_domainServiceException_shouldFail() throws IOException {
        byte[] excel = createTestExcel(new String[][]{
                {"标题", "内容", "", "Java基础", "启用", "0"},
        });
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excel);

        KnowledgeCategory cat = buildCategory(1L, "Java基础", KnowledgeCategoryStatus.ACTIVE);
        when(categoryRepositoryPort.findAll()).thenReturn(List.of(cat));
        when(markdownRenderer.render(anyString())).thenReturn("<p>rendered</p>");
        when(itemDomainService.validateAndCreate(anyString(), anyString(), any(), anyList(), anyInt(), anyList()))
                .thenThrow(new BusinessException("知识条目标题不能超过 200 字"));

        KnowledgeItemImportResult result = appService.importExcel(file);

        assertEquals(1, result.getTotalCount());
        assertEquals(0, result.getSuccessCount());
        assertEquals(1, result.getFailCount());
        assertTrue(result.getFailDetails().get(0).getReason().contains("200"));
    }

    /**
     * 场景 h: 导入停用条目 → status 应为 INACTIVE
     */
    @Test
    @DisplayName("导入 - 停用条目 status 正确应用")
    void importExcel_inactiveStatus_shouldDeactivate() throws IOException {
        byte[] excel = createTestExcel(new String[][]{
                {"标题", "内容", "", "Java基础", "停用", "0"},
        });
        MockMultipartFile file = new MockMultipartFile("file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excel);

        KnowledgeCategory cat = buildCategory(1L, "Java基础", KnowledgeCategoryStatus.ACTIVE);
        when(categoryRepositoryPort.findAll()).thenReturn(List.of(cat));
        when(markdownRenderer.render(anyString())).thenReturn("<p>rendered</p>");
        KnowledgeItem item = buildItem(1L);
        when(itemDomainService.validateAndCreate(anyString(), anyString(), any(), anyList(), anyInt(), anyList()))
                .thenReturn(item);
        when(itemRepository.save(any())).thenReturn(item);

        appService.importExcel(file);

        // 验证 deactivate() 被调用（status → INACTIVE）
        assertEquals(KnowledgeItemStatus.INACTIVE, item.getStatus());
    }

    /**
     * 场景 g: 非 .xlsx 文件 → throw BusinessException
     */
    @Test
    @DisplayName("导入 - 非 xlsx 文件抛异常")
    void importExcel_notXlsx_shouldThrow() {
        MockMultipartFile file = new MockMultipartFile("file", "test.txt",
                "text/plain", "not excel".getBytes());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> appService.importExcel(file));
        assertEquals("仅支持 .xlsx 格式文件", exception.getMessage());
    }

    // ========== Markdown zip 导入测试 ==========

    /**
     * 场景 a: 正常导入（index.xlsx 含 3 条 + 3 个 .md 文件）→ 全部成功
     */
    @Test
    @DisplayName("Zip 导入 - 正常导入 3 条全部成功")
    void importMarkdownZip_shouldSucceed() throws IOException {
        byte[] zipBytes = createTestZip(new String[][]{
                {"readme.md", "标题1", "", "Java基础", "启用", "0"},
                {"notes.md", "标题2", "Python", "Java基础,Python进阶", "", ""},
                {"faq.md", "标题3", "", "Java基础", "停用", "5"},
        }, new String[]{"# 内容1", "内容2内容2", "# FAQ\n常见问题解答。"});

        MockMultipartFile file = new MockMultipartFile("file", "test.zip",
                "application/zip", zipBytes);

        KnowledgeCategory javaCat = buildCategory(1L, "Java基础", KnowledgeCategoryStatus.ACTIVE);
        KnowledgeCategory pyCat = buildCategory(2L, "Python进阶", KnowledgeCategoryStatus.ACTIVE);
        when(categoryRepositoryPort.findAll()).thenReturn(List.of(javaCat, pyCat));
        when(markdownRenderer.render(anyString())).thenReturn("<p>rendered</p>");
        KnowledgeItem item = buildItem(1L);
        when(itemDomainService.validateAndCreate(anyString(), anyString(), any(), anyList(), anyInt(), anyList()))
                .thenReturn(item);
        when(itemRepository.save(any())).thenReturn(item);

        KnowledgeItemImportResult result = appService.importMarkdownZip(file);

        assertEquals(3, result.getTotalCount());
        assertEquals(3, result.getSuccessCount());
        assertEquals(0, result.getFailCount());
    }

    /**
     * 场景 b: index.xlsx 引用缺失的 .md 文件 → 失败
     */
    @Test
    @DisplayName("Zip 导入 - 缺失 md 文件返回失败")
    void importMarkdownZip_missingMd_shouldFail() throws IOException {
        byte[] zipBytes = createTestZip(new String[][]{
                {"missing.md", "标题", "", "Java基础", "启用", "0"},
        }, new String[0]);

        MockMultipartFile file = new MockMultipartFile("file", "test.zip",
                "application/zip", zipBytes);

        KnowledgeCategory cat = buildCategory(1L, "Java基础", KnowledgeCategoryStatus.ACTIVE);
        when(categoryRepositoryPort.findAll()).thenReturn(List.of(cat));

        KnowledgeItemImportResult result = appService.importMarkdownZip(file);

        assertEquals(1, result.getTotalCount());
        assertEquals(0, result.getSuccessCount());
        assertEquals(1, result.getFailCount());
        assertTrue(result.getFailDetails().get(0).getReason().contains("Markdown 文件不存在"));
    }

    /**
     * 场景 d: 仅含表头的 index.xlsx（无数据行）→ totalCount=0
     */
    @Test
    @DisplayName("Zip 导入 - 空模板返回零统计")
    void importMarkdownZip_emptyTemplate_shouldReturnZero() throws IOException {
        byte[] zipBytes = createTestZip(new String[][]{}, new String[0]);

        MockMultipartFile file = new MockMultipartFile("file", "test.zip",
                "application/zip", zipBytes);

        KnowledgeItemImportResult result = appService.importMarkdownZip(file);

        assertEquals(0, result.getTotalCount());
        assertEquals(0, result.getSuccessCount());
        assertEquals(0, result.getFailCount());
    }

    /**
     * 场景 e: 非 .zip 文件 → throw BusinessException
     */
    @Test
    @DisplayName("Zip 导入 - 非 zip 文件抛异常")
    void importMarkdownZip_notZip_shouldThrow() {
        MockMultipartFile file = new MockMultipartFile("file", "test.rar",
                "application/x-rar-compressed", "not zip".getBytes());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> appService.importMarkdownZip(file));
        assertEquals("仅支持 .zip 格式文件", exception.getMessage());
    }

    // ========== 辅助方法 ==========

    /**
     * 创建测试用 Excel byte[]
     */
    private byte[] createTestExcel(String[][] data) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            String[] headers = {"标题", "Markdown正文", "标签", "分类名称", "状态", "排序"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            for (int r = 0; r < data.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < data[r].length; c++) {
                    if (data[r][c] != null && !data[r][c].isEmpty()) {
                        row.createCell(c).setCellValue(data[r][c]);
                    }
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    /**
     * 创建测试用 zip byte[]（含 index.xlsx + 若干 .md 文件）
     */
    private byte[] createTestZip(String[][] rows, String[] mdContents) throws IOException {
        // 构建 index.xlsx
        byte[] xlsxBytes;
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            String[] headers = {"文件名", "标题", "标签", "分类名称", "状态", "排序"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    if (rows[r][c] != null && !rows[r][c].isEmpty()) {
                        row.createCell(c).setCellValue(rows[r][c]);
                    }
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            xlsxBytes = out.toByteArray();
        }

        // 构建 zip
        ByteArrayOutputStream zipOut = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(zipOut)) {
            ZipEntry xlsxEntry = new ZipEntry("index.xlsx");
            zos.putNextEntry(xlsxEntry);
            zos.write(xlsxBytes);
            zos.closeEntry();

            for (int i = 0; i < mdContents.length; i++) {
                String mdName = rows[i][0];
                ZipEntry mdEntry = new ZipEntry(mdName);
                zos.putNextEntry(mdEntry);
                zos.write(mdContents[i].getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return zipOut.toByteArray();
    }

    private CreateKnowledgeItemRequest buildCreateRequest() {
        CreateKnowledgeItemRequest req = new CreateKnowledgeItemRequest();
        req.setTitle("标题");
        req.setContent("内容");
        req.setCoverImageFileId(null);
        req.setTags(List.of("Java"));
        req.setSortOrder(0);
        req.setCategoryIds(List.of(1L));
        return req;
    }

    private KnowledgeItem buildItem(Long id) {
        return KnowledgeItem.reconstruct(id, "标题", "内容", "<p>内容</p>",
                null, List.of("Java"), 0,
                KnowledgeItemStatus.ACTIVE,
                LocalDateTime.now(), LocalDateTime.now());
    }

    private KnowledgeItemSummary buildSummary(Long id) {
        return KnowledgeItemSummary.reconstruct(id, "标题",
                null, List.of("Java"), 0,
                KnowledgeItemStatus.ACTIVE,
                LocalDateTime.now(), LocalDateTime.now());
    }

    private KnowledgeCategory buildCategory(Long id, String name, KnowledgeCategoryStatus status) {
        return KnowledgeCategory.reconstruct(id, null, name, null, null, null, null, 0,
                status, null, null);
    }
}
