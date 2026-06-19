package com.knowledgegame.core.infrastructure.db.converter;

import com.knowledgegame.core.domain.model.domainenum.GroupRole;
import com.knowledgegame.core.domain.model.entity.GroupMember;
import com.knowledgegame.core.infrastructure.db.entity.GroupMemberPO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GroupMemberConverterTest {

    @Nested
    @DisplayName("toDomain（PO → 领域模型）")
    class ToDomainTests {

        @Test
        @DisplayName("应正确还原所有字段含 role 枚举")
        void shouldRestoreAllFields() {
            LocalDateTime joinedAt = LocalDateTime.of(2025, 3, 15, 10, 0, 0);
            GroupMemberPO po = GroupMemberPO.builder()
                    .id(99L)
                    .groupId(10L)
                    .userId(100L)
                    .role(GroupRole.ADMIN)
                    .points(50)
                    .joinedAt(joinedAt)
                    .build();

            GroupMember domain = GroupMemberConverter.INSTANCE.toDomain(po);

            assertEquals(99L, domain.getId());
            assertEquals(10L, domain.getGroupId());
            assertEquals(100L, domain.getUserId());
            assertEquals(GroupRole.ADMIN, domain.getRole());
            assertEquals(50, domain.getPoints());
            assertEquals(joinedAt, domain.getJoinedAt());
        }

        @Test
        @DisplayName("null PO 返回 null")
        void shouldReturnNullForNullPO() {
            assertNull(GroupMemberConverter.INSTANCE.toDomain(null));
        }
    }

    @Nested
    @DisplayName("toPO（领域模型 → PO）")
    class ToPOTests {

        @Test
        @DisplayName("应正确转换所有字段含 role 枚举")
        void shouldConvertAllFields() {
            GroupMember domain = GroupMember.createOwner(10L, 100L);

            GroupMemberPO po = GroupMemberConverter.INSTANCE.toPO(domain);

            assertEquals(10L, po.getGroupId());
            assertEquals(100L, po.getUserId());
            assertEquals(GroupRole.OWNER, po.getRole());
            assertEquals(0, po.getPoints());
            assertNull(po.getId());
        }

        @Test
        @DisplayName("null 领域模型返回 null")
        void shouldReturnNullForNullDomain() {
            assertNull(GroupMemberConverter.INSTANCE.toPO(null));
        }
    }
}
