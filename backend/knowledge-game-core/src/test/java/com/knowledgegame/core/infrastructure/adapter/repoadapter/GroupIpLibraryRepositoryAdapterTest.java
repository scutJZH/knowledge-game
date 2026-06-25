package com.knowledgegame.core.infrastructure.adapter.repoadapter;

import com.knowledgegame.core.domain.model.entity.GroupIpLibrary;
import com.knowledgegame.core.infrastructure.db.entity.GroupIpLibraryPO;
import com.knowledgegame.core.infrastructure.db.repository.GroupIpLibraryJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@EntityScan(basePackages = "com.knowledgegame.core.infrastructure.db.entity")
@EnableJpaRepositories(basePackages = "com.knowledgegame.core.infrastructure.db.repository")
@Import(GroupIpLibraryRepositoryAdapter.class)
class GroupIpLibraryRepositoryAdapterTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private GroupIpLibraryRepositoryAdapter adapter;

    @Test
    @DisplayName("save 新增记录应正确持久化")
    void save_shouldPersistRecord() {
        GroupIpLibrary item = GroupIpLibrary.create(1L, 10L);

        GroupIpLibrary saved = adapter.save(item);

        assertNotNull(saved.getId());
        assertEquals(1L, saved.getGroupId());
        assertEquals(10L, saved.getIpSeriesId());
        assertNotNull(saved.getAddedAt());

        entityManager.flush();
        entityManager.clear();
        GroupIpLibraryPO po = entityManager.find(GroupIpLibraryPO.class, saved.getId());
        assertNotNull(po);
        assertEquals(1L, po.getGroupId());
        assertEquals(10L, po.getIpSeriesId());
    }

    @Test
    @DisplayName("UNIQUE(group_id, ip_series_id) 重复插入应抛 DataIntegrityViolationException")
    void shouldThrowOnDuplicateGroupIpSeries() {
        GroupIpLibrary first = GroupIpLibrary.create(20L, 200L);
        adapter.save(first);
        entityManager.flush();
        entityManager.clear();

        GroupIpLibrary duplicate = GroupIpLibrary.create(20L, 200L);
        assertThrows(DataIntegrityViolationException.class, () -> {
            adapter.save(duplicate);
        });
    }

    @Test
    @DisplayName("saveAll 批量新增应全部持久化")
    void saveAll_shouldPersistAllRecords() {
        GroupIpLibrary item1 = GroupIpLibrary.create(30L, 300L);
        GroupIpLibrary item2 = GroupIpLibrary.create(30L, 301L);

        List<GroupIpLibrary> saved = adapter.saveAll(List.of(item1, item2));

        assertEquals(2, saved.size());
        assertNotNull(saved.get(0).getId());
        assertNotNull(saved.get(1).getId());

        entityManager.flush();
        entityManager.clear();
        List<GroupIpLibraryPO> poList = entityManager.getEntityManager()
                .createQuery("SELECT g FROM GroupIpLibraryPO g WHERE g.groupId = 30L", GroupIpLibraryPO.class)
                .getResultList();
        assertEquals(2, poList.size());
    }

    @Test
    @DisplayName("findByGroupId 应返回群组全部关联")
    void findByGroupId_shouldReturnAllItems() {
        entityManager.persistAndFlush(
                new GroupIpLibraryPO().setGroupId(40L).setIpSeriesId(400L).setAddedAt(LocalDateTime.now()));
        entityManager.persistAndFlush(
                new GroupIpLibraryPO().setGroupId(40L).setIpSeriesId(401L).setAddedAt(LocalDateTime.now()));
        entityManager.persistAndFlush(
                new GroupIpLibraryPO().setGroupId(41L).setIpSeriesId(402L).setAddedAt(LocalDateTime.now()));
        entityManager.clear();

        List<GroupIpLibrary> result = adapter.findByGroupId(40L);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(i -> i.getIpSeriesId() == 400L));
        assertTrue(result.stream().anyMatch(i -> i.getIpSeriesId() == 401L));
    }

    @Test
    @DisplayName("findByGroupId 无结果返回空列表")
    void findByGroupId_emptyShouldReturnEmptyList() {
        assertTrue(adapter.findByGroupId(999L).isEmpty());
    }

    @Test
    @DisplayName("existsByGroupIdAndIpSeriesId 存在返回 true")
    void existsByGroupIdAndIpSeriesId_shouldReturnTrue() {
        entityManager.persistAndFlush(
                new GroupIpLibraryPO().setGroupId(50L).setIpSeriesId(500L).setAddedAt(LocalDateTime.now()));
        entityManager.clear();

        assertTrue(adapter.existsByGroupIdAndIpSeriesId(50L, 500L));
    }

    @Test
    @DisplayName("existsByGroupIdAndIpSeriesId 不存在返回 false")
    void existsByGroupIdAndIpSeriesId_shouldReturnFalse() {
        assertFalse(adapter.existsByGroupIdAndIpSeriesId(999L, 999L));
    }

    @Test
    @DisplayName("deleteByGroupIdAndIpSeriesIdIn 应批量删除指定 IP")
    void deleteByGroupIdAndIpSeriesIdIn_shouldDeleteSpecifiedItems() {
        entityManager.persistAndFlush(
                new GroupIpLibraryPO().setGroupId(60L).setIpSeriesId(600L).setAddedAt(LocalDateTime.now()));
        entityManager.persistAndFlush(
                new GroupIpLibraryPO().setGroupId(60L).setIpSeriesId(601L).setAddedAt(LocalDateTime.now()));
        entityManager.persistAndFlush(
                new GroupIpLibraryPO().setGroupId(60L).setIpSeriesId(602L).setAddedAt(LocalDateTime.now()));
        entityManager.clear();

        adapter.deleteByGroupIdAndIpSeriesIdIn(60L, List.of(600L, 601L));
        entityManager.flush();
        entityManager.clear();

        List<GroupIpLibrary> remaining = adapter.findByGroupId(60L);
        assertEquals(1, remaining.size());
        assertEquals(602L, remaining.get(0).getIpSeriesId());
    }

    @Test
    @DisplayName("deleteAllByGroupId 应删除群组全部关联")
    void deleteAllByGroupId_shouldDeleteAllItems() {
        entityManager.persistAndFlush(
                new GroupIpLibraryPO().setGroupId(70L).setIpSeriesId(700L).setAddedAt(LocalDateTime.now()));
        entityManager.persistAndFlush(
                new GroupIpLibraryPO().setGroupId(70L).setIpSeriesId(701L).setAddedAt(LocalDateTime.now()));
        entityManager.clear();

        adapter.deleteAllByGroupId(70L);
        entityManager.flush();
        entityManager.clear();

        assertTrue(adapter.findByGroupId(70L).isEmpty());
    }
}
