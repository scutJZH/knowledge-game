package com.knowledgegame.core.infrastructure.db.converter;

import com.knowledgegame.core.domain.model.entity.GroupIpLibrary;
import com.knowledgegame.core.infrastructure.db.entity.GroupIpLibraryPO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupIpLibraryConverterTest {

    @Nested
    @DisplayName("toDomain（PO → 领域模型）")
    class ToDomainTests {

        @Test
        @DisplayName("应正确还原所有字段")
        void shouldRestoreAllFields() {
            LocalDateTime addedAt = LocalDateTime.of(2025, 6, 1, 12, 0, 0);
            GroupIpLibraryPO po = new GroupIpLibraryPO()
                    .setId(99L)
                    .setGroupId(1L)
                    .setIpSeriesId(10L)
                    .setAddedAt(addedAt);

            GroupIpLibrary domain = GroupIpLibraryConverter.INSTANCE.toDomain(po);

            assertEquals(99L, domain.getId());
            assertEquals(1L, domain.getGroupId());
            assertEquals(10L, domain.getIpSeriesId());
            assertEquals(addedAt, domain.getAddedAt());
        }

        @Test
        @DisplayName("null PO 返回 null")
        void shouldReturnNullForNullPO() {
            assertNull(GroupIpLibraryConverter.INSTANCE.toDomain(null));
        }
    }

    @Nested
    @DisplayName("toPO（领域模型 → PO）")
    class ToPOTests {

        @Test
        @DisplayName("应正确转换所有字段，id 不设")
        void shouldConvertAllFields() {
            GroupIpLibrary domain = GroupIpLibrary.create(1L, 10L);

            GroupIpLibraryPO po = GroupIpLibraryConverter.INSTANCE.toPO(domain);

            assertNull(po.getId());
            assertEquals(1L, po.getGroupId());
            assertEquals(10L, po.getIpSeriesId());
            assertEquals(domain.getAddedAt(), po.getAddedAt());
        }

        @Test
        @DisplayName("null 领域模型返回 null")
        void shouldReturnNullForNullDomain() {
            assertNull(GroupIpLibraryConverter.INSTANCE.toPO(null));
        }
    }

    @Nested
    @DisplayName("toDomainList（PO 列表 → 领域模型列表）")
    class ToDomainListTests {

        @Test
        @DisplayName("应正确转换列表")
        void shouldConvertList() {
            LocalDateTime now = LocalDateTime.now();
            GroupIpLibraryPO po1 = new GroupIpLibraryPO().setId(1L).setGroupId(1L).setIpSeriesId(10L).setAddedAt(now);
            GroupIpLibraryPO po2 = new GroupIpLibraryPO().setId(2L).setGroupId(1L).setIpSeriesId(20L).setAddedAt(now);

            List<GroupIpLibrary> result = GroupIpLibraryConverter.INSTANCE.toDomainList(List.of(po1, po2));

            assertEquals(2, result.size());
            assertEquals(1L, result.get(0).getId());
            assertEquals(10L, result.get(0).getIpSeriesId());
            assertEquals(2L, result.get(1).getId());
            assertEquals(20L, result.get(1).getIpSeriesId());
        }

        @Test
        @DisplayName("空列表返回空列表")
        void shouldReturnEmptyListForEmptyInput() {
            assertTrue(GroupIpLibraryConverter.INSTANCE.toDomainList(List.of()).isEmpty());
        }

        @Test
        @DisplayName("null 列表返回空列表")
        void shouldReturnEmptyListForNullInput() {
            assertTrue(GroupIpLibraryConverter.INSTANCE.toDomainList(null).isEmpty());
        }
    }
}
