package com.knowledgegame.core.domain.port.outbound;

import com.knowledgegame.core.domain.model.domainenum.ReferenceType;
import com.knowledgegame.core.domain.model.domainenum.TxType;
import com.knowledgegame.core.domain.model.entity.PointTransaction;
import com.knowledgegame.core.domain.model.vo.PageResult;
import com.knowledgegame.core.domain.model.vo.SortField;

import java.time.LocalDateTime;

/**
 * 积分流水仓储出端口（领域层定义，基础设施层实现）
 */
public interface PointTransactionRepository {

    PointTransaction save(PointTransaction tx);

    /** 群组维度分页查询（管理员视角：userId 可空） */
    PageResult<PointTransaction> findByGroup(Long groupId, Long userId,
                                              TxType type, ReferenceType refType,
                                              LocalDateTime startDate, LocalDateTime endDate,
                                              SortField sortField, int page, int size);

    /** 用户维度分页查询（个人视角：跨群组） */
    PageResult<PointTransaction> findByUser(Long userId, Long groupId,
                                             TxType type, ReferenceType refType,
                                             LocalDateTime startDate, LocalDateTime endDate,
                                             SortField sortField, int page, int size);
}
