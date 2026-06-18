package com.knowledgegame.admin.integration;

import com.knowledgegame.core.domain.model.domainenum.KnowledgeCategoryStatus;
import com.knowledgegame.core.domain.model.entity.KnowledgeCategory;
import com.knowledgegame.core.domain.model.vo.PageResult;
import com.knowledgegame.core.domain.model.vo.SortField;
import com.knowledgegame.core.domain.port.outbound.KnowledgeCategoryRepositoryPort;
import com.knowledgegame.core.infrastructure.adapter.repoadapter.KnowledgeCategoryRepositoryAdapter;
import com.knowledgegame.core.infrastructure.db.entity.KnowledgeCategoryPO;
import com.knowledgegame.core.infrastructure.db.repository.KnowledgeCategoryJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 知识点分类列表排序集成测试（REQ-86 ISSUE-5）
 * <p>
 * 使用真实 MySQL knowledge_game_test 库（@DataJpaTest + RepositoryAdapter）：
 * <ul>
 *   <li>覆盖排序链路：SortField → SortFieldSpec → SortFields → Spring Data JPA ORDER BY</li>
 *   <li>验证默认双字段（sortOrder ASC, createdAt DESC）和单字段覆盖</li>
 * </ul>
 * <p>
 * Mock 策略：不 Mock，走真实 JPA。本地需先建库：
 * CREATE DATABASE knowledge_game_test DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackages = "com.knowledgegame.core.infrastructure.db.entity")
@EnableJpaRepositories(basePackages = "com.knowledgegame.core.infrastructure.db.repository")
@Import(KnowledgeCategoryRepositoryAdapter.class)
@ActiveProfiles("test")
class KnowledgeCategoryListSortIntegrationTest {

    @Autowired
    private KnowledgeCategoryJpaRepository jpaRepository;

    @Autowired
    private KnowledgeCategoryRepositoryPort categoryRepositoryPort;

    /**
     * 默认排序（sort=null）应按 sortOrder ASC, createdAt DESC：
     * 准备两条 sortOrder 相同但 createdAt 不同的记录，验证 createdAt DESC 生效。
     */
    @Test
    @DisplayName("findByConditions sort=null 时按 sortOrder ASC, createdAt DESC 双字段排序")
    void findByConditions_sortNull_fallbackToDualFieldDefault() {
        jpaRepository.save(buildPO("编程", 1, time(2026, 1, 1), KnowledgeCategoryStatus.ACTIVE));
        jpaRepository.save(buildPO("数学", 1, time(2026, 1, 2), KnowledgeCategoryStatus.ACTIVE));
        jpaRepository.save(buildPO("英语", 0, time(2026, 1, 3), KnowledgeCategoryStatus.ACTIVE));

        PageResult<KnowledgeCategory> page = categoryRepositoryPort.findByConditions(
                null, null, null, null, 0, 20);

        List<KnowledgeCategory> content = page.getContent();
        assertEquals(3, content.size());
        // sortOrder=0 排第一
        assertEquals("英语", content.get(0).getName());
        // sortOrder=1 的两条按 createdAt DESC（数学较晚，排第二）
        assertEquals("数学", content.get(1).getName());
        assertEquals("编程", content.get(2).getName());
    }

    /**
     * sort=name&order=asc 覆盖默认，按 name ASC
     */
    @Test
    @DisplayName("findByConditions sort=name&order=asc 时覆盖默认按 name ASC")
    void findByConditions_sortNameAsc_returnsNameAscSort() {
        jpaRepository.save(buildPO("Charlie", 0, time(2026, 1, 3), KnowledgeCategoryStatus.ACTIVE));
        jpaRepository.save(buildPO("Alpha", 2, time(2026, 1, 2), KnowledgeCategoryStatus.ACTIVE));
        jpaRepository.save(buildPO("Bravo", 1, time(2026, 1, 1), KnowledgeCategoryStatus.ACTIVE));

        PageResult<KnowledgeCategory> page = categoryRepositoryPort.findByConditions(
                null, null, null,
                new SortField("name", SortField.Direction.ASC),
                0, 20);

        List<KnowledgeCategory> content = page.getContent();
        assertEquals(3, content.size());
        assertEquals("Alpha", content.get(0).getName());
        assertEquals("Bravo", content.get(1).getName());
        assertEquals("Charlie", content.get(2).getName());
    }

    /**
     * sort=sortOrder&order=desc 覆盖默认，按 sortOrder DESC
     */
    @Test
    @DisplayName("findByConditions sort=sortOrder&order=desc 时覆盖默认按 sortOrder DESC")
    void findByConditions_sortSortOrderDesc_returnsSortOrderDescSort() {
        jpaRepository.save(buildPO("低序号", 0, time(2026, 1, 3), KnowledgeCategoryStatus.ACTIVE));
        jpaRepository.save(buildPO("高序号", 5, time(2026, 1, 1), KnowledgeCategoryStatus.ACTIVE));
        jpaRepository.save(buildPO("中序号", 2, time(2026, 1, 2), KnowledgeCategoryStatus.ACTIVE));

        PageResult<KnowledgeCategory> page = categoryRepositoryPort.findByConditions(
                null, null, null,
                new SortField("sortOrder", SortField.Direction.DESC),
                0, 20);

        List<KnowledgeCategory> content = page.getContent();
        assertEquals(3, content.size());
        assertEquals("高序号", content.get(0).getName());
        assertEquals("中序号", content.get(1).getName());
        assertEquals("低序号", content.get(2).getName());
    }

    // --- 辅助方法 ---

    private KnowledgeCategoryPO buildPO(String name, int sortOrder, LocalDateTime createdAt,
                                         KnowledgeCategoryStatus status) {
        KnowledgeCategoryPO po = new KnowledgeCategoryPO();
        po.setParentId(null);
        po.setName(name);
        po.setDescription("desc");
        po.setSortOrder(sortOrder);
        po.setStatus(status);
        po.setCreatedAt(createdAt);
        po.setUpdatedAt(createdAt);
        return po;
    }

    private LocalDateTime time(int year, int month, int day) {
        return LocalDateTime.of(year, month, day, 0, 0);
    }
}
