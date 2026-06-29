package com.knowledgegame.core.infrastructure.adapter.repoadapter;

import com.knowledgegame.core.domain.model.domainenum.IpSeriesStatus;
import com.knowledgegame.core.domain.model.entity.IpSeries;
import com.knowledgegame.core.infrastructure.db.entity.IpSeriesPO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@EntityScan(basePackages = "com.knowledgegame.core.infrastructure.db.entity")
@EnableJpaRepositories(basePackages = "com.knowledgegame.core.infrastructure.db.repository")
@Import(IpSeriesRepositoryAdapter.class)
class IpSeriesRepositoryAdapterIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private IpSeriesRepositoryAdapter adapter;

    @Test
    @DisplayName("findAllActive → 仅返回 ACTIVE 状态的 IP，INACTIVE 不出现")
    void shouldReturnOnlyActiveIpSeries() {
        IpSeriesPO active1 = persistIpSeries("ACTIVE-1", "活跃系列1", IpSeriesStatus.ACTIVE);
        IpSeriesPO active2 = persistIpSeries("ACTIVE-2", "活跃系列2", IpSeriesStatus.ACTIVE);
        persistIpSeries("INACTIVE-1", "停用系列1", IpSeriesStatus.INACTIVE);

        entityManager.flush();
        entityManager.clear();

        List<IpSeries> result = adapter.findAllActive();

        assertEquals(2, result.size());
        List<Long> resultIds = result.stream().map(IpSeries::getId).toList();
        assertTrue(resultIds.contains(active1.getId()));
        assertTrue(resultIds.contains(active2.getId()));
    }

    @Test
    @DisplayName("findAllActive → 按 ID 升序排列")
    void shouldReturnSortedByIdAsc() {
        IpSeriesPO first = persistIpSeries("CODE-A", "系列A", IpSeriesStatus.ACTIVE);
        IpSeriesPO second = persistIpSeries("CODE-B", "系列B", IpSeriesStatus.ACTIVE);
        IpSeriesPO third = persistIpSeries("CODE-C", "系列C", IpSeriesStatus.ACTIVE);

        entityManager.flush();
        entityManager.clear();

        List<IpSeries> result = adapter.findAllActive();

        assertEquals(3, result.size());
        assertEquals(first.getId(), result.get(0).getId());
        assertEquals(second.getId(), result.get(1).getId());
        assertEquals(third.getId(), result.get(2).getId());
    }

    @Test
    @DisplayName("findAllActive → 空表返回空 List")
    void shouldReturnEmptyListWhenNoActiveIpSeries() {
        List<IpSeries> result = adapter.findAllActive();

        assertTrue(result.isEmpty());
    }

    private IpSeriesPO persistIpSeries(String code, String name, IpSeriesStatus status) {
        IpSeriesPO po = IpSeriesPO.builder()
                .code(code)
                .name(name)
                .status(status)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return entityManager.persistFlushFind(po);
    }
}
