package com.knowledgegame.app.api.controller;

import com.knowledgegame.app.api.dto.request.PointTransactionQueryRequest;
import com.knowledgegame.app.api.dto.response.BalanceResponse;
import com.knowledgegame.app.api.dto.response.PointTransactionCrossGroupResponse;
import com.knowledgegame.app.api.dto.response.PointTransactionPageResponse;
import com.knowledgegame.app.api.dto.response.PointTransactionResponse;
import com.knowledgegame.app.application.command.PointTransactionQuery;
import com.knowledgegame.app.application.service.PointTransactionAppService;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.common.result.Result;
import com.knowledgegame.core.common.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PointTransactionControllerTest {

    @Mock
    private PointTransactionAppService appService;

    @InjectMocks
    private PointTransactionController controller;

    // ==================== listByGroup ====================

    @Test
    @DisplayName("GET listByGroup → 调用 appService.listByGroup 并返回 200")
    void listByGroup_returnsPageResult() {
        PointTransactionPageResponse<PointTransactionResponse> serviceResult =
                new PointTransactionPageResponse<>(List.of(), 0, 0);
        when(appService.listByGroup(eq(1L), any(PointTransactionQuery.class)))
                .thenReturn(serviceResult);

        Result<PointTransactionPageResponse<PointTransactionResponse>> result =
                controller.listByGroup(1L, new PointTransactionQueryRequest());

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(0, result.getData().getTotalElements());
        verify(appService).listByGroup(eq(1L), any(PointTransactionQuery.class));
    }

    // ==================== listByUser ====================

    @Test
    @DisplayName("GET listByUser → 调用 appService.listByUser")
    void listByUser_callsAppService() {
        PointTransactionPageResponse<PointTransactionCrossGroupResponse> serviceResult =
                new PointTransactionPageResponse<>(List.of(), 0, 0);
        when(appService.listByUser(any(PointTransactionQuery.class)))
                .thenReturn(serviceResult);

        Result<PointTransactionPageResponse<PointTransactionCrossGroupResponse>> result =
                controller.listByUser(new PointTransactionQueryRequest());

        assertNotNull(result);
        assertEquals(200, result.getCode());
        verify(appService).listByUser(any(PointTransactionQuery.class));
    }

    // ==================== getBalance ====================

    @Test
    @DisplayName("GET balance → 返回 BalanceResponse")
    void getBalance_returnsBalance() {
        BalanceResponse balance = new BalanceResponse(1L, 10L, 150);
        when(appService.getBalance(1L)).thenReturn(balance);

        Result<BalanceResponse> result = controller.getBalance(1L);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals(1L, result.getData().getGroupId());
        assertEquals(10L, result.getData().getUserId());
        assertEquals(150, result.getData().getBalance());
    }

    // ==================== exception propagation ====================

    @Test
    @DisplayName("appService 抛 BusinessException(NOT_GROUP_MEMBER) → Controller 透传异常")
    void listByGroup_notGroupMember_throwsBusinessException() {
        when(appService.listByGroup(eq(1L), any(PointTransactionQuery.class)))
                .thenThrow(new BusinessException(ResultCode.NOT_GROUP_MEMBER));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.listByGroup(1L, new PointTransactionQueryRequest()));
        assertEquals(ResultCode.NOT_GROUP_MEMBER.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("appService 抛 BusinessException(OPTIMISTIC_LOCK_CONFLICT) → Controller 透传")
    void listByGroup_optimisticLock_throwsBusinessException() {
        when(appService.listByGroup(eq(1L), any(PointTransactionQuery.class)))
                .thenThrow(new BusinessException(ResultCode.OPTIMISTIC_LOCK_CONFLICT));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.listByGroup(1L, new PointTransactionQueryRequest()));
        assertEquals(ResultCode.OPTIMISTIC_LOCK_CONFLICT.getCode(), ex.getCode());
    }
}
