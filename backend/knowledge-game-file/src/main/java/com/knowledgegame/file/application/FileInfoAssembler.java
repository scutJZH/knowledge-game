package com.knowledgegame.file.application;

import com.knowledgegame.file.api.dto.FileInfoResponse;
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
    @Mapping(target = "bizType", expression = "java(fileInfo.getBizType().name())")
    @Mapping(target = "uploaderId", source = "uploaderId")
    FileInfoResponse toResponse(FileInfo fileInfo);

    List<FileInfoResponse> toResponseList(List<FileInfo> fileInfos);
}
