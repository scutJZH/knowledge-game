package com.knowledgegame.app.application.assembler;

import com.knowledgegame.app.api.dto.StudyGroupResponse;
import com.knowledgegame.core.domain.model.domainenum.JoinPolicy;
import com.knowledgegame.core.domain.model.domainenum.StudyGroupStatus;
import com.knowledgegame.core.domain.model.entity.StudyGroup;
import com.knowledgegame.core.domain.model.vo.FileRef;
import com.knowledgegame.core.domain.model.vo.InviteCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class StudyGroupAssemblerTest {

    @Test
    @DisplayName("toResponse 应正确解包 FileRef 双字段 + joinPolicy + inviteCode + 毫秒时间戳")
    void toResponse_shouldUnpackFileRefAndTimestamps() {
        LocalDateTime now = LocalDateTime.now();
        StudyGroup group = StudyGroup.reconstruct(1L, "测试群组", "描述",
                FileRef.of(10L, "https://example.com/avatar.png"),
                100L, StudyGroupStatus.ACTIVE, JoinPolicy.OPEN, InviteCode.of("ABC12345"), now, now);

        StudyGroupResponse response = StudyGroupAssembler.INSTANCE.toResponse(group);

        assertEquals(1L, response.getId());
        assertEquals("测试群组", response.getName());
        assertEquals("描述", response.getDescription());
        assertEquals(10L, response.getAvatarFileId());
        assertEquals("https://example.com/avatar.png", response.getAvatarUrl());
        assertEquals(100L, response.getOwnerId());
        assertEquals("OPEN", response.getJoinPolicy());
        assertEquals("ABC12345", response.getInviteCode());
        assertNotNull(response.getCreatedAt());
        assertNotNull(response.getUpdatedAt());
        long expectedMillis = now.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
        assertEquals(expectedMillis, response.getCreatedAt());
        assertEquals(expectedMillis, response.getUpdatedAt());
    }

    @Test
    @DisplayName("toResponse FileRef 为 null 时应返回 null 双字段")
    void toResponse_shouldReturnNullFileRefFieldsWhenNull() {
        StudyGroup group = StudyGroup.reconstruct(1L, "群组", null, null, 100L, StudyGroupStatus.ACTIVE,
                JoinPolicy.INVITE_ONLY, InviteCode.of("XYZ67890"),
                LocalDateTime.now(), LocalDateTime.now());

        StudyGroupResponse response = StudyGroupAssembler.INSTANCE.toResponse(group);

        assertNull(response.getAvatarFileId());
        assertNull(response.getAvatarUrl());
        assertNull(response.getDescription());
        assertEquals("INVITE_ONLY", response.getJoinPolicy());
        assertEquals("XYZ67890", response.getInviteCode());
    }
}
