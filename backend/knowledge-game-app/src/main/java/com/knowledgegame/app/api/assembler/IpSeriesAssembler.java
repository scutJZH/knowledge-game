package com.knowledgegame.app.api.assembler;

import com.knowledgegame.app.api.dto.response.ActiveIpSeriesResponse;
import com.knowledgegame.core.domain.model.entity.IpSeries;
import com.knowledgegame.core.domain.model.vo.FileRef;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * IP 系列领域模型 → 用户端 DTO 转换器
 */
@Mapper
public interface IpSeriesAssembler {

    IpSeriesAssembler INSTANCE = Mappers.getMapper(IpSeriesAssembler.class);

    /**
     * 领域模型转 ACTIVE IP 系列响应
     */
    @Mapping(target = "coverImageFileId", expression = "java(fileIdOf(ipSeries.getCoverImage()))")
    @Mapping(target = "coverImageUrl", expression = "java(urlOf(ipSeries.getCoverImage()))")
    ActiveIpSeriesResponse toActiveResponse(IpSeries ipSeries);

    /**
     * 批量转换
     */
    List<ActiveIpSeriesResponse> toActiveResponseList(List<IpSeries> ipSeriesList);

    default Long fileIdOf(FileRef ref) {
        return ref != null ? ref.fileId() : null;
    }

    default String urlOf(FileRef ref) {
        return ref != null ? ref.url() : null;
    }
}
