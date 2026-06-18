package com.knowledgegame.admin.integration;

import com.knowledgegame.core.domain.model.domainenum.CardRarity;
import com.knowledgegame.core.domain.model.domainenum.CardTemplateStatus;
import com.knowledgegame.core.domain.model.entity.CardTemplate;
import com.knowledgegame.core.domain.model.vo.PageResult;
import com.knowledgegame.core.domain.model.vo.SortField;
import com.knowledgegame.core.domain.port.outbound.CardTemplateRepositoryPort;
import com.knowledgegame.core.infrastructure.adapter.repoadapter.CardTemplateRepositoryAdapter;
import com.knowledgegame.core.infrastructure.db.entity.CardTemplatePO;
import com.knowledgegame.core.infrastructure.db.repository.CardTemplateJpaRepository;
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
 * 卡牌模板列表排序 + code 搜索集成测试（REQ-86 ISSUE-4）
 * <p>
 * 使用真实 MySQL knowledge_game_test 库（@DataJpaTest + RepositoryAdapter）：
 * <ul>
 *   <li>覆盖排序链路：SortField → SortFieldSpec → SortFields → Spring Data JPA ORDER BY</li>
 *   <li>覆盖 code 模糊搜索的 Specification 实际 SQL 行为</li>
 * </ul>
 * <p>
 * Mock 策略：不 MOCK，走真实 JPA。本地需先建库：
 * CREATE DATABASE knowledge_game_test DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackages = "com.knowledgegame.core.infrastructure.db.entity")
@EnableJpaRepositories(basePackages = "com.knowledgegame.core.infrastructure.db.repository")
@Import(CardTemplateRepositoryAdapter.class)
@ActiveProfiles("test")
class CardTemplateListSortIntegrationTest {

    @Autowired
    private CardTemplateJpaRepository jpaRepository;

    @Autowired
    private CardTemplateRepositoryPort cardTemplateRepositoryPort;

    /**
     * sort=null 走默认 createdAt DESC：先创建的排后
     */
    @Test
    @DisplayName("findByConditions sort=null 时按 createdAt DESC")
    void findByConditions_sortNull_fallbackToCreatedAtDesc() {
        jpaRepository.save(buildPO("CT001", "皮卡丘", time(2026, 1, 1)));
        jpaRepository.save(buildPO("CT002", "喷火龙", time(2026, 1, 3)));
        jpaRepository.save(buildPO("CT003", "杰尼龟", time(2026, 1, 2)));

        PageResult<CardTemplate> page = cardTemplateRepositoryPort.findByConditions(
                null, null, null, null, null, null, 0, 20);

        List<CardTemplate> content = page.getContent();
        assertEquals(3, content.size());
        assertEquals("CT002", content.get(0).getCode());
        assertEquals("CT003", content.get(1).getCode());
        assertEquals("CT001", content.get(2).getCode());
    }

    /**
     * sort=code&order=asc 按 code 字典序
     */
    @Test
    @DisplayName("findByConditions sort=code&order=asc 时按 code ASC")
    void findByConditions_sortCodeAsc_returnsCodeAscSort() {
        jpaRepository.save(buildPO("CT003", "C", time(2026, 1, 1)));
        jpaRepository.save(buildPO("CT001", "A", time(2026, 1, 2)));
        jpaRepository.save(buildPO("CT002", "B", time(2026, 1, 3)));

        PageResult<CardTemplate> page = cardTemplateRepositoryPort.findByConditions(
                null, null, null, null, null,
                new SortField("code", SortField.Direction.ASC),
                0, 20);

        List<CardTemplate> content = page.getContent();
        assertEquals(3, content.size());
        assertEquals("CT001", content.get(0).getCode());
        assertEquals("CT002", content.get(1).getCode());
        assertEquals("CT003", content.get(2).getCode());
    }

    /**
     * code=CT 模糊搜索，匹配所有 CT 开头（默认 createdAt DESC 顺序）
     */
    @Test
    @DisplayName("findByConditions code=CT 模糊匹配所有 CT 编码")
    void findByConditions_codeFilter_matchesAllCT() {
        jpaRepository.save(buildPO("CT001", "皮卡丘", time(2026, 1, 1)));
        jpaRepository.save(buildPO("CT002", "喷火龙", time(2026, 1, 2)));
        jpaRepository.save(buildPO("XY001", "可达鸭", time(2026, 1, 3)));

        PageResult<CardTemplate> page = cardTemplateRepositoryPort.findByConditions(
                null, "CT", null, null, null, null, 0, 20);

        List<CardTemplate> content = page.getContent();
        assertEquals(2, content.size());
        // 默认 createdAt DESC，CT002（1-2）比 CT001（1-1）晚，排在前
        assertEquals("CT002", content.get(0).getCode());
        assertEquals("CT001", content.get(1).getCode());
    }

    /**
     * name=皮 + code=CT AND 组合（两个条件都满足才返回）
     */
    @Test
    @DisplayName("findByConditions name+code AND 组合（两个条件都满足）")
    void findByConditions_nameAndCode_andCombination() {
        jpaRepository.save(buildPO("CT001", "皮卡丘", time(2026, 1, 1)));
        jpaRepository.save(buildPO("CT002", "皮皮", time(2026, 1, 2)));
        jpaRepository.save(buildPO("XY001", "皮可西", time(2026, 1, 3)));

        PageResult<CardTemplate> page = cardTemplateRepositoryPort.findByConditions(
                "皮", "CT", null, null, null, null, 0, 20);

        List<CardTemplate> content = page.getContent();
        assertEquals(2, content.size());
        // 仅匹配 CT 开头且名字含"皮"的两条
    }

    // --- 辅助方法 ---

    private CardTemplatePO buildPO(String code, String name, LocalDateTime createdAt) {
        CardTemplatePO po = new CardTemplatePO();
        po.setIpSeriesId(1L);
        po.setCode(code);
        po.setName(name);
        po.setRarity(CardRarity.N);
        po.setDescription("desc");
        po.setStatus(CardTemplateStatus.ACTIVE);
        po.setCreatedAt(createdAt);
        po.setUpdatedAt(createdAt);
        return po;
    }

    private LocalDateTime time(int year, int month, int day) {
        return LocalDateTime.of(year, month, day, 0, 0);
    }
}
