package com.knowledgegame.core.infrastructure.db.converter;

import com.knowledgegame.core.domain.model.entity.RecycleBinItem;
import com.knowledgegame.core.infrastructure.db.entity.RecycleBinItemPO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * 回收站条目 PO ↔ Domain 转换器（MapStruct）
 * <p>
 * resourceType 在 PO 和 Domain 均为枚举，MapStruct 直接映射无需自定义。
 * 时间字段均为 LocalDateTime，无需转换。
 */
@Mapper
public interface RecycleBinItemConverter {

    RecycleBinItemConverter INSTANCE = Mappers.getMapper(RecycleBinItemConverter.class);

    /**
     * PO 转领域模型
     */
    RecycleBinItem toDomain(RecycleBinItemPO po);

    /**
     * 领域模型转 PO
     */
    RecycleBinItemPO toPO(RecycleBinItem item);
}
