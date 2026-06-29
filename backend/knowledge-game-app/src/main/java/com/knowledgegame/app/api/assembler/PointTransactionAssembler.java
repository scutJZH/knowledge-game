package com.knowledgegame.app.api.assembler;

import com.knowledgegame.app.api.dto.response.PointTransactionCrossGroupResponse;
import com.knowledgegame.app.api.dto.response.PointTransactionResponse;
import com.knowledgegame.core.domain.model.entity.PointTransaction;
import com.knowledgegame.core.domain.model.entity.StudyGroup;
import com.knowledgegame.core.domain.model.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

/**
 * 积分流水 Assembler（Domain → DTO）
 */
@Mapper
public interface PointTransactionAssembler {

    PointTransactionAssembler INSTANCE = Mappers.getMapper(PointTransactionAssembler.class);

    /** 群组视角：仅冗余 user 信息（同群组上下文已知 groupId） */
    default PointTransactionResponse toResponse(PointTransaction tx, Map<Long, User> userMap) {
        if (tx == null) {
            return null;
        }
        PointTransactionResponse resp = new PointTransactionResponse();
        fillBaseFields(tx, resp);
        User user = userMap.get(tx.getUserId());
        if (user != null) {
            resp.setUserNickname(user.getNickname());
            if (user.getAvatar() != null) {
                resp.setUserAvatarUrl(user.getAvatar().url());
            }
        }
        return resp;
    }

    /** 个人跨群组视角：冗余 user + group 信息 */
    default PointTransactionCrossGroupResponse toResponse(PointTransaction tx,
                                                           Map<Long, User> userMap,
                                                           Map<Long, StudyGroup> groupMap) {
        if (tx == null) {
            return null;
        }
        PointTransactionCrossGroupResponse resp = new PointTransactionCrossGroupResponse();
        fillBaseFields(tx, resp);
        User user = userMap.get(tx.getUserId());
        if (user != null) {
            resp.setUserNickname(user.getNickname());
            if (user.getAvatar() != null) {
                resp.setUserAvatarUrl(user.getAvatar().url());
            }
        }
        StudyGroup group = groupMap.get(tx.getGroupId());
        if (group != null) {
            resp.setGroupName(group.getName());
            if (group.getAvatar() != null) {
                resp.setGroupAvatarUrl(group.getAvatar().url());
            }
        }
        return resp;
    }

    default Long toEpochMilli(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private void fillBaseFields(PointTransaction tx, PointTransactionResponse resp) {
        resp.setId(tx.getId());
        resp.setGroupId(tx.getGroupId());
        resp.setUserId(tx.getUserId());
        resp.setType(tx.getType().name());
        resp.setAmount(tx.getAmount());
        resp.setReferenceType(tx.getReferenceType().name());
        resp.setReferenceId(tx.getReferenceId());
        resp.setBalanceAfter(tx.getBalanceAfter());
        resp.setCreatedAt(toEpochMilli(tx.getCreatedAt()));
    }
}
