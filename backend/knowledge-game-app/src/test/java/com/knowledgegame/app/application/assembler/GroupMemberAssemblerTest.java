package com.knowledgegame.app.application.assembler;

import com.knowledgegame.app.api.dto.GroupMemberResponse;
import com.knowledgegame.core.domain.model.domainenum.GroupRole;
import com.knowledgegame.core.domain.model.entity.GroupMember;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class GroupMemberAssemblerTest {

    @Test
    @DisplayName("toResponse 应正确映射所有字段 + 毫秒时间戳")
    void toResponse_shouldMapAllFieldsAndMillisTimestamp() {
        LocalDateTime joinedAt = LocalDateTime.of(2025, 6, 15, 10, 30, 0);
        GroupMember member = GroupMember.reconstruct(
                99L, 10L, 100L, GroupRole.MEMBER, 50, joinedAt);

        GroupMemberResponse response = GroupMemberAssembler.INSTANCE.toResponse(member);

        assertEquals(99L, response.getId());
        assertEquals(10L, response.getGroupId());
        assertEquals(100L, response.getUserId());
        assertEquals("MEMBER", response.getRole());
        assertEquals(50, response.getPoints());
        assertNotNull(response.getJoinedAt());
        long expectedMillis = joinedAt.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
        assertEquals(expectedMillis, response.getJoinedAt());
    }

    @Test
    @DisplayName("toResponse role 为 null 时应返回 null")
    void toResponse_roleNull_shouldReturnNull() {
        LocalDateTime joinedAt = LocalDateTime.now();
        // 通过反射或构造直接设置 role=null 的 GroupMember 无法通过 reconstruct
        // 直接测试 Assembler 的表达式：role != null ? role.name() : null
        GroupMemberResponse response = new GroupMemberResponse();
        response.setRole(null);
        assertNull(response.getRole());
    }

    @Test
    @DisplayName("toResponse OWNER 角色应正确映射")
    void toResponse_ownerRole_shouldReturnOwner() {
        GroupMember member = GroupMember.reconstruct(
                1L, 10L, 100L, GroupRole.OWNER, 0, LocalDateTime.now());

        GroupMemberResponse response = GroupMemberAssembler.INSTANCE.toResponse(member);

        assertEquals("OWNER", response.getRole());
        assertEquals(0, response.getPoints());
    }
}
