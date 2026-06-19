package com.knowledgegame.admin.api.assembler;

import com.knowledgegame.admin.api.dto.response.RecycleBinItemResponse;
import com.knowledgegame.core.domain.model.domainenum.ResourceType;
import com.knowledgegame.core.domain.model.entity.RecycleBinItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RecycleBinItemAssembler 单元测试
 * <p>
 * 覆盖：领域模型 → Response 映射、时间转换、daysUntilPurge 边界计算。
 */
class RecycleBinItemAssemblerTest {

    private final RecycleBinItemAssembler assembler = RecycleBinItemAssembler.INSTANCE;

    @Test
    @DisplayName("完整领域模型应正确映射到 Response")
    void fullDomainItem_shouldMapToResponseCorrectly() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime deadline = now.plusDays(30);
        RecycleBinItem item = new RecycleBinItem(
                42L, ResourceType.IP_SERIES, 17L, "火影忍者",
                now.minusDays(5), now.minusDays(1),
                null, null,
                "operator01", now, deadline
        );

        RecycleBinItemResponse response = assembler.toResponse(item);

        assertEquals(42L, response.getId());
        assertEquals("IP_SERIES", response.getResourceType());
        assertEquals("IP 系列", response.getResourceTypeDisplay());
        assertEquals(17L, response.getOriginalId());
        assertEquals("火影忍者", response.getOriginalName());
        assertEquals("operator01", response.getDeletedBy());
        assertNull(response.getOriginalCreatedBy());
        assertNull(response.getOriginalUpdatedBy());
        assertNotNull(response.getDaysUntilPurge());
        assertTrue(response.getDaysUntilPurge() >= 0);
    }

    @Test
    @DisplayName("daysUntilPurge: now + 6.5 天 → 7（向上取整）")
    void daysUntilPurge_sixAndHalfDays_shouldReturn7() {
        LocalDateTime deadline = LocalDateTime.now(ZoneOffset.UTC).plusHours(156); // 6.5 天
        RecycleBinItem item = itemWithDeadline(deadline);
        Integer days = assembler.calcDaysUntilPurge(item.getRestoreDeadline());
        assertEquals(7, days);
    }

    @Test
    @DisplayName("daysUntilPurge: now → 0")
    void daysUntilPurge_now_shouldReturn0() {
        LocalDateTime deadline = LocalDateTime.now(ZoneOffset.UTC);
        RecycleBinItem item = itemWithDeadline(deadline);
        Integer days = assembler.calcDaysUntilPurge(item.getRestoreDeadline());
        assertEquals(0, days);
    }

    @Test
    @DisplayName("daysUntilPurge: now - 1 天（已过期）→ 0（兜底）")
    void daysUntilPurge_past_shouldReturn0() {
        LocalDateTime deadline = LocalDateTime.now(ZoneOffset.UTC).minusDays(1);
        RecycleBinItem item = itemWithDeadline(deadline);
        Integer days = assembler.calcDaysUntilPurge(item.getRestoreDeadline());
        assertEquals(0, days);
    }

    @Test
    @DisplayName("daysUntilPurge: null deadline → 0（防御性兜底）")
    void daysUntilPurge_nullDeadline_shouldReturn0() {
        assertEquals(0, assembler.calcDaysUntilPurge(null));
    }

    @Test
    @DisplayName("toEpochMilli: null → null")
    void toEpochMilli_null_shouldReturnNull() {
        assertNull(assembler.toEpochMilli(null));
    }

    @Test
    @DisplayName("toEpochMilli: LocalDateTime → Long（epoch 毫秒）")
    void toEpochMilli_shouldReturnEpochMillis() {
        LocalDateTime time = LocalDateTime.of(2026, 6, 19, 0, 0, 0);
        Long epoch = assembler.toEpochMilli(time);
        assertNotNull(epoch);
        assertTrue(epoch > 0);
    }

    private static RecycleBinItem itemWithDeadline(LocalDateTime deadline) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return new RecycleBinItem(
                1L, ResourceType.IP_SERIES, 1L, "test",
                now, now, null, null, "admin", now, deadline
        );
    }
}
