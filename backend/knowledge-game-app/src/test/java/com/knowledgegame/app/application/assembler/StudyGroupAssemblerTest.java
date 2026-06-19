package com.knowledgegame.app.application.assembler;

import com.knowledgegame.app.api.dto.StudyGroupResponse;
import com.knowledgegame.core.domain.model.entity.StudyGroup;
import com.knowledgegame.core.domain.model.vo.FileRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class StudyGroupAssemblerTest {

    @Test
    @DisplayName("toResponse 应正确解包 FileRef 双字段 + 毫秒时间戳")
    void toResponse_shouldUnpackFileRefAndTimestamps() {
        LocalDateTime now = LocalDateTime.now();
        StudyGroup group = StudyGroup.reconstruct(1L, "测试群组", "描述",
                FileRef.of(10L, "https://example.com/avatar.png"),
                100L, now, now);

        StudyGroupResponse response = StudyGroupAssembler.INSTANCE.toResponse(group);

        assertEquals(1L, response.getId());
        assertEquals("测试群组", response.getName());
        assertEquals("描述", response.getDescription());
        assertEquals(10L, response.getAvatarFileId());
        assertEquals("https://example.com/avatar.png", response.getAvatarUrl());
        assertEquals(100L, response.getOwnerId());
        assertNotNull(response.getCreatedAt());
        assertNotNull(response.getUpdatedAt());
        long expectedMillis = now.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
        assertEquals(expectedMillis, response.getCreatedAt());
        assertEquals(expectedMillis, response.getUpdatedAt());
    }

    @Test
    @DisplayName("toResponse FileRef 为 null 时应返回 null 双字段")
    void toResponse_shouldReturnNullFileRefFieldsWhenNull() {
        StudyGroup group = StudyGroup.reconstruct(1L, "群组", null, null, 100L,
                LocalDateTime.now(), LocalDateTime.now());

        StudyGroupResponse response = StudyGroupAssembler.INSTANCE.toResponse(group);

        assertNull(response.getAvatarFileId());
        assertNull(response.getAvatarUrl());
        assertNull(response.getDescription());
    }
}
