package com.knowledgegame.core.domain.model.entity;

import com.knowledgegame.core.domain.model.domainenum.GroupRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
