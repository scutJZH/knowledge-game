package com.knowledgegame.admin.api.assembler;

import com.knowledgegame.admin.api.dto.response.IpSeriesResponse;
import com.knowledgegame.domain.model.entity.IpSeries;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * IP 系列领域模型 → DTO 转换器（MapStruct 自动生成实现）
 */
@Mapper
public interface IpSeriesAssembler {

    IpSeriesAssembler INSTANCE = Mappers.getMapper(IpSeriesAssembler.class);

    /**
     * 领域模型转响应 DTO
     */
    IpSeriesResponse toResponse(IpSeries ipSeries);
}
