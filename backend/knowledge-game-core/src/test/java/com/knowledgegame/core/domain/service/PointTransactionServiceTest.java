package com.knowledgegame.core.domain.service;

import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.common.result.ResultCode;
import com.knowledgegame.core.domain.model.domainenum.ReferenceType;
import com.knowledgegame.core.domain.model.domainenum.TxType;
import com.knowledgegame.core.domain.model.entity.GroupMember;
import com.knowledgegame.core.domain.model.entity.PointTransaction;
import com.knowledgegame.core.domain.port.outbound.GroupMemberRepository;
import com.knowledgegame.core.domain.port.outbound.PointTransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PointTransactionServiceTest {

    @Mock
    private GroupMemberRepository memberRepo;

    @Mock
    private PointTransactionRepository txRepo;

    @InjectMocks
    private PointTransactionService service;

    private static final Long GROUP_ID = 1L;
    private static final Long USER_ID = 10L;

    // ==================== record EARN ====================

    @Test
    @DisplayName("record EARN → member.earnPoints 被调用 + member.save + tx.save 均执行")
    void record_earn_success() {
        GroupMember member = GroupMember.reconstruct(1L, GROUP_ID, USER_ID,
                com.knowledgegame.core.domain.model.domainenum.GroupRole.MEMBER, 50,
                LocalDateTime.now());
        when(memberRepo.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.of(member));
        when(memberRepo.save(any(GroupMember.class))).thenReturn(member);
        when(txRepo.save(any(PointTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        PointTransaction result = service.record(GROUP_ID, USER_ID, TxType.EARN,
                50, ReferenceType.GAME_REWARD, null);

        assertNotNull(result);
        assertEquals(TxType.EARN, result.getType());
        assertEquals(100, member.getPoints()); // 50 + 50
        verify(memberRepo).save(member);
        verify(txRepo).save(any(PointTransaction.class));
    }

    // ==================== record SPEND ====================

    @Test
    @DisplayName("record SPEND → member.spendPoints 被调用，余额扣减正确")
    void record_spend_success() {
        GroupMember member = GroupMember.reconstruct(1L, GROUP_ID, USER_ID,
                com.knowledgegame.core.domain.model.domainenum.GroupRole.MEMBER, 150,
                LocalDateTime.now());
        when(memberRepo.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.of(member));
        when(memberRepo.save(any(GroupMember.class))).thenReturn(member);
        when(txRepo.save(any(PointTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        PointTransaction result = service.record(GROUP_ID, USER_ID, TxType.SPEND,
                100, ReferenceType.GACHA, 42L);

        assertNotNull(result);
        assertEquals(TxType.SPEND, result.getType());
        assertEquals(50, member.getPoints()); // 150 - 100
        verify(memberRepo).save(member);
        verify(txRepo).save(any(PointTransaction.class));
    }

    // ==================== user not in group ====================

    @Test
    @DisplayName("record 用户不在群组 → 抛 POINT_TRANSACTION_USER_NOT_IN_GROUP")
    void record_userNotInGroup_throws() {
        when(memberRepo.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.record(GROUP_ID, USER_ID, TxType.EARN, 50,
                        ReferenceType.CHECK_IN, null));
        assertEquals(ResultCode.POINT_TRANSACTION_USER_NOT_IN_GROUP.getCode(), ex.getCode());
    }

    // ==================== insufficient balance ====================

    @Test
    @DisplayName("record SPEND 余额不足 → 抛 POINT_TRANSACTION_INSUFFICIENT_BALANCE")
    void record_insufficientBalance_throws() {
        GroupMember member = GroupMember.reconstruct(1L, GROUP_ID, USER_ID,
                com.knowledgegame.core.domain.model.domainenum.GroupRole.MEMBER, 10,
                LocalDateTime.now());
        when(memberRepo.findByGroupIdAndUserId(GROUP_ID, USER_ID)).thenReturn(Optional.of(member));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.record(GROUP_ID, USER_ID, TxType.SPEND, 100,
                        ReferenceType.GACHA, null));
        assertEquals(ResultCode.POINT_TRANSACTION_INSUFFICIENT_BALANCE.getCode(), ex.getCode());
    }
}
