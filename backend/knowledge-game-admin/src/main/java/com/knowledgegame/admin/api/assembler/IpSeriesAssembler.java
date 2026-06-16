package com.knowledgegame.admin.api.assembler;

import com.knowledgegame.admin.api.dto.response.IpSeriesResponse;
import com.knowledgegame.core.domain.model.entity.IpSeries;
import com.knowledgegame.core.domain.model.vo.FileRef;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * IP 系列领域模型 → DTO 转换器（MapStruct 自动生成实现）
 */
@Mapper
public interface IpSeriesAssembler {

    IpSeriesAssembler INSTANCE = Mappers.getMapper(IpSeriesAssembler.class);

    /**
     * 领域模型转响应 DTO
     */
    @Mapping(target = "coverImageFileId", expression = "java(fileIdOf(ipSeries.getCoverImage()))")
    @Mapping(target = "coverImageUrl", expression = "java(urlOf(ipSeries.getCoverImage()))")
    @Mapping(target = "createdAt", expression = "java(toEpochMilli(ipSeries.getCreatedAt()))")
    @Mapping(target = "updatedAt", expression = "java(toEpochMilli(ipSeries.getUpdatedAt()))")
    IpSeriesResponse toResponse(IpSeries ipSeries);

    /**
     * LocalDateTime 转 epoch 毫秒（UTC）
     */
    default Long toEpochMilli(LocalDateTime dateTime) {
        if (dateTime == null) return null;
        return dateTime.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    default Long fileIdOf(FileRef ref) {
        return ref != null ? ref.fileId() : null;
    }

    default String urlOf(FileRef ref) {
        return ref != null ? ref.url() : null;
    }
}
