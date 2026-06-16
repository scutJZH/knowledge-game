package com.knowledgegame.file.application;

import com.knowledgegame.components.feign.dto.FileInfoResponse;
import com.knowledgegame.file.domain.model.FileInfo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * FileInfo → DTO Assembler
 */
@Mapper
public interface FileInfoAssembler {

    FileInfoAssembler INSTANCE = Mappers.getMapper(FileInfoAssembler.class);

    @Mapping(target = "fileId", source = "id")
    @Mapping(target = "basePath", source = "basePath")
    @Mapping(target = "uploaderId", source = "uploaderId")
    @Mapping(target = "metadata", source = "metadata")
    FileInfoResponse toResponse(FileInfo fileInfo);

    List<FileInfoResponse> toResponseList(List<FileInfo> fileInfos);
}
