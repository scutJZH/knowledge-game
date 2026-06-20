package com.knowledgegame.core.domain.model.entity;

import com.knowledgegame.core.domain.model.domainenum.GroupRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class GroupMemberTest {

    @Test
    @DisplayName("createOwner() 应创建 role=OWNER / points=0 的成员记录")
    void createOwner_shouldSetOwnerRoleAndZeroPoints() {
        GroupMember member = GroupMember.createOwner(10L, 100L);

        assertEquals(10L, member.getGroupId());
        assertEquals(100L, member.getUserId());
        assertEquals(GroupRole.OWNER, member.getRole());
        assertEquals(0, member.getPoints());
    }

    @Test
    @DisplayName("createOwner() id 应为 null，joinedAt 非空")
    void createOwner_shouldSetJoinedAtAndIdNull() {
        LocalDateTime before = LocalDateTime.now();
        GroupMember member = GroupMember.createOwner(10L, 100L);
        LocalDateTime after = LocalDateTime.now();

        assertNull(member.getId());
        assertNotNull(member.getJoinedAt());
        assertEquals(false, member.getJoinedAt().isBefore(before));
        assertEquals(false, member.getJoinedAt().isAfter(after));
    }

    @Test
    @DisplayName("reconstruct() 应完整还原所有字段")
    void reconstruct_shouldRestoreAllFields() {
        LocalDateTime joinedAt = LocalDateTime.of(2025, 3, 15, 10, 0, 0);

        GroupMember member = GroupMember.reconstruct(
                99L, 10L, 100L, GroupRole.ADMIN, 50, joinedAt);

        assertEquals(99L, member.getId());
        assertEquals(10L, member.getGroupId());
        assertEquals(100L, member.getUserId());
        assertEquals(GroupRole.ADMIN, member.getRole());
        assertEquals(50, member.getPoints());
        assertEquals(joinedAt, member.getJoinedAt());
    }

    @Test
    @DisplayName("joinAsMember() 应创建 role=MEMBER / points=0 的成员记录")
    void joinAsMember_returnsMemberRoleZeroPoints() {
        GroupMember member = GroupMember.joinAsMember(10L, 100L);

        assertEquals(10L, member.getGroupId());
        assertEquals(100L, member.getUserId());
        assertEquals(GroupRole.MEMBER, member.getRole());
        assertEquals(0, member.getPoints());
        assertNotNull(member.getJoinedAt());
        assertNull(member.getId());
    }

    @Nested
    @DisplayName("promoteToAdmin")
    class PromoteToAdminTests {

        @Test
        @DisplayName("MEMBER → ADMIN 角色应变为 ADMIN")
        void memberPromotesToAdmin_roleBecomesAdmin() {
            GroupMember member = GroupMember.reconstruct(1L, 10L, 100L,
                    GroupRole.MEMBER, 0, LocalDateTime.now());
            member.promoteToAdmin();
            assertEquals(GroupRole.ADMIN, member.getRole());
        }

        @Test
        @DisplayName("ADMIN 幂等调用应保持 ADMIN")
        void adminPromotesToAdmin_isIdempotent() {
            GroupMember member = GroupMember.reconstruct(1L, 10L, 100L,
                    GroupRole.ADMIN, 0, LocalDateTime.now());
            member.promoteToAdmin();
            assertEquals(GroupRole.ADMIN, member.getRole());
        }

        @Test
        @DisplayName("OWNER 调用应抛 IllegalStateException")
        void ownerCannotPromote_throwsIllegalStateException() {
            GroupMember owner = GroupMember.reconstruct(1L, 10L, 100L,
                    GroupRole.OWNER, 0, LocalDateTime.now());
            assertThrows(IllegalStateException.class, owner::promoteToAdmin);
        }
    }

    @Nested
    @DisplayName("demoteToMember")
    class DemoteToMemberTests {

        @Test
        @DisplayName("ADMIN → MEMBER 角色应变为 MEMBER")
        void adminDemotesToMember_roleBecomesMember() {
            GroupMember member = GroupMember.reconstruct(1L, 10L, 100L,
                    GroupRole.ADMIN, 0, LocalDateTime.now());
            member.demoteToMember();
            assertEquals(GroupRole.MEMBER, member.getRole());
        }

        @Test
        @DisplayName("MEMBER 幂等调用应保持 MEMBER")
        void memberDemotesToMember_isIdempotent() {
            GroupMember member = GroupMember.reconstruct(1L, 10L, 100L,
                    GroupRole.MEMBER, 0, LocalDateTime.now());
            member.demoteToMember();
            assertEquals(GroupRole.MEMBER, member.getRole());
        }

        @Test
        @DisplayName("OWNER 调用应抛 IllegalStateException")
        void ownerCannotDemote_throwsIllegalStateException() {
            GroupMember owner = GroupMember.reconstruct(1L, 10L, 100L,
                    GroupRole.OWNER, 0, LocalDateTime.now());
            assertThrows(IllegalStateException.class, owner::demoteToMember);
        }
    }

    @Nested
    @DisplayName("transferOwnershipTo")
    class TransferOwnershipToTests {

        @Test
        @DisplayName("OWNER 转让后原 OWNER 变 ADMIN，目标变 OWNER")
        void ownerTransfersToMember_rolesSwap() {
            GroupMember owner = GroupMember.reconstruct(1L, 10L, 100L,
                    GroupRole.OWNER, 0, LocalDateTime.now());
            GroupMember target = GroupMember.reconstruct(2L, 10L, 200L,
                    GroupRole.MEMBER, 0, LocalDateTime.now());

            owner.transferOwnershipTo(target);

            assertEquals(GroupRole.ADMIN, owner.getRole());
            assertEquals(GroupRole.OWNER, target.getRole());
        }

        @Test
        @DisplayName("非 OWNER 转让应抛 IllegalStateException")
        void nonOwnerTransfers_throwsIllegalStateException() {
            GroupMember admin = GroupMember.reconstruct(1L, 10L, 100L,
                    GroupRole.ADMIN, 0, LocalDateTime.now());
            GroupMember target = GroupMember.reconstruct(2L, 10L, 200L,
                    GroupRole.MEMBER, 0, LocalDateTime.now());

            assertThrows(IllegalStateException.class, () -> admin.transferOwnershipTo(target));
        }

        @Test
        @DisplayName("转让给不同群组成员应抛 IllegalStateException")
        void transferToDifferentGroup_throwsIllegalStateException() {
            GroupMember owner = GroupMember.reconstruct(1L, 10L, 100L,
                    GroupRole.OWNER, 0, LocalDateTime.now());
            GroupMember otherGroup = GroupMember.reconstruct(2L, 20L, 200L,
                    GroupRole.MEMBER, 0, LocalDateTime.now());

            assertThrows(IllegalStateException.class, () -> owner.transferOwnershipTo(otherGroup));
        }
    }
}
