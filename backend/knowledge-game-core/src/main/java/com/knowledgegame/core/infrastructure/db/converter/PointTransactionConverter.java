package com.knowledgegame.core.infrastructure.db.converter;

import com.knowledgegame.core.domain.model.entity.PointTransaction;
import com.knowledgegame.core.infrastructure.db.entity.PointTransactionPO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * 积分流水 PO ↔ 领域模型转换器（MapStruct 自动生成实现）。
 * 仅含 toPO / toDomain，无 updatePO（流水只追加，写入后永不修改）
 */
@Mapper
public interface PointTransactionConverter {

    PointTransactionConverter INSTANCE = Mappers.getMapper(PointTransactionConverter.class);

    /**
     * PO 转领域模型（使用 reconstruct 工厂方法）
     */
    default PointTransaction toDomain(PointTransactionPO po) {
        if (po == null) {
            return null;
        }
        return PointTransaction.reconstruct(
                po.getId(),
                po.getGroupId(),
                po.getUserId(),
                po.getType(),
                po.getAmount(),
                po.getReferenceType(),
                po.getReferenceId(),
                po.getBalanceAfter(),
                po.getCreatedAt()
        );
    }

    /**
     * 领域模型转 PO（新增用）
     */
    default PointTransactionPO toPO(PointTransaction domain) {
        if (domain == null) {
            return null;
        }
        return PointTransactionPO.builder()
                .groupId(domain.getGroupId())
                .userId(domain.getUserId())
                .type(domain.getType())
                .amount(domain.getAmount())
                .referenceType(domain.getReferenceType())
                .referenceId(domain.getReferenceId())
                .balanceAfter(domain.getBalanceAfter())
                .createdAt(domain.getCreatedAt())
                .build();
    }
}
