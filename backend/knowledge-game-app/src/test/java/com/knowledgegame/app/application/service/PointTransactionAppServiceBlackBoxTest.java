package com.knowledgegame.app.application.service;

import com.knowledgegame.app.api.dto.response.BalanceResponse;
import com.knowledgegame.app.api.dto.response.PointTransactionCrossGroupResponse;
import com.knowledgegame.app.api.dto.response.PointTransactionPageResponse;
import com.knowledgegame.app.api.dto.response.PointTransactionResponse;
import com.knowledgegame.app.application.command.PointTransactionQuery;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.common.result.ResultCode;
import com.knowledgegame.core.domain.model.domainenum.GroupRole;
import com.knowledgegame.core.domain.model.domainenum.ReferenceType;
import com.knowledgegame.core.domain.model.domainenum.TxType;
import com.knowledgegame.core.domain.model.entity.GroupMember;
import com.knowledgegame.core.domain.model.entity.PointTransaction;
import com.knowledgegame.core.domain.model.entity.StudyGroup;
import com.knowledgegame.core.domain.model.entity.User;
import com.knowledgegame.core.domain.model.vo.PageResult;
import com.knowledgegame.core.domain.port.outbound.GroupMemberRepository;
import com.knowledgegame.core.domain.port.outbound.PointTransactionRepository;
import com.knowledgegame.core.domain.port.outbound.StudyGroupRepository;
import com.knowledgegame.core.domain.port.outbound.UserRepositoryPort;
import com.knowledgegame.core.domain.service.PointTransactionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 黑盒测试：仅凭 PRD 接口签名 + 行为描述编写，不参考 AppService 实现代码。
 * 侧重 Repository 调用参数正确性、批量预加载、权限边界。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PointTransactionAppService Black-Box")
class PointTransactionAppServiceBlackBoxTest {

    @Mock
    private PointTransactionService txService;

    @Mock
    private PointTransactionRepository txRepo;

    @Mock
    private GroupMemberRepository memberRepo;

    @Mock
    private UserRepositoryPort userRepo;

    @Mock
    private StudyGroupRepository groupRepo;

    private PointTransactionAppService appService;

    private MockedStatic<com.knowledgegame.auth.security.SecurityUtils> securityUtilsMock;

    private static final Long CALLER_ID = 10L;

    @BeforeEach
    void setUp() {
        appService = new PointTransactionAppService(txService, txRepo, memberRepo, userRepo, groupRepo);
        securityUtilsMock = mockStatic(com.knowledgegame.auth.security.SecurityUtils.class);
        securityUtilsMock.when(com.knowledgegame.auth.security.SecurityUtils::getCurrentUserId)
                .thenReturn(CALLER_ID);
    }

    @AfterEach
    void tearDown() {
        securityUtilsMock.close();
    }

    // ==================== listByGroup — 批量预加载验证（白盒未覆盖） ====================

    @Test
    @DisplayName("流水含 2 个不同 userId → userRepo.findByIdIn 接收 2 个 id（批量预加载无 N+1）")
    void listByGroup_bulkPreload_userRepoCalledWithDistinctIds() {
        GroupMember owner = GroupMember.reconstruct(1L, 1L, CALLER_ID, GroupRole.OWNER, 0, LocalDateTime.now());
        when(memberRepo.findByGroupIdAndUserId(1L, CALLER_ID)).thenReturn(Optional.of(owner));

        PointTransaction tx1 = PointTransaction.record(1L, 10L, TxType.EARN,
                100, ReferenceType.GAME_REWARD, null, 100);
        PointTransaction tx2 = PointTransaction.record(1L, 11L, TxType.EARN,
                50, ReferenceType.CHECK_IN, null, 50);
        PageResult<PointTransaction> page = PageResult.<PointTransaction>builder()
                .content(List.of(tx1, tx2)).totalElements(2)
                .pageNumber(0).pageSize(10).totalPages(1).build();
        when(txRepo.findByGroup(any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(page);
        when(userRepo.findByIdIn(any())).thenReturn(List.of(newUser(10L), newUser(11L)));

        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        appService.listByGroup(1L, new PointTransactionQuery(null, null, null, null,
                null, null, null, 1, 10));
        verify(userRepo).findByIdIn(captor.capture());

        List<Long> captured = captor.getValue();
        assertEquals(2, captured.size());
        assertEquals(List.of(10L, 11L), captured);
    }

    // ==================== listByUser ====================

    @Test
    @DisplayName("listByUser 跨群组 → groupRepo.findByIdIn 接收所有 groupId")
    void listByUser_bulkPreload_groupsCalledWithDistinctIds() {
        PointTransaction tx1 = PointTransaction.record(1L, CALLER_ID, TxType.EARN,
                100, ReferenceType.GAME_REWARD, null, 100);
        PointTransaction tx2 = PointTransaction.record(3L, CALLER_ID, TxType.SPEND,
                20, ReferenceType.GACHA, null, 80);
        PageResult<PointTransaction> page = PageResult.<PointTransaction>builder()
                .content(List.of(tx1, tx2)).totalElements(2)
                .pageNumber(0).pageSize(10).totalPages(1).build();
        when(txRepo.findByUser(any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(page);
        when(userRepo.findByIdIn(any())).thenReturn(List.of(newUser(CALLER_ID)));
        when(groupRepo.findByIdIn(any())).thenReturn(List.of());

        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        appService.listByUser(new PointTransactionQuery(null, null, null, null,
                null, null, null, 1, 10));
        verify(groupRepo).findByIdIn(captor.capture());

        List<Long> captured = captor.getValue();
        assertEquals(2, captured.size());
    }

    // ==================== getBalance ====================

    @Test
    @DisplayName("getBalance → 返回 BalanceResponse 含正确 groupId/userId/balance")
    void getBalance_returnsCorrectResponse() {
        GroupMember member = GroupMember.reconstruct(1L, 1L, CALLER_ID, GroupRole.MEMBER, 150,
                LocalDateTime.now());
        when(memberRepo.findByGroupIdAndUserId(1L, CALLER_ID)).thenReturn(Optional.of(member));

        BalanceResponse resp = appService.getBalance(1L);

        assertNotNull(resp);
        assertEquals(1L, resp.getGroupId());
        assertEquals(CALLER_ID, resp.getUserId());
        assertEquals(150, resp.getBalance());
    }

    // ==================== helpers ====================

    private PageResult<PointTransaction> emptyPage() {
        return PageResult.<PointTransaction>builder()
                .content(List.of()).totalElements(0).pageNumber(0).pageSize(10).totalPages(0).build();
    }

    private User newUser(Long id) {
        return User.reconstruct(id, "user" + id, null, "用户" + id, null,
                com.knowledgegame.core.domain.model.domainenum.UserRole.USER,
                LocalDateTime.now(), LocalDateTime.now());
    }
}
