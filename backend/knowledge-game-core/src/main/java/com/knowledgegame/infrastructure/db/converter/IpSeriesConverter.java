package com.knowledgegame.infrastructure.db.converter;

import com.knowledgegame.domain.model.entity.IpSeries;
import com.knowledgegame.infrastructure.db.entity.IpSeriesPO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.factory.Mappers;

/**
 * PO ↔ 领域模型转换器（IP 系列，MapStruct 自动生成实现）
 */
@Mapper(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface IpSeriesConverter {

    IpSeriesConverter INSTANCE = Mappers.getMapper(IpSeriesConverter.class);

    /**
     * PO 转领域模型（使用 reconstruct 工厂方法，MapStruct 无法自动映射无构造器的领域实体）
     */
    default IpSeries toDomain(IpSeriesPO po) {
        if (po == null) {
            return null;
        }
        return IpSeries.reconstruct(
                po.getId(),
                po.getCode(),
                po.getName(),
                po.getDescription(),
                po.getCoverImageUrl(),
                po.getStatus(),
                po.getCreatedAt(),
                po.getUpdatedAt()
        );
    }

    /**
     * 领域模型转 PO（新增）
     */
    IpSeriesPO toPO(IpSeries ipSeries);

    /**
     * 用领域模型更新已有 PO（忽略 null 字段）
     */
    void updatePO(@MappingTarget IpSeriesPO po, IpSeries ipSeries);
}
