package com.knowledgegame.core.infrastructure.adapter.repoadapter;

import com.knowledgegame.core.domain.model.domainenum.GroupRole;
import com.knowledgegame.core.domain.model.entity.GroupMember;
import com.knowledgegame.core.infrastructure.db.entity.GroupMemberPO;
import com.knowledgegame.core.infrastructure.db.repository.GroupMemberJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@EntityScan(basePackages = "com.knowledgegame.core.infrastructure.db.entity")
@EnableJpaRepositories(basePackages = "com.knowledgegame.core.infrastructure.db.repository")
@Import(GroupMemberRepositoryAdapter.class)
class GroupMemberRepositoryAdapterTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private GroupMemberRepositoryAdapter adapter;

    @Test
    @DisplayName("save 新增 OWNER 记录应正确持久化")
    void save_shouldPersistOwnerRecord() {
        GroupMember member = GroupMember.createOwner(10L, 100L);

        GroupMember saved = adapter.save(member);

        assertNotNull(saved.getId());
        assertEquals(10L, saved.getGroupId());
        assertEquals(100L, saved.getUserId());
        assertEquals(GroupRole.OWNER, saved.getRole());
        assertEquals(0, saved.getPoints());
        assertNotNull(saved.getJoinedAt());

        entityManager.flush();
        entityManager.clear();
        GroupMemberPO po = entityManager.find(GroupMemberPO.class, saved.getId());
        assertNotNull(po);
        assertEquals(GroupRole.OWNER, po.getRole());
        assertEquals(0, po.getPoints());
    }

    @Test
    @DisplayName("UNIQUE(group_id, user_id) 重复插入应抛 DataIntegrityViolationException")
    void shouldThrowOnDuplicateGroupUser() {
        GroupMember first = GroupMember.createOwner(20L, 200L);
        adapter.save(first);
        entityManager.flush();
        entityManager.clear();

        GroupMember duplicate = GroupMember.createOwner(20L, 200L);
        assertThrows(DataIntegrityViolationException.class, () -> {
            adapter.save(duplicate);
        });
    }

    @Test
    @DisplayName("existsByGroupIdAndUserId 存在时应返回 true")
    void existsByGroupIdAndUserId_shouldReturnTrue() {
        GroupMemberPO po = GroupMemberPO.builder()
                .groupId(30L)
                .userId(300L)
                .role(GroupRole.OWNER)
                .points(0)
                .joinedAt(LocalDateTime.now())
                .build();
        entityManager.persistAndFlush(po);
        entityManager.clear();

        assertTrue(adapter.existsByGroupIdAndUserId(30L, 300L));
    }

    @Test
    @DisplayName("existsByGroupIdAndUserId 不存在时应返回 false")
    void existsByGroupIdAndUserId_shouldReturnFalse() {
        assertFalse(adapter.existsByGroupIdAndUserId(99999L, 99999L));
    }

    @Test
    @DisplayName("findByGroupIdAndUserId 找到时应返回领域模型")
    void findByGroupIdAndUserId_shouldReturnDomainWhenFound() {
        GroupMemberPO po = GroupMemberPO.builder()
                .groupId(40L)
                .userId(400L)
                .role(GroupRole.ADMIN)
                .points(50)
                .joinedAt(LocalDateTime.now())
                .build();
        entityManager.persistAndFlush(po);
        entityManager.clear();

        Optional<GroupMember> result = adapter.findByGroupIdAndUserId(40L, 400L);

        assertTrue(result.isPresent());
        assertEquals(40L, result.get().getGroupId());
        assertEquals(400L, result.get().getUserId());
        assertEquals(GroupRole.ADMIN, result.get().getRole());
        assertEquals(50, result.get().getPoints());
    }

    @Test
    @DisplayName("findByGroupIdAndUserId 未找到时应返回空")
    void findByGroupIdAndUserId_shouldReturnEmptyWhenNotFound() {
        Optional<GroupMember> result = adapter.findByGroupIdAndUserId(99999L, 99999L);
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("deleteByGroupIdAndUserId 存在时应在数据库移除记录")
    void deleteByGroupIdAndUserId_existing_removesRecord() {
        GroupMemberPO po = GroupMemberPO.builder()
                .groupId(50L)
                .userId(500L)
                .role(GroupRole.MEMBER)
                .points(0)
                .joinedAt(LocalDateTime.now())
                .build();
        entityManager.persistAndFlush(po);
        entityManager.clear();

        adapter.deleteByGroupIdAndUserId(50L, 500L);
        entityManager.flush();
        entityManager.clear();

        assertNull(entityManager.find(GroupMemberPO.class, po.getId()));
    }

    @Test
    @DisplayName("deleteByGroupIdAndUserId 不存在时应无副作用")
    void deleteByGroupIdAndUserId_nonExisting_noSideEffect() {
        // 不应抛异常
        adapter.deleteByGroupIdAndUserId(99999L, 99999L);
    }

    @Test
    @DisplayName("findById 找到时应返回领域模型")
    void findById_shouldReturnDomainWhenFound() {
        GroupMemberPO po = GroupMemberPO.builder()
                .groupId(60L)
                .userId(600L)
                .role(GroupRole.MEMBER)
                .points(10)
                .joinedAt(LocalDateTime.now())
                .build();
        entityManager.persistAndFlush(po);
        entityManager.clear();

        Optional<GroupMember> result = adapter.findById(po.getId());

        assertTrue(result.isPresent());
        assertEquals(po.getId(), result.get().getId());
        assertEquals(60L, result.get().getGroupId());
        assertEquals(600L, result.get().getUserId());
        assertEquals(GroupRole.MEMBER, result.get().getRole());
    }

    @Test
    @DisplayName("findById 未找到时应返回空")
    void findById_shouldReturnEmptyWhenNotFound() {
        Optional<GroupMember> result = adapter.findById(99999L);
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("findByUserIdOrderByJoinedAtDesc 应按加入时间降序返回用户所有成员记录")
    void findByUserIdOrderByJoinedAtDesc_shouldReturnMembersOrderedByJoinedAtDesc() {
        LocalDateTime t1 = LocalDateTime.now().minusDays(3);
        LocalDateTime t2 = LocalDateTime.now().minusDays(1);
        LocalDateTime t3 = LocalDateTime.now();

        entityManager.persistAndFlush(GroupMemberPO.builder()
                .groupId(70L).userId(700L).role(GroupRole.OWNER).points(0).joinedAt(t1).build());
        entityManager.persistAndFlush(GroupMemberPO.builder()
                .groupId(80L).userId(700L).role(GroupRole.MEMBER).points(0).joinedAt(t3).build());
        entityManager.persistAndFlush(GroupMemberPO.builder()
                .groupId(90L).userId(700L).role(GroupRole.ADMIN).points(0).joinedAt(t2).build());
        // 另一个用户的记录不应出现在结果中
        entityManager.persistAndFlush(GroupMemberPO.builder()
                .groupId(80L).userId(999L).role(GroupRole.MEMBER).points(0).joinedAt(t1).build());
        entityManager.flush();
        entityManager.clear();

        List<GroupMember> result = adapter.findByUserIdOrderByJoinedAtDesc(700L);

        assertEquals(3, result.size());
        assertEquals(80L, result.get(0).getGroupId());  // t3 最新
        assertEquals(90L, result.get(1).getGroupId());  // t2
        assertEquals(70L, result.get(2).getGroupId());  // t1 最旧
    }

    @Test
    @DisplayName("countByGroupIdIn 应正确统计各群组成员数")
    void countByGroupIdIn_shouldReturnCounts() {
        entityManager.persistAndFlush(GroupMemberPO.builder()
                .groupId(100L).userId(1L).role(GroupRole.MEMBER).points(0).joinedAt(LocalDateTime.now()).build());
        entityManager.persistAndFlush(GroupMemberPO.builder()
                .groupId(100L).userId(2L).role(GroupRole.MEMBER).points(0).joinedAt(LocalDateTime.now()).build());
        entityManager.persistAndFlush(GroupMemberPO.builder()
                .groupId(110L).userId(3L).role(GroupRole.OWNER).points(0).joinedAt(LocalDateTime.now()).build());
        entityManager.flush();
        entityManager.clear();

        Map<Long, Integer> counts = adapter.countByGroupIdIn(List.of(100L, 110L, 999L));

        assertEquals(3, counts.size());
        assertEquals(2, counts.get(100L));
        assertEquals(1, counts.get(110L));
        assertEquals(0, counts.get(999L));
    }

    @Test
    @DisplayName("countByGroupIdIn 空列表应返回空 Map")
    void countByGroupIdIn_emptyList_returnsEmptyMap() {
        Map<Long, Integer> counts = adapter.countByGroupIdIn(List.of());
        assertTrue(counts.isEmpty());
    }
}
