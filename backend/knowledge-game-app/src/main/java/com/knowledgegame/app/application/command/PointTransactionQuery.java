package com.knowledgegame.app.application.command;

import com.knowledgegame.core.domain.model.domainenum.ReferenceType;
import com.knowledgegame.core.domain.model.domainenum.TxType;
import com.knowledgegame.core.domain.model.vo.SortField;

import java.time.LocalDateTime;

/**
 * 积分流水查询对象（应用层内部 query，由 Controller 的 QueryRequest.toQuery() 转换而来）
 */
public record PointTransactionQuery(
        Long userId,
        Long groupId,
        TxType type,
        ReferenceType referenceType,
        LocalDateTime startDate,
        LocalDateTime endDate,
        SortField sortField,
        int page,
        int size
) {}
