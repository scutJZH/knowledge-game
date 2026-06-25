package com.knowledgegame.core.domain.model.entity;

import com.knowledgegame.core.domain.model.domainenum.JoinPolicy;
import com.knowledgegame.core.domain.model.domainenum.StudyGroupStatus;
import com.knowledgegame.core.domain.model.vo.FileRef;
import com.knowledgegame.core.domain.model.vo.InviteCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudyGroupTest {

    private static final FileRef AVATAR = FileRef.of(1L, "https://example.com/avatar.png");

    @Nested
    @DisplayName("create（工厂方法）")
    class CreateTests {

        @Test
        @DisplayName("create() 应正确设置所有字段")
        void create_shouldSetAllFields() {
            StudyGroup group = StudyGroup.create("测试群组", "描述", AVATAR, 100L, JoinPolicy.OPEN);

            assertEquals("测试群组", group.getName());
            assertEquals("描述", group.getDescription());
            assertEquals(AVATAR, group.getAvatar());
            assertEquals(100L, group.getOwnerId());
        }

        @Test
        @DisplayName("create() description 为 null 应正常创建")
        void create_shouldAllowNullDescription() {
            StudyGroup group = StudyGroup.create("群组", null, AVATAR, 100L, JoinPolicy.OPEN);

            assertNull(group.getDescription());
        }

        @Test
        @DisplayName("create() avatar 为 null 应正常创建")
        void create_shouldAllowNullAvatar() {
            StudyGroup group = StudyGroup.create("群组", "描述", null, 100L, JoinPolicy.OPEN);

            assertNull(group.getAvatar());
        }

        @Test
        @DisplayName("create() id 应为 null，createdAt/updatedAt 非空")
        void create_shouldSetTimestampsAndIdNull() {
            LocalDateTime before = LocalDateTime.now();
            StudyGroup group = StudyGroup.create("群组", "描述", AVATAR, 100L, JoinPolicy.OPEN);
            LocalDateTime after = LocalDateTime.now();

            assertNull(group.getId());
            assertNotNull(group.getCreatedAt());
            assertNotNull(group.getUpdatedAt());
            assertEquals(false, group.getCreatedAt().isBefore(before));
            assertEquals(false, group.getCreatedAt().isAfter(after));
        }

        @Test
        @DisplayName("create() JOIN_POLICY=OPEN 应正确设置")
        void create_withJoinPolicyOpen_returnsOpenPolicy() {
            StudyGroup group = StudyGroup.create("群组", null, null, 100L, JoinPolicy.OPEN);

            assertEquals(JoinPolicy.OPEN, group.getJoinPolicy());
        }

        @Test
        @DisplayName("create() JOIN_POLICY=INVITE_ONLY 应正确设置")
        void create_withJoinPolicyInviteOnly_returnsInviteOnlyPolicy() {
            StudyGroup group = StudyGroup.create("群组", null, null, 100L, JoinPolicy.INVITE_ONLY);

            assertEquals(JoinPolicy.INVITE_ONLY, group.getJoinPolicy());
        }

        @Test
        @DisplayName("create() 应自动生成 8 位 Crockford Base32 邀请码")
        void create_generatesInviteCode8Chars() {
            StudyGroup group = StudyGroup.create("群组", null, null, 100L, JoinPolicy.OPEN);

            assertNotNull(group.getInviteCode());
            String code = group.getInviteCodeValue();
            assertEquals(8, code.length());
            // 验证所有字符在 Crockford 字符集内
            String crockfordChars = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
            for (char c : code.toCharArray()) {
                assertTrue(crockfordChars.indexOf(c) >= 0,
                        "非法 Crockford 字符: " + c);
            }
        }
    }

    @Nested
    @DisplayName("reconstruct（持久化重建）")
    class ReconstructTests {

        @Test
        @DisplayName("reconstruct() 应完整还原所有字段")
        void reconstruct_shouldRestoreAllFields() {
            LocalDateTime created = LocalDateTime.of(2025, 3, 15, 10, 0, 0);
            LocalDateTime updated = LocalDateTime.of(2025, 6, 1, 15, 30, 0);
            InviteCode inviteCode = InviteCode.of("ABC12345");

            StudyGroup group = StudyGroup.reconstruct(
                    99L, "重建群组", "重建描述", AVATAR, 100L,
                    StudyGroupStatus.ACTIVE, JoinPolicy.OPEN, inviteCode, created, updated);

            assertEquals(99L, group.getId());
            assertEquals("重建群组", group.getName());
            assertEquals("重建描述", group.getDescription());
            assertEquals(AVATAR, group.getAvatar());
            assertEquals(100L, group.getOwnerId());
            assertEquals(JoinPolicy.OPEN, group.getJoinPolicy());
            assertEquals("ABC12345", group.getInviteCodeValue());
            assertEquals(created, group.getCreatedAt());
            assertEquals(updated, group.getUpdatedAt());
        }

        @Test
        @DisplayName("reconstruct() avatar 为 null 应正常还原")
        void reconstruct_shouldAllowNullAvatar() {
            InviteCode inviteCode = InviteCode.of("XYZ67890");
            StudyGroup group = StudyGroup.reconstruct(
                    1L, "群组", null, null, 100L,
                    StudyGroupStatus.ACTIVE, JoinPolicy.INVITE_ONLY, inviteCode,
                    LocalDateTime.of(2025, 1, 1, 0, 0),
                    LocalDateTime.of(2025, 1, 1, 0, 0));

            assertNull(group.getAvatar());
            assertNull(group.getDescription());
            assertEquals(JoinPolicy.INVITE_ONLY, group.getJoinPolicy());
            assertEquals("XYZ67890", group.getInviteCodeValue());
        }

        @Test
        @DisplayName("reconstruct() 应保留 joinPolicy 和 inviteCode")
        void reconstruct_preservesJoinPolicyAndInviteCode() {
            InviteCode inviteCode = InviteCode.of("TEST1234");
            StudyGroup group = StudyGroup.reconstruct(
                    1L, "群组", null, null, 100L,
                    StudyGroupStatus.ACTIVE, JoinPolicy.INVITE_ONLY, inviteCode,
                    LocalDateTime.of(2025, 1, 1, 0, 0),
                    LocalDateTime.of(2025, 1, 1, 0, 0));

            assertEquals(JoinPolicy.INVITE_ONLY, group.getJoinPolicy());
            assertEquals(inviteCode, group.getInviteCode());
        }
    }

    @Nested
    @DisplayName("策略判断方法")
    class PolicyCheckTests {

        @Test
        @DisplayName("isJoinPolicyOpen() OPEN 时返回 true")
        void isJoinPolicyOpen_trueWhenOpen() {
            StudyGroup group = StudyGroup.create("群组", null, null, 100L, JoinPolicy.OPEN);
            assertTrue(group.isJoinPolicyOpen());
        }

        @Test
        @DisplayName("isJoinPolicyInviteOnly() INVITE_ONLY 时返回 true")
        void isJoinPolicyInviteOnly_trueWhenInviteOnly() {
            StudyGroup group = StudyGroup.create("群组", null, null, 100L, JoinPolicy.INVITE_ONLY);
            assertTrue(group.isJoinPolicyInviteOnly());
        }
    }

    @Nested
    @DisplayName("regenerateInviteCode")
    class RegenerateInviteCodeTests {

        @Test
        @DisplayName("regenerateInviteCode() 应生成不同的邀请码")
        void regenerateInviteCode_newCodeDiffersFromOld() {
            InviteCode oldCode = InviteCode.of("ABC12345");
            StudyGroup group = StudyGroup.reconstruct(
                    1L, "群组", null, null, 100L,
                    StudyGroupStatus.ACTIVE, JoinPolicy.OPEN, oldCode,
                    LocalDateTime.of(2025, 1, 1, 0, 0),
                    LocalDateTime.of(2025, 1, 1, 0, 0));

            group.regenerateInviteCode();

            assertNotNull(group.getInviteCode());
            assertEquals(false, oldCode.equals(group.getInviteCode()));
        }

        @Test
        @DisplayName("regenerateInviteCode() 应更新 updatedAt")
        void regenerateInviteCode_updatesUpdatedAt() {
            LocalDateTime oldUpdatedAt = LocalDateTime.of(2025, 1, 1, 0, 0);
            InviteCode oldCode = InviteCode.of("ABC12345");
            StudyGroup group = StudyGroup.reconstruct(
                    1L, "群组", null, null, 100L,
                    StudyGroupStatus.ACTIVE, JoinPolicy.OPEN, oldCode,
                    LocalDateTime.of(2025, 1, 1, 0, 0),
                    oldUpdatedAt);

            group.regenerateInviteCode();

            assertNotNull(group.getUpdatedAt());
            // updatedAt 应该晚于旧值
            assertTrue(group.getUpdatedAt().isAfter(oldUpdatedAt));
        }
    }

    @Nested
    @DisplayName("updateOwner")
    class UpdateOwnerTests {

        @Test
        @DisplayName("updateOwner() 应更新 ownerId 和 updatedAt")
        void updateOwner_updatesOwnerIdAndTimestamp() {
            LocalDateTime oldUpdatedAt = LocalDateTime.of(2025, 1, 1, 0, 0);
            StudyGroup group = StudyGroup.reconstruct(
                    1L, "群组", null, null, 100L,
                    StudyGroupStatus.ACTIVE, JoinPolicy.OPEN, InviteCode.of("ABC12345"),
                    LocalDateTime.of(2025, 1, 1, 0, 0),
                    oldUpdatedAt);

            group.updateOwner(200L);

            assertEquals(200L, group.getOwnerId());
            assertTrue(group.getUpdatedAt().isAfter(oldUpdatedAt));
        }
    }
}
