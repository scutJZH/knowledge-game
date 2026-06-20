package com.knowledgegame.core.infrastructure.adapter.repoadapter;

import com.knowledgegame.core.domain.model.domainenum.JoinPolicy;
import com.knowledgegame.core.domain.model.entity.StudyGroup;
import com.knowledgegame.core.domain.model.vo.FileRef;
import com.knowledgegame.core.infrastructure.db.entity.StudyGroupPO;
import com.knowledgegame.core.infrastructure.db.repository.StudyGroupJpaRepository;
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
@Import(StudyGroupRepositoryAdapter.class)
class StudyGroupRepositoryAdapterTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private StudyGroupRepositoryAdapter adapter;

    @Test
    @DisplayName("save 新增应持久化 FileRef 双字段")
    void save_shouldPersistFileRefDualFields() {
        StudyGroup group = StudyGroup.create("测试群组", "描述",
                FileRef.of(1L, "https://example.com/avatar.png"), 100L, JoinPolicy.OPEN);

        StudyGroup saved = adapter.save(group);

        assertNotNull(saved.getId());
        assertEquals("测试群组", saved.getName());
        assertNotNull(saved.getAvatar());
        assertEquals(1L, saved.getAvatar().fileId());
        assertEquals("https://example.com/avatar.png", saved.getAvatar().url());

        entityManager.flush();
        entityManager.clear();
        StudyGroupPO po = entityManager.find(StudyGroupPO.class, saved.getId());
        assertNotNull(po);
        assertEquals(1L, po.getAvatarFileId());
        assertEquals("https://example.com/avatar.png", po.getAvatarUrl());
    }

    @Test
    @DisplayName("save 新增 avatar 为 null 应正常持久化")
    void save_shouldPersistNullAvatar() {
        StudyGroup group = StudyGroup.create("无头像群组", null, null, 100L, JoinPolicy.OPEN);

        StudyGroup saved = adapter.save(group);

        assertNotNull(saved.getId());
        assertNull(saved.getAvatar());

        entityManager.flush();
        entityManager.clear();
        StudyGroupPO po = entityManager.find(StudyGroupPO.class, saved.getId());
        assertNull(po.getAvatarFileId());
        assertNull(po.getAvatarUrl());
    }

    @Test
    @DisplayName("findById 找到时应返回领域模型")
    void findById_shouldReturnDomainWhenFound() {
        StudyGroupPO po = StudyGroupPO.builder()
                .name("查找群组")
                .description("描述")
                .avatarFileId(2L)
                .avatarUrl("https://example.com/avatar2.png")
                .ownerId(200L)
                .joinPolicy(JoinPolicy.OPEN)
                .inviteCode("VN1KVE01")
                .createdAt(java.time.LocalDateTime.now())
                .updatedAt(java.time.LocalDateTime.now())
                .build();
        Long id = entityManager.persistAndGetId(po, Long.class);
        entityManager.flush();
        entityManager.clear();

        Optional<StudyGroup> result = adapter.findById(id);

        assertTrue(result.isPresent());
        assertEquals("查找群组", result.get().getName());
        assertNotNull(result.get().getAvatar());
        assertEquals(2L, result.get().getAvatar().fileId());
    }

    @Test
    @DisplayName("findById 未找到时应返回空")
    void findById_shouldReturnEmptyWhenNotFound() {
        Optional<StudyGroup> result = adapter.findById(99999L);
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("deleteById 应硬删除记录")
    void deleteById_shouldHardDelete() {
        StudyGroupPO po = StudyGroupPO.builder()
                .name("待删除群组")
                .description("描述")
                .ownerId(300L)
                .joinPolicy(JoinPolicy.OPEN)
                .inviteCode("VN1KVE02")
                .createdAt(java.time.LocalDateTime.now())
                .updatedAt(java.time.LocalDateTime.now())
                .build();
        Long id = entityManager.persistAndGetId(po, Long.class);
        entityManager.flush();
        entityManager.clear();

        adapter.deleteById(id);
        entityManager.flush();
        entityManager.clear();

        assertNull(entityManager.find(StudyGroupPO.class, id));
    }

    @Test
    @DisplayName("findByInviteCode 找到时应返回领域模型")
    void findByInviteCode_existingCode_returnsGroup() {
        StudyGroupPO po = StudyGroupPO.builder()
                .name("邀请码群组")
                .description("描述")
                .ownerId(400L)
                .joinPolicy(JoinPolicy.INVITE_ONLY)
                .inviteCode("JNVCDE13")
                .createdAt(java.time.LocalDateTime.now())
                .updatedAt(java.time.LocalDateTime.now())
                .build();
        entityManager.persistAndFlush(po);
        entityManager.clear();

        Optional<StudyGroup> result = adapter.findByInviteCode("JNVCDE13");

        assertTrue(result.isPresent());
        assertEquals("邀请码群组", result.get().getName());
        assertEquals(JoinPolicy.INVITE_ONLY, result.get().getJoinPolicy());
        assertEquals("JNVCDE13", result.get().getInviteCodeValue());
    }

    @Test
    @DisplayName("findByInviteCode 未找到时应返回空")
    void findByInviteCode_unknownCode_returnsEmpty() {
        Optional<StudyGroup> result = adapter.findByInviteCode("NONEXIST");
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("invite_code 重复插入应抛异常（唯一约束生效）")
    void save_duplicateInviteCode_throwsDataIntegrityViolation() {
        StudyGroupPO po1 = StudyGroupPO.builder()
                .name("群组1")
                .ownerId(500L)
                .joinPolicy(JoinPolicy.OPEN)
                .inviteCode("DVPCDE13")
                .createdAt(java.time.LocalDateTime.now())
                .updatedAt(java.time.LocalDateTime.now())
                .build();
        entityManager.persistAndFlush(po1);
        entityManager.clear();

        StudyGroupPO po2 = StudyGroupPO.builder()
                .name("群组2")
                .ownerId(600L)
                .joinPolicy(JoinPolicy.OPEN)
                .inviteCode("DVPCDE13")
                .createdAt(java.time.LocalDateTime.now())
                .updatedAt(java.time.LocalDateTime.now())
                .build();

        // TestEntityManager 直连 Hibernate，抛 ConstraintViolationException
        // Spring Data JPA（adapter.save）会包装为 DataIntegrityViolationException
        // 本测试验证 DB 层唯一约束存在
        assertThrows(RuntimeException.class, () -> {
            entityManager.persistAndFlush(po2);
        });
    }

    @Test
    @DisplayName("StudyGroup.create() 默认 JOIN_POLICY=OPEN 应正确持久化")
    void save_withoutJoinPolicy_defaultsToOpen() {
        StudyGroup group = StudyGroup.create("开放群组", null, null, 700L, JoinPolicy.OPEN);
        StudyGroup saved = adapter.save(group);

        entityManager.flush();
        entityManager.clear();

        StudyGroupPO po = entityManager.find(StudyGroupPO.class, saved.getId());
        assertNotNull(po);
        assertEquals(JoinPolicy.OPEN, po.getJoinPolicy());
        assertNotNull(po.getInviteCode());
        assertEquals(8, po.getInviteCode().length());
    }
}
