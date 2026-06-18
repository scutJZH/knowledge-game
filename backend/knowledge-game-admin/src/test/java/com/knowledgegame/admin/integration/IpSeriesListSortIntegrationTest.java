package com.knowledgegame.admin.integration;

import com.knowledgegame.core.domain.model.domainenum.IpSeriesStatus;
import com.knowledgegame.core.domain.model.entity.IpSeries;
import com.knowledgegame.core.domain.model.vo.PageResult;
import com.knowledgegame.core.domain.model.vo.SortField;
import com.knowledgegame.core.domain.port.outbound.IpSeriesRepositoryPort;
import com.knowledgegame.core.infrastructure.adapter.repoadapter.IpSeriesRepositoryAdapter;
import com.knowledgegame.core.infrastructure.db.entity.IpSeriesPO;
import com.knowledgegame.core.infrastructure.db.repository.IpSeriesJpaRepository;
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

/**
 * IP 系列列表排序/搜索集成测试（REQ-86 ISSUE-3）
 * <p>
 * 使用真实 MySQL knowledge_game_test 库（@DataJpaTest + RepositoryAdapter）：
 * <ul>
 *   <li>覆盖排序链路：SortField → SortFieldSpec → SortFields → Spring Data JPA ORDER BY</li>
 *   <li>覆盖 code 模糊搜索与 name 模糊搜索的 AND 组合</li>
 * </ul>
 * <p>
 * Mock 策略：不 Mock，走真实 JPA。本地需先建库：
 * CREATE DATABASE knowledge_game_test DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackages = "com.knowledgegame.core.infrastructure.db.entity")
@EnableJpaRepositories(basePackages = "com.knowledgegame.core.infrastructure.db.repository")
@Import(IpSeriesRepositoryAdapter.class)
@ActiveProfiles("test")
class IpSeriesListSortIntegrationTest {

    @Autowired
    private IpSeriesJpaRepository jpaRepository;

    @Autowired
    private IpSeriesRepositoryPort ipSeriesRepositoryPort;

    /**
     * sort=code&order=asc 应按 code 字典序 ASC
     */
    @Test
    @DisplayName("findByConditions sort=code&order=asc 时按 code 字典序升序")
    void findByConditions_sortCodeAsc_returnsCodeAscOrder() {
        jpaRepository.save(buildPO("IP003", "漫威", time(2026, 1, 1), IpSeriesStatus.ACTIVE));
        jpaRepository.save(buildPO("IP001", "DC", time(2026, 1, 2), IpSeriesStatus.ACTIVE));
        jpaRepository.save(buildPO("IP002", "火影", time(2026, 1, 3), IpSeriesStatus.ACTIVE));

        PageResult<IpSeries> page = ipSeriesRepositoryPort.findByConditions(
                null, null, null,
                new SortField("code", SortField.Direction.ASC),
                0, 20);

        List<IpSeries> content = page.getContent();
        assertEquals(3, content.size());
        assertEquals("IP001", content.get(0).getCode());
        assertEquals("IP002", content.get(1).getCode());
        assertEquals("IP003", content.get(2).getCode());
    }

    /**
     * sort=null（默认）应按 createdAt DESC：createdAt 越晚越靠前
     */
    @Test
    @DisplayName("findByConditions sort=null 时按 createdAt DESC 默认排序")
    void findByConditions_sortNull_fallbackToCreatedAtDesc() {
        jpaRepository.save(buildPO("IP001", "漫威", time(2026, 1, 1), IpSeriesStatus.ACTIVE));
        jpaRepository.save(buildPO("IP002", "DC", time(2026, 1, 3), IpSeriesStatus.ACTIVE));
        jpaRepository.save(buildPO("IP003", "火影", time(2026, 1, 2), IpSeriesStatus.ACTIVE));

        PageResult<IpSeries> page = ipSeriesRepositoryPort.findByConditions(
                null, null, null, null, 0, 20);

        List<IpSeries> content = page.getContent();
        assertEquals(3, content.size());
        assertEquals("IP002", content.get(0).getCode());
        assertEquals("IP003", content.get(1).getCode());
        assertEquals("IP001", content.get(2).getCode());
    }

    /**
     * code=IP001 应只返回 code 包含 "IP001" 的记录
     */
    @Test
    @DisplayName("findByConditions code=IP001 时仅返回 code 匹配的记录")
    void findByConditions_codeParam_returnsOnlyMatchingCode() {
        jpaRepository.save(buildPO("IP001", "漫威", time(2026, 1, 1), IpSeriesStatus.ACTIVE));
        jpaRepository.save(buildPO("IP002", "DC", time(2026, 1, 2), IpSeriesStatus.ACTIVE));
        jpaRepository.save(buildPO("MARVEL001", "火影", time(2026, 1, 3), IpSeriesStatus.ACTIVE));

        PageResult<IpSeries> page = ipSeriesRepositoryPort.findByConditions(
                null, "IP001", null, null, 0, 20);

        List<IpSeries> content = page.getContent();
        assertEquals(1, content.size());
        assertEquals("IP001", content.get(0).getCode());
    }

    /**
     * name + code AND 组合：同时满足两个模糊条件才返回
     */
    @Test
    @DisplayName("findByConditions name + code 同时传时按 AND 组合过滤")
    void findByConditions_nameAndCode_andCombination() {
        jpaRepository.save(buildPO("IP001", "漫威宇宙", time(2026, 1, 1), IpSeriesStatus.ACTIVE));
        jpaRepository.save(buildPO("IP002", "漫威英雄", time(2026, 1, 2), IpSeriesStatus.ACTIVE));
        jpaRepository.save(buildPO("IP001X", "DC宇宙", time(2026, 1, 3), IpSeriesStatus.ACTIVE));

        PageResult<IpSeries> page = ipSeriesRepositoryPort.findByConditions(
                "漫威", "IP001", null, null, 0, 20);

        List<IpSeries> content = page.getContent();
        assertEquals(1, content.size());
        assertEquals("IP001", content.get(0).getCode());
        assertEquals("漫威宇宙", content.get(0).getName());
    }

    // --- 辅助方法 ---

    private IpSeriesPO buildPO(String code, String name, LocalDateTime createdAt,
                                IpSeriesStatus status) {
        IpSeriesPO po = new IpSeriesPO();
        po.setCode(code);
        po.setName(name);
        po.setDescription("desc");
        po.setStatus(status);
        po.setCreatedAt(createdAt);
        po.setUpdatedAt(createdAt);
        return po;
    }

    private LocalDateTime time(int year, int month, int day) {
        return LocalDateTime.of(year, month, day, 0, 0);
    }
}
