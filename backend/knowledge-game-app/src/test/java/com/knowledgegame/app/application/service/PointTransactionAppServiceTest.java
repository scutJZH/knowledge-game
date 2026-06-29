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
import org.junit.jupiter.api.Nested;
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

@ExtendWith(MockitoExtension.class)
class PointTransactionAppServiceTest {

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

    private PointTransactionQuery defaultQuery() {
        return new PointTransactionQuery(null, null, null, null,
                null, null, null, 1, 10);
    }

    private PageResult<PointTransaction> emptyPage() {
        return PageResult.<PointTransaction>builder()
                .content(List.of()).totalElements(0).pageNumber(0).pageSize(10).totalPages(0).build();
    }

    // ==================== listByGroup ====================

    @Nested
    @DisplayName("listByGroup")
    class ListByGroupTests {

        @Test
        @DisplayName("OWNER 不传 userId → effectiveUserId=null → findByGroup(userId=null)")
        void ownerNoUserId_effectiveUserIdNull() {
            GroupMember owner = member(GroupRole.OWNER);
            when(memberRepo.findByGroupIdAndUserId(1L, CALLER_ID))
                    .thenReturn(Optional.of(owner));
            when(txRepo.findByGroup(eq(1L), eq(null), any(), any(),
                    any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(emptyPage());
            when(userRepo.findByIdIn(any())).thenReturn(List.of());

            PointTransactionPageResponse<PointTransactionResponse> result =
                    appService.listByGroup(1L, defaultQuery());

            assertNotNull(result);
            verify(txRepo).findByGroup(eq(1L), eq(null), any(), any(),
                    any(), any(), any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("OWNER 传 userId=11 → effectiveUserId=11")
        void ownerWithUserId_effectiveUserId11() {
            GroupMember owner = member(GroupRole.OWNER);
            when(memberRepo.findByGroupIdAndUserId(1L, CALLER_ID))
                    .thenReturn(Optional.of(owner));
            when(txRepo.findByGroup(eq(1L), eq(11L), any(), any(),
                    any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(emptyPage());
            when(userRepo.findByIdIn(any())).thenReturn(List.of());

            PointTransactionQuery query = new PointTransactionQuery(11L, null, null, null,
                    null, null, null, 1, 10);
            appService.listByGroup(1L, query);

            verify(txRepo).findByGroup(eq(1L), eq(11L), any(), any(),
                    any(), any(), any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("ADMIN 不传 userId → 看全员（同 OWNER 不传）")
        void adminNoUserId_effectiveUserIdNull() {
            GroupMember admin = member(GroupRole.ADMIN);
            when(memberRepo.findByGroupIdAndUserId(1L, CALLER_ID))
                    .thenReturn(Optional.of(admin));
            when(txRepo.findByGroup(eq(1L), eq(null), any(), any(),
                    any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(emptyPage());
            when(userRepo.findByIdIn(any())).thenReturn(List.of());

            appService.listByGroup(1L, defaultQuery());

            verify(txRepo).findByGroup(eq(1L), eq(null), any(), any(),
                    any(), any(), any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("MEMBER 不传 userId → effectiveUserId=caller")
        void memberNoUserId_effectiveUserIdCaller() {
            GroupMember member = member(GroupRole.MEMBER);
            when(memberRepo.findByGroupIdAndUserId(1L, CALLER_ID))
                    .thenReturn(Optional.of(member));
            when(txRepo.findByGroup(eq(1L), eq(CALLER_ID), any(), any(),
                    any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(emptyPage());
            when(userRepo.findByIdIn(any())).thenReturn(List.of());

            appService.listByGroup(1L, defaultQuery());

            verify(txRepo).findByGroup(eq(1L), eq(CALLER_ID), any(), any(),
                    any(), any(), any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("MEMBER 传 userId=11 → 强制改为 caller(10)")
        void memberWithUserId_forcedToCaller() {
            GroupMember member = member(GroupRole.MEMBER);
            when(memberRepo.findByGroupIdAndUserId(1L, CALLER_ID))
                    .thenReturn(Optional.of(member));
            when(txRepo.findByGroup(eq(1L), eq(CALLER_ID), any(), any(),
                    any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(emptyPage());
            when(userRepo.findByIdIn(any())).thenReturn(List.of());

            PointTransactionQuery query = new PointTransactionQuery(11L, null, null, null,
                    null, null, null, 1, 10);
            appService.listByGroup(1L, query);

            // MEMBER 传 userId=11 被强制改为 10
            verify(txRepo).findByGroup(eq(1L), eq(CALLER_ID), any(), any(),
                    any(), any(), any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("非群组成员 → 抛 BusinessException(NOT_GROUP_MEMBER)")
        void notGroupMember_throwsException() {
            when(memberRepo.findByGroupIdAndUserId(1L, CALLER_ID))
                    .thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> appService.listByGroup(1L, defaultQuery()));
            assertEquals(ResultCode.NOT_GROUP_MEMBER.getCode(), ex.getCode());
        }
    }

    // ==================== listByUser ====================

    @Nested
    @DisplayName("listByUser")
    class ListByUserTests {

        @Test
        @DisplayName("调用 → findByUser(CALLER_ID) 被调用")
        void callsFindByUserWithCallerId() {
            when(txRepo.findByUser(eq(CALLER_ID), any(), any(), any(),
                    any(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(emptyPage());
            when(userRepo.findByIdIn(any())).thenReturn(List.of());
            when(groupRepo.findByIdIn(any())).thenReturn(List.of());

            PointTransactionPageResponse<PointTransactionCrossGroupResponse> result =
                    appService.listByUser(defaultQuery());

            assertNotNull(result);
            verify(txRepo).findByUser(eq(CALLER_ID), any(), any(), any(),
                    any(), any(), any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("包含 2 个不同 groupId 的流水 → groupRepo.findByIdIn 接收 2 个 id")
        void twoGroups_groupRepoReceivesTwo() {
            PointTransaction tx1 = PointTransaction.record(1L, CALLER_ID, TxType.EARN,
                    100, ReferenceType.GAME_REWARD, null, 100);
            PointTransaction tx2 = PointTransaction.record(2L, CALLER_ID, TxType.EARN,
                    50, ReferenceType.CHECK_IN, null, 150);

            PageResult<PointTransaction> page = PageResult.<PointTransaction>builder()
                    .content(List.of(tx1, tx2))
                    .totalElements(2).pageNumber(0).pageSize(10).totalPages(1).build();

            when(txRepo.findByUser(any(), any(), any(), any(), any(), any(),
                    any(), anyInt(), anyInt())).thenReturn(page);
            when(userRepo.findByIdIn(any())).thenReturn(List.of());
            when(groupRepo.findByIdIn(any())).thenReturn(List.of(
                    group(1L), group(2L)));

            ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
            appService.listByUser(defaultQuery());
            verify(groupRepo).findByIdIn(captor.capture());
            assertEquals(2, captor.getValue().size());
        }
    }

    // ==================== getBalance ====================

    @Nested
    @DisplayName("getBalance")
    class GetBalanceTests {

        @Test
        @DisplayName("正常路径 → BalanceResponse(groupId=1, userId=10, balance=150)")
        void normal_returnsBalanceResponse() {
            GroupMember m = member(GroupRole.MEMBER);
            when(memberRepo.findByGroupIdAndUserId(1L, CALLER_ID))
                    .thenReturn(Optional.of(m));

            BalanceResponse resp = appService.getBalance(1L);

            assertEquals(1L, resp.getGroupId());
            assertEquals(CALLER_ID, resp.getUserId());
            assertEquals(0, resp.getBalance()); // member fixture has 0 points
        }

        @Test
        @DisplayName("非群组成员 → 抛 BusinessException(NOT_GROUP_MEMBER)")
        void notMember_throwsException() {
            when(memberRepo.findByGroupIdAndUserId(1L, CALLER_ID))
                    .thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> appService.getBalance(1L));
            assertEquals(ResultCode.NOT_GROUP_MEMBER.getCode(), ex.getCode());
        }
    }

    // ==================== helpers ====================

    private GroupMember member(GroupRole role) {
        return GroupMember.reconstruct(1L, 1L, CALLER_ID, role, 0, LocalDateTime.now());
    }

    private StudyGroup group(Long id) {
        return StudyGroup.reconstruct(id, "group-" + id, null, null, id,
                com.knowledgegame.core.domain.model.domainenum.StudyGroupStatus.ACTIVE,
                com.knowledgegame.core.domain.model.domainenum.JoinPolicy.OPEN,
                null, null, null);
    }
}
