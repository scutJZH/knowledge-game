package com.knowledgegame.core.infrastructure.db.converter;

import com.knowledgegame.core.domain.model.domainenum.UserRole;
import com.knowledgegame.core.domain.model.entity.User;
import com.knowledgegame.core.domain.model.vo.FileRef;
import com.knowledgegame.core.infrastructure.db.entity.UserPO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class UserConverterTest {

    static FileRef avatar = FileRef.of(1L, "/static/avatar.png");

    @Nested
    @DisplayName("toDomain（PO → 领域模型）")
    class ToDomainTests {

        @Test
        @DisplayName("双字段 PO 应映射为 FileRef avatar")
        void shouldMapDualFieldsToFileRef() {
            UserPO po = buildPO(1L, 10L, "/static/avatar.png");
            User domain = UserConverter.toDomain(po);
            assertNotNull(domain.getAvatar());
            assertEquals(10L, domain.getAvatar().fileId());
            assertEquals("/static/avatar.png", domain.getAvatar().url());
        }

        @Test
        @DisplayName("双字段均为 null 时 FileRef 为 null")
        void shouldReturnNullFileRefWhenBothNull() {
            UserPO po = buildPO(1L, null, null);
            User domain = UserConverter.toDomain(po);
            assertNull(domain.getAvatar());
        }
    }

    @Nested
    @DisplayName("toPO（领域模型 → PO）")
    class ToPOTests {

        @Test
        @DisplayName("FileRef 应映射为双字段 PO")
        void shouldMapFileRefToDualFields() {
            User domain = buildDomain(avatar);
            UserPO po = UserConverter.toPO(domain);
            assertEquals(1L, po.getAvatarFileId());
            assertEquals("/static/avatar.png", po.getAvatar());
        }

        @Test
        @DisplayName("null FileRef 应映射为双 null PO 字段")
        void shouldMapNullFileRefToNullFields() {
            User domain = buildDomain(null);
            UserPO po = UserConverter.toPO(domain);
            assertNull(po.getAvatarFileId());
            assertNull(po.getAvatar());
        }
    }

    @Nested
    @DisplayName("updatePO（领域模型 → 已有 PO 更新）")
    class UpdatePOTests {

        @Test
        @DisplayName("非 null FileRef 应显式赋值 PO 双字段")
        void shouldSetDualFieldsWhenFileRefNonNull() {
            UserPO po = buildPO(1L, 9L, "/old.png");
            User domain = buildDomain(avatar);
            UserConverter.updatePO(po, domain);
            assertEquals(1L, po.getAvatarFileId());
            assertEquals("/static/avatar.png", po.getAvatar());
        }

        @Test
        @DisplayName("null FileRef 应清空 PO 双字段")
        void shouldClearDualFieldsWhenFileRefNull() {
            UserPO po = buildPO(1L, 9L, "/old.png");
            User domain = buildDomain(null);
            UserConverter.updatePO(po, domain);
            assertNull(po.getAvatarFileId());
            assertNull(po.getAvatar());
        }
    }

    private UserPO buildPO(Long id, Long fileId, String avatarUrl) {
        UserPO po = new UserPO();
        po.setId(id);
        po.setUsername("testuser");
        po.setPasswordHash("hash");
        po.setNickname("测试");
        po.setAvatarFileId(fileId);
        po.setAvatar(avatarUrl);
        po.setRole(UserRole.USER);
        po.setCreatedAt(LocalDateTime.now());
        po.setUpdatedAt(LocalDateTime.now());
        return po;
    }

    private User buildDomain(FileRef avatarRef) {
        return User.reconstruct(1L, "testuser", "hash", "测试",
                avatarRef, UserRole.USER,
                LocalDateTime.now(), LocalDateTime.now());
    }
}
