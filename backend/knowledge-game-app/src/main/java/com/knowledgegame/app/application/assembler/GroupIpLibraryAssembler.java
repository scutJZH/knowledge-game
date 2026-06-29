package com.knowledgegame.app.application.assembler;

import com.knowledgegame.app.api.dto.GroupIpLibraryResponse;
import com.knowledgegame.core.domain.model.entity.GroupIpLibrary;
import com.knowledgegame.core.domain.model.entity.IpSeries;
import com.knowledgegame.core.domain.model.vo.FileRef;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 群组 IP 库关联 MapStruct Assembler（领域实体 → DTO）
 */
@Mapper
public interface GroupIpLibraryAssembler {

    GroupIpLibraryAssembler INSTANCE = Mappers.getMapper(GroupIpLibraryAssembler.class);

    default GroupIpLibraryResponse toResponse(GroupIpLibrary item, IpSeries ipSeries) {
        GroupIpLibraryResponse response = new GroupIpLibraryResponse();
        response.setId(item.getId());
        response.setGroupId(item.getGroupId());
        response.setIpSeriesId(item.getIpSeriesId());
        if (ipSeries != null) {
            response.setIpSeriesName(ipSeries.getName());
            response.setIpSeriesCode(ipSeries.getCode());
            response.setCoverImageFileId(fileIdOf(ipSeries.getCoverImage()));
            response.setCoverImageUrl(urlOf(ipSeries.getCoverImage()));
        }
        response.setStatus(item.getStatus().name());
        response.setAddedAt(toEpochMilli(item.getAddedAt()));
        return response;
    }

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
