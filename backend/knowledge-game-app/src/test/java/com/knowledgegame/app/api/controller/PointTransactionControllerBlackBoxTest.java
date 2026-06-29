package com.knowledgegame.app.api.controller;

import com.knowledgegame.app.api.dto.response.BalanceResponse;
import com.knowledgegame.app.api.dto.response.PointTransactionCrossGroupResponse;
import com.knowledgegame.app.api.dto.response.PointTransactionPageResponse;
import com.knowledgegame.app.api.dto.response.PointTransactionResponse;
import com.knowledgegame.app.application.service.PointTransactionAppService;
import com.knowledgegame.app.common.OptimisticLockExceptionHandler;
import com.knowledgegame.app.config.JacksonConfig;
import com.knowledgegame.components.exception.handler.GlobalExceptionHandler;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.common.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 黑盒测试：仅凭 PRD API 协议编写，不参考 Controller/AppService 实现代码。
 * 侧重 HTTP 层行为（路由匹配、响应 JSON 结构、异常转换格式、参数边界）。
 */
@WebMvcTest(controllers = PointTransactionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, OptimisticLockExceptionHandler.class, JacksonConfig.class})
class PointTransactionControllerBlackBoxTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PointTransactionAppService appService;

    // ==================== happy path ====================

    @Test
    @DisplayName("GET /api/study-groups/1/point-transactions → 200 + 验证分页 JSON 结构")
    void listByGroup_returnsPageResult() throws Exception {
        PointTransactionResponse item = new PointTransactionResponse();
        item.setId(101L);
        item.setGroupId(1L);
        item.setUserId(10L);
        item.setUserNickname("玩家小明");
        item.setUserAvatarUrl("https://cdn.example.com/avatars/10.png");
        item.setType("EARN");
        item.setAmount(100);
        item.setReferenceType("GAME_REWARD");
        item.setBalanceAfter(100);
        item.setCreatedAt(1719791999000L);

        PointTransactionPageResponse<PointTransactionResponse> page =
                new PointTransactionPageResponse<>(List.of(item), 1L, 1);
        when(appService.listByGroup(eq(1L), any())).thenReturn(page);

        mockMvc.perform(get("/api/study-groups/1/point-transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].id").value(101))
                .andExpect(jsonPath("$.data.content[0].type").value("EARN"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/study-groups/1/point-transactions 空结果 → data.content 空数组")
    void listByGroup_empty_returnsEmptyContent() throws Exception {
        PointTransactionPageResponse<PointTransactionResponse> page =
                new PointTransactionPageResponse<>(List.of(), 0L, 0);
        when(appService.listByGroup(eq(1L), any())).thenReturn(page);

        mockMvc.perform(get("/api/study-groups/1/point-transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content").isEmpty());
    }

    @Test
    @DisplayName("GET /api/me/point-transactions → 200 + 验证跨群组响应含 groupName")
    void listByUser_returnsCrossGroupResponse() throws Exception {
        PointTransactionCrossGroupResponse item = new PointTransactionCrossGroupResponse();
        item.setId(102L);
        item.setGroupId(5L);
        item.setGroupName("面试冲刺群");
        item.setGroupAvatarUrl("https://cdn.example.com/groups/5.png");
        item.setUserId(10L);
        item.setUserNickname("玩家小明");
        item.setType("SPEND");
        item.setAmount(50);
        item.setReferenceType("GACHA");
        item.setBalanceAfter(50);
        item.setCreatedAt(1719800000000L);

        PointTransactionPageResponse<PointTransactionCrossGroupResponse> page =
                new PointTransactionPageResponse<>(List.of(item), 1L, 1);
        when(appService.listByUser(any())).thenReturn(page);

        mockMvc.perform(get("/api/me/point-transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].groupName").value("面试冲刺群"))
                .andExpect(jsonPath("$.data.content[0].groupAvatarUrl")
                        .value("https://cdn.example.com/groups/5.png"));
    }

    @Test
    @DisplayName("GET /api/study-groups/1/members/me/balance → 200 + 验证余额字段")
    void getBalance_returnsBalance() throws Exception {
        BalanceResponse balance = new BalanceResponse(1L, 10L, 150);
        when(appService.getBalance(1L)).thenReturn(balance);

        mockMvc.perform(get("/api/study-groups/1/members/me/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groupId").value(1))
                .andExpect(jsonPath("$.data.balance").value(150));
    }

    // ==================== BusinessException propagation ====================

    @Test
    @DisplayName("AppService 抛 BusinessException → component-exception GlobalExceptionHandler 包装为 Result.fail")
    void listByGroup_businessException_returnsWrappedError() throws Exception {
        when(appService.listByGroup(eq(1L), any()))
                .thenThrow(new BusinessException(ResultCode.NOT_GROUP_MEMBER));

        mockMvc.perform(get("/api/study-groups/1/point-transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("非群组成员"));
    }

    // ==================== ObjectOptimisticLockingFailureException → 409 ====================

    @Test
    @DisplayName("AppService 抛 ObjectOptimisticLockingFailureException → OptimisticLockExceptionHandler 返回 409")
    void listByGroup_optimisticLockException_returns409() throws Exception {
        when(appService.listByGroup(eq(1L), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException("Row was updated", null));

        mockMvc.perform(get("/api/study-groups/1/point-transactions"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("数据已被其他操作修改，请重试"));
    }

    // ==================== parameter boundary ====================

    @Test
    @DisplayName("type=INVALID → toQuery() 抛 BusinessException → 400")
    void listByGroup_invalidType_returns400() throws Exception {
        mockMvc.perform(get("/api/study-groups/1/point-transactions")
                        .param("type", "INVALID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("不支持的积分来源类型"));
    }

    @Test
    @DisplayName("referenceType=INVALID → toQuery() 抛 BusinessException → 400")
    void listByGroup_invalidReferenceType_returns400() throws Exception {
        mockMvc.perform(get("/api/study-groups/1/point-transactions")
                        .param("referenceType", "INVALID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("不支持的积分来源类型"));
    }

    // ==================== page/size boundary ====================

    @Test
    @DisplayName("page=0 → toQuery() 抛 BusinessException → 400")
    void listByGroup_pageZero_returns400() throws Exception {
        mockMvc.perform(get("/api/study-groups/1/point-transactions")
                        .param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("page=-1 → toQuery() 抛 BusinessException → 400")
    void listByGroup_pageNegative_returns400() throws Exception {
        mockMvc.perform(get("/api/study-groups/1/point-transactions")
                        .param("page", "-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("size=0 → toQuery() 抛 BusinessException → 400")
    void listByGroup_sizeZero_returns400() throws Exception {
        mockMvc.perform(get("/api/study-groups/1/point-transactions")
                        .param("size", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("size=101 → toQuery() 抛 BusinessException → 400")
    void listByGroup_sizeOverMax_returns400() throws Exception {
        mockMvc.perform(get("/api/study-groups/1/point-transactions")
                        .param("size", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("size=200 → toQuery() 抛 BusinessException → 400")
    void listByGroup_size200_returns400() throws Exception {
        mockMvc.perform(get("/api/study-groups/1/point-transactions")
                        .param("size", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }
}
