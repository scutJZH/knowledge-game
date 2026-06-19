package com.knowledgegame.admin.api.assembler;

import com.knowledgegame.admin.api.dto.response.RecycleBinItemResponse;
import com.knowledgegame.core.domain.model.entity.RecycleBinItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 回收站条目领域模型 → DTO 转换器
 */
@Mapper
public interface RecycleBinItemAssembler {

    RecycleBinItemAssembler INSTANCE = Mappers.getMapper(RecycleBinItemAssembler.class);

    /**
     * 领域模型转响应 DTO
     * <p>
     * resourceType 枚举直接映射 name() 字符串，
     * displayName 中文走 expression；
     * 时间字段统一转 epoch 毫秒；
     * daysUntilPurge 由 Assembler 计算。
     */
    // resourceType.name() → String（MapStruct 自动调用 toString/name）
    @Mapping(target = "resourceType", expression = "java(item.getResourceType().name())")
    // displayName() 中文映射
    @Mapping(target = "resourceTypeDisplay", expression = "java(item.getResourceType().displayName())")
    // 计算列
    @Mapping(target = "daysUntilPurge", expression = "java(calcDaysUntilPurge(item.getRestoreDeadline()))")
    // 时间字段 → epoch 毫秒
    @Mapping(target = "originalCreatedAt", source = "originalCreatedAt", qualifiedByName = "toEpochMilli")
    @Mapping(target = "originalUpdatedAt", source = "originalUpdatedAt", qualifiedByName = "toEpochMilli")
    @Mapping(target = "deletedAt", source = "deletedAt", qualifiedByName = "toEpochMilli")
    @Mapping(target = "restoreDeadline", source = "restoreDeadline", qualifiedByName = "toEpochMilli")
    RecycleBinItemResponse toResponse(RecycleBinItem item);

    /**
     * LocalDateTime 转 epoch 毫秒
     */
    @Named("toEpochMilli")
    default Long toEpochMilli(LocalDateTime time) {
        if (time == null) return null;
        return time.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    /**
     * 计算剩余保留天数（向上取整）
     * <p>
     * 时间源使用 System.currentTimeMillis()（项目惯例）。
     * restoreDeadline 为 null 时返回 0（防御性兜底）。
     */
    default Integer calcDaysUntilPurge(LocalDateTime restoreDeadline) {
        if (restoreDeadline == null) return 0;
        long diffMs = restoreDeadline.atZone(ZoneOffset.UTC).toInstant().toEpochMilli()
                - System.currentTimeMillis();
        return (int) Math.max(0, (diffMs + 86_399_999) / 86_400_000);
    }
}
