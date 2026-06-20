package com.knowledgegame.core.infrastructure.adapter.repoadapter;

import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.domainenum.ResourceType;
import com.knowledgegame.core.domain.model.entity.RecycleBinItem;
import com.knowledgegame.core.domain.model.vo.PageResult;
import com.knowledgegame.core.domain.model.vo.SortField;
import com.knowledgegame.core.infrastructure.db.entity.IpSeriesDeletedPO;
import com.knowledgegame.core.infrastructure.db.entity.RecycleBinItemPO;
import com.knowledgegame.core.infrastructure.db.repository.IpSeriesDeletedJpaRepository;
import com.knowledgegame.core.infrastructure.db.repository.RecycleBinItemJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RecycleBinItemRepositoryAdapter 集成测试
 * <p>
 * 使用真实 MySQL 测试数据库，覆盖 DDL 建表、分页查询、过滤、排序等核心功能。
 */
@DataJpaTest
@EntityScan(basePackages = "com.knowledgegame.core.infrastructure.db.entity")
@EnableJpaRepositories(basePackages = "com.knowledgegame.core.infrastructure.db.repository")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(RecycleBinItemRepositoryAdapter.class)
@ActiveProfiles("test")
class RecycleBinItemRepositoryAdapterIT {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private RecycleBinItemJpaRepository recycleBinItemJpaRepository;

    @Autowired
    private IpSeriesDeletedJpaRepository ipSeriesDeletedJpaRepository;

    @Autowired
    private RecycleBinItemRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        recycleBinItemJpaRepository.deleteAll();
    }

    // ===== DDL 测试 =====

    @Test
    @DisplayName("6 张表 DDL 通过 ddl-auto 自动生成，INSERT 不报错")
    void allSixTables_shouldBeCreatedAndAcceptInsert() {
        // recycle_bin 表
        RecycleBinItemPO binPo = RecycleBinItemPO.builder()
                .resourceType(ResourceType.IP_SERIES)
                .originalId(1L)
                .originalName("火影忍者")
                .deletedBy("admin")
                .deletedAt(LocalDateTime.now())
                .restoreDeadline(LocalDateTime.now().plusDays(30))
                .build();
        RecycleBinItemPO savedBin = recycleBinItemJpaRepository.save(binPo);
        assertNotNull(savedBin.getId());

        // ip_series_deleted 表
        IpSeriesDeletedPO deletedPo = IpSeriesDeletedPO.builder()
                .originalId(1L)
                .code("NARUTO")
                .name("火影忍者")
                .status(com.knowledgegame.core.domain.model.domainenum.IpSeriesStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .deletedBy("admin")
                .deletedAt(LocalDateTime.now())
                .build();
        IpSeriesDeletedPO savedDeleted = ipSeriesDeletedJpaRepository.save(deletedPo);
        assertNotNull(savedDeleted.getId());
        assertEquals(1L, savedDeleted.getOriginalId());
    }

    // ===== 空表查询 =====

    @Test
    @DisplayName("空表查询应返回空分页结果")
    void findAll_emptyTable_shouldReturnEmptyPage() {
        PageResult<RecycleBinItem> result = adapter.findAll(null, null, 0, 20, null);
        assertEquals(0, result.getTotalElements());
        assertTrue(result.getContent().isEmpty());
    }

    // ===== resourceType 过滤 =====

    @Test
    @DisplayName("resourceType 过滤应只返回指定类型")
    void findAll_filterByResourceType_shouldReturnOnlyMatchingType() {
        // 构造 2 条不同类型数据
        recycleBinItemJpaRepository.save(RecycleBinItemPO.builder()
                .resourceType(ResourceType.IP_SERIES).originalId(1L).originalName("火影忍者")
                .deletedBy("admin").deletedAt(LocalDateTime.now()).restoreDeadline(LocalDateTime.now().plusDays(30))
                .build());
        recycleBinItemJpaRepository.save(RecycleBinItemPO.builder()
                .resourceType(ResourceType.QUESTION).originalId(2L).originalName("测试题")
                .deletedBy("admin").deletedAt(LocalDateTime.now()).restoreDeadline(LocalDateTime.now().plusDays(30))
                .build());

        PageResult<RecycleBinItem> result = adapter.findAll(ResourceType.IP_SERIES, null, 0, 20, null);
        assertEquals(1, result.getTotalElements());
        assertEquals(ResourceType.IP_SERIES, result.getContent().get(0).getResourceType());
    }

    // ===== keyword 模糊匹配 =====

    @Test
    @DisplayName("keyword 模糊匹配应返回名称包含关键字的记录")
    void findAll_filterByKeyword_shouldReturnMatchingName() {
        recycleBinItemJpaRepository.save(RecycleBinItemPO.builder()
                .resourceType(ResourceType.IP_SERIES).originalId(1L).originalName("火影忍者")
                .deletedBy("admin").deletedAt(LocalDateTime.now()).restoreDeadline(LocalDateTime.now().plusDays(30))
                .build());
        recycleBinItemJpaRepository.save(RecycleBinItemPO.builder()
                .resourceType(ResourceType.IP_SERIES).originalId(2L).originalName("海贼王")
                .deletedBy("admin").deletedAt(LocalDateTime.now()).restoreDeadline(LocalDateTime.now().plusDays(30))
                .build());

        PageResult<RecycleBinItem> result = adapter.findAll(null, "火影", 0, 20, null);
        assertEquals(1, result.getTotalElements());
        assertEquals("火影忍者", result.getContent().get(0).getOriginalName());
    }

    // ===== 白名单排序校验 =====

    @Test
    @DisplayName("非法 sort 字段应抛 BusinessException(400)")
    void findAll_invalidSortField_shouldThrowBusinessException() {
        SortField invalidSort = new SortField("invalidField", SortField.Direction.ASC);
        BusinessException ex = assertThrows(BusinessException.class, () ->
                adapter.findAll(null, null, 0, 20, invalidSort));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("不支持的排序字段"));
        assertTrue(ex.getMessage().contains("invalidField"));
    }

    // ===== 默认排序 deletedAt DESC =====

    @Test
    @DisplayName("默认排序应为 deletedAt DESC")
    void findAll_defaultSort_shouldBeDeletedAtDesc() {
        LocalDateTime earlier = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime later = LocalDateTime.of(2026, 6, 1, 0, 0);
        recycleBinItemJpaRepository.save(RecycleBinItemPO.builder()
                .resourceType(ResourceType.IP_SERIES).originalId(1L).originalName("较早")
                .deletedBy("admin").deletedAt(earlier).restoreDeadline(earlier.plusDays(30))
                .build());
        recycleBinItemJpaRepository.save(RecycleBinItemPO.builder()
                .resourceType(ResourceType.IP_SERIES).originalId(2L).originalName("较晚")
                .deletedBy("admin").deletedAt(later).restoreDeadline(later.plusDays(30))
                .build());

        PageResult<RecycleBinItem> result = adapter.findAll(null, null, 0, 20, null);
        assertEquals(2, result.getTotalElements());
        // 较晚删除的应排在前面（DESC）
        assertEquals("较晚", result.getContent().get(0).getOriginalName());
    }

    // ===== 白名单合法排序 =====

    @Test
    @DisplayName("合法排序字段 originalName ASC 应正确排序")
    void findAll_sortByOriginalNameAsc_shouldReturnAscendingOrder() {
        recycleBinItemJpaRepository.save(RecycleBinItemPO.builder()
                .resourceType(ResourceType.IP_SERIES).originalId(1L).originalName("B 资源")
                .deletedBy("admin").deletedAt(LocalDateTime.now()).restoreDeadline(LocalDateTime.now().plusDays(30))
                .build());
        recycleBinItemJpaRepository.save(RecycleBinItemPO.builder()
                .resourceType(ResourceType.IP_SERIES).originalId(2L).originalName("A 资源")
                .deletedBy("admin").deletedAt(LocalDateTime.now()).restoreDeadline(LocalDateTime.now().plusDays(30))
                .build());

        SortField ascSort = new SortField("originalName", SortField.Direction.ASC);
        PageResult<RecycleBinItem> result = adapter.findAll(null, null, 0, 20, ascSort);
        assertEquals("A 资源", result.getContent().get(0).getOriginalName());
        assertEquals("B 资源", result.getContent().get(1).getOriginalName());
    }

    // ===== 分页测试 =====

    @Test
    @DisplayName("分页查询应正确切分数据")
    void findAll_pagination_shouldSliceCorrectly() {
        for (int i = 0; i < 15; i++) {
            recycleBinItemJpaRepository.save(RecycleBinItemPO.builder()
                    .resourceType(ResourceType.IP_SERIES).originalId((long) i).originalName("资源" + i)
                    .deletedBy("admin").deletedAt(LocalDateTime.now()).restoreDeadline(LocalDateTime.now().plusDays(30))
                    .build());
        }

        PageResult<RecycleBinItem> page1 = adapter.findAll(null, null, 0, 10, null);
        assertEquals(15, page1.getTotalElements());
        assertEquals(2, page1.getTotalPages());
        assertEquals(10, page1.getContent().size());

        PageResult<RecycleBinItem> page2 = adapter.findAll(null, null, 1, 10, null);
        assertEquals(5, page2.getContent().size());
    }

    // ===== findById =====

    @Test
    @DisplayName("findById 应返回正确的回收站条目")
    void findById_shouldReturnCorrectItem() {
        RecycleBinItemPO po = recycleBinItemJpaRepository.save(RecycleBinItemPO.builder()
                .resourceType(ResourceType.IP_SERIES).originalId(42L).originalName("测试")
                .deletedBy("admin").deletedAt(LocalDateTime.now()).restoreDeadline(LocalDateTime.now().plusDays(30))
                .build());

        RecycleBinItem item = adapter.findById(po.getId()).orElseThrow();
        assertEquals(ResourceType.IP_SERIES, item.getResourceType());
        assertEquals(42L, item.getOriginalId());
        assertEquals("测试", item.getOriginalName());
    }

    @Test
    @DisplayName("findById 不存在的 ID 应返回 empty")
    void findById_nonExistentId_shouldReturnEmpty() {
        assertTrue(adapter.findById(99999L).isEmpty());
    }

    // ===== findAllById (REQ-103 落地，REQ-102 共用) =====

    @Test
    @DisplayName("findAllById 正常 id → 查回对应条目")
    void findAllById_existingIds_shouldReturnItems() {
        RecycleBinItemPO po1 = recycleBinItemJpaRepository.save(RecycleBinItemPO.builder()
                .resourceType(ResourceType.IP_SERIES).originalId(10L).originalName("条目1")
                .deletedBy("admin").deletedAt(LocalDateTime.now()).restoreDeadline(LocalDateTime.now().plusDays(30))
                .build());
        RecycleBinItemPO po2 = recycleBinItemJpaRepository.save(RecycleBinItemPO.builder()
                .resourceType(ResourceType.QUESTION).originalId(20L).originalName("条目2")
                .deletedBy("admin").deletedAt(LocalDateTime.now()).restoreDeadline(LocalDateTime.now().plusDays(30))
                .build());

        List<RecycleBinItem> items = adapter.findAllById(List.of(po1.getId(), po2.getId()));

        assertEquals(2, items.size());
        assertEquals("条目1", items.get(0).getOriginalName());
        assertEquals("条目2", items.get(1).getOriginalName());
    }

    @Test
    @DisplayName("findAllById 部分不存在 id → 静默跳过，返回存在的")
    void findAllById_partialNonExistent_shouldSkipMissing() {
        RecycleBinItemPO po = recycleBinItemJpaRepository.save(RecycleBinItemPO.builder()
                .resourceType(ResourceType.IP_SERIES).originalId(1L).originalName("存在")
                .deletedBy("admin").deletedAt(LocalDateTime.now()).restoreDeadline(LocalDateTime.now().plusDays(30))
                .build());

        List<RecycleBinItem> items = adapter.findAllById(List.of(po.getId(), 99999L));

        assertEquals(1, items.size());
        assertEquals("存在", items.get(0).getOriginalName());
    }

    @Test
    @DisplayName("findAllById 空集合 → 返回空列表")
    void findAllById_emptyCollection_shouldReturnEmptyList() {
        List<RecycleBinItem> items = adapter.findAllById(Collections.emptyList());
        assertTrue(items.isEmpty());
    }
}
