package com.knowledgegame.core.infrastructure.db.converter;

import com.knowledgegame.core.domain.model.domainenum.JoinPolicy;
import com.knowledgegame.core.domain.model.entity.StudyGroup;
import com.knowledgegame.core.domain.model.vo.FileRef;
import com.knowledgegame.core.domain.model.vo.InviteCode;
import com.knowledgegame.core.infrastructure.db.entity.StudyGroupPO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class StudyGroupConverterTest {

    @Nested
    @DisplayName("toDomain（PO → 领域模型）")
    class ToDomainTests {

        @Test
        @DisplayName("双字段 PO 应映射为 FileRef")
        void shouldMapDualFieldsToFileRef() {
            StudyGroupPO po = buildPO(1L, 10L, "/static/avatar.jpg",
                    JoinPolicy.OPEN, "ABC12345");

            StudyGroup domain = StudyGroupConverter.INSTANCE.toDomain(po);

            assertNotNull(domain.getAvatar());
            assertEquals(10L, domain.getAvatar().fileId());
            assertEquals("/static/avatar.jpg", domain.getAvatar().url());
        }

        @Test
        @DisplayName("双字段均为 null 时 FileRef 为 null")
        void shouldReturnNullFileRefWhenBothNull() {
            StudyGroupPO po = buildPO(1L, null, null,
                    JoinPolicy.OPEN, "ABC12345");

            StudyGroup domain = StudyGroupConverter.INSTANCE.toDomain(po);

            assertNull(domain.getAvatar());
        }

        @Test
        @DisplayName("null PO 返回 null")
        void shouldReturnNullForNullPO() {
            assertNull(StudyGroupConverter.INSTANCE.toDomain(null));
        }

        @Test
        @DisplayName("joinPolicy 枚举应正确映射")
        void toDomain_mapsJoinPolicy() {
            StudyGroupPO po = buildPO(1L, null, null,
                    JoinPolicy.INVITE_ONLY, "ABC12345");

            StudyGroup domain = StudyGroupConverter.INSTANCE.toDomain(po);

            assertEquals(JoinPolicy.INVITE_ONLY, domain.getJoinPolicy());
        }

        @Test
        @DisplayName("inviteCode 字符串应正确映射为 InviteCode 值对象")
        void toDomain_mapsInviteCodeFromString() {
            StudyGroupPO po = buildPO(1L, null, null,
                    JoinPolicy.OPEN, "XYZ67890");

            StudyGroup domain = StudyGroupConverter.INSTANCE.toDomain(po);

            assertNotNull(domain.getInviteCode());
            assertEquals("XYZ67890", domain.getInviteCodeValue());
        }
    }

    @Nested
    @DisplayName("toPO（领域模型 → PO）")
    class ToPOTests {

        @Test
        @DisplayName("FileRef 应映射为双字段 PO")
        void shouldMapFileRefToDualFields() {
            StudyGroup domain = StudyGroup.reconstruct(1L, "测试群组", "描述",
                    FileRef.of(10L, "/static/avatar.jpg"),
                    100L, JoinPolicy.OPEN, InviteCode.of("ABC12345"),
                    LocalDateTime.now(), LocalDateTime.now());

            StudyGroupPO po = StudyGroupConverter.INSTANCE.toPO(domain);

            assertEquals(10L, po.getAvatarFileId());
            assertEquals("/static/avatar.jpg", po.getAvatarUrl());
        }

        @Test
        @DisplayName("null FileRef 应映射为双 null PO 字段")
        void shouldMapNullFileRefToNullFields() {
            StudyGroup domain = StudyGroup.reconstruct(1L, "测试群组", "描述",
                    null, 100L, JoinPolicy.OPEN, InviteCode.of("ABC12345"),
                    LocalDateTime.now(), LocalDateTime.now());

            StudyGroupPO po = StudyGroupConverter.INSTANCE.toPO(domain);

            assertNull(po.getAvatarFileId());
            assertNull(po.getAvatarUrl());
        }

        @Test
        @DisplayName("joinPolicy 枚举应正确映射到 PO")
        void toPO_mapsJoinPolicy() {
            StudyGroup domain = StudyGroup.reconstruct(1L, "测试群组", null,
                    null, 100L, JoinPolicy.INVITE_ONLY, InviteCode.of("ABC12345"),
                    LocalDateTime.now(), LocalDateTime.now());

            StudyGroupPO po = StudyGroupConverter.INSTANCE.toPO(domain);

            assertEquals(JoinPolicy.INVITE_ONLY, po.getJoinPolicy());
        }

        @Test
        @DisplayName("inviteCode 值对象应映射为 PO 字符串")
        void toPO_mapsInviteCodeToString() {
            StudyGroup domain = StudyGroup.reconstruct(1L, "测试群组", null,
                    null, 100L, JoinPolicy.OPEN, InviteCode.of("XYZ67890"),
                    LocalDateTime.now(), LocalDateTime.now());

            StudyGroupPO po = StudyGroupConverter.INSTANCE.toPO(domain);

            assertEquals("XYZ67890", po.getInviteCode());
        }
    }

    @Nested
    @DisplayName("updatePO（领域模型 → 已有 PO 更新）")
    class UpdatePOTests {

        @Test
        @DisplayName("非 null FileRef 应显式赋值 PO 双字段")
        void shouldSetDualFieldsWhenFileRefNonNull() {
            StudyGroupPO po = buildPO(1L, 1L, "/static/old.jpg",
                    JoinPolicy.OPEN, "VKDCDE13");
            StudyGroup domain = StudyGroup.reconstruct(1L, "新名称", "新描述",
                    FileRef.of(99L, "/static/new.jpg"),
                    100L, JoinPolicy.INVITE_ONLY, InviteCode.of("NWKCDE12"),
                    null, null);

            StudyGroupConverter.INSTANCE.updatePO(po, domain);

            assertEquals(99L, po.getAvatarFileId());
            assertEquals("/static/new.jpg", po.getAvatarUrl());
        }

        @Test
        @DisplayName("null FileRef 应清空 PO 双字段（clearXxx 语义穿透）")
        void shouldClearDualFieldsWhenFileRefNull() {
            StudyGroupPO po = buildPO(1L, 1L, "/static/old.jpg",
                    JoinPolicy.OPEN, "VKDCDE13");
            StudyGroup domain = StudyGroup.reconstruct(1L, "新名称", null,
                    null, 100L, JoinPolicy.INVITE_ONLY, InviteCode.of("NWKCDE12"),
                    null, null);

            StudyGroupConverter.INSTANCE.updatePO(po, domain);

            assertNull(po.getAvatarFileId());
            assertNull(po.getAvatarUrl());
        }
    }

    @Nested
    @DisplayName("roundTrip（PO → Domain → PO 全字段保留）")
    class RoundTripTests {

        @Test
        @DisplayName("往返转换后除 id 外所有字段应保持一致（toPO 不设 id）")
        void roundTrip_preservesAllFields() {
            LocalDateTime now = LocalDateTime.now();
            StudyGroupPO original = buildPO(1L, 10L, "https://example.com/avatar.png",
                    JoinPolicy.INVITE_ONLY, "XYZ98765");
            original.setCreatedAt(now);
            original.setUpdatedAt(now);

            StudyGroup domain = StudyGroupConverter.INSTANCE.toDomain(original);
            StudyGroupPO result = StudyGroupConverter.INSTANCE.toPO(domain);

            assertEquals(original.getName(), result.getName());
            assertEquals(original.getDescription(), result.getDescription());
            assertEquals(original.getAvatarFileId(), result.getAvatarFileId());
            assertEquals(original.getAvatarUrl(), result.getAvatarUrl());
            assertEquals(original.getOwnerId(), result.getOwnerId());
            assertEquals(original.getJoinPolicy(), result.getJoinPolicy());
            assertEquals(original.getInviteCode(), result.getInviteCode());
        }
    }

    private StudyGroupPO buildPO(Long id, Long fileId, String url,
                                  JoinPolicy joinPolicy, String inviteCode) {
        return StudyGroupPO.builder()
                .id(id)
                .name("测试群组")
                .description("描述")
                .avatarFileId(fileId)
                .avatarUrl(url)
                .ownerId(100L)
                .joinPolicy(joinPolicy)
                .inviteCode(inviteCode)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
