package com.knowledgegame.core.infrastructure.db.converter;

import com.knowledgegame.core.domain.model.entity.GroupIpLibrary;
import com.knowledgegame.core.infrastructure.db.entity.GroupIpLibraryPO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * PO ↔ 领域模型转换器（群组 IP 库关联，MapStruct 自动生成实现）
 */
@Mapper
public interface GroupIpLibraryConverter {

    GroupIpLibraryConverter INSTANCE = Mappers.getMapper(GroupIpLibraryConverter.class);

    default GroupIpLibrary toDomain(GroupIpLibraryPO po) {
        if (po == null) {
            return null;
        }
        return GroupIpLibrary.reconstruct(
                po.getId(),
                po.getGroupId(),
                po.getIpSeriesId(),
                po.getStatus(),
                po.getAddedAt()
        );
    }

    default GroupIpLibraryPO toPO(GroupIpLibrary domain) {
        if (domain == null) {
            return null;
        }
        return new GroupIpLibraryPO()
                .setGroupId(domain.getGroupId())
                .setIpSeriesId(domain.getIpSeriesId())
                .setStatus(domain.getStatus())
                .setAddedAt(domain.getAddedAt());
    }

    default List<GroupIpLibrary> toDomainList(List<GroupIpLibraryPO> poList) {
        if (poList == null) {
            return List.of();
        }
        return poList.stream().map(this::toDomain).toList();
    }
}
