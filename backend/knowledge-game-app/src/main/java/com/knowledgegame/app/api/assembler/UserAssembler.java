package com.knowledgegame.app.api.assembler;

import com.knowledgegame.app.api.dto.response.UserResponse;
import com.knowledgegame.core.domain.model.entity.User;
import com.knowledgegame.core.domain.model.vo.FileRef;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface UserAssembler {

    UserAssembler INSTANCE = Mappers.getMapper(UserAssembler.class);

    @Mapping(target = "avatarFileId", expression = "java(fileIdOf(user.getAvatar()))")
    @Mapping(target = "avatarUrl", expression = "java(urlOf(user.getAvatar()))")
    @Mapping(target = "role", expression = "java(user.getRole().name())")
    UserResponse toResponse(User user);

    default Long fileIdOf(FileRef ref) {
        return ref != null ? ref.fileId() : null;
    }

    default String urlOf(FileRef ref) {
        return ref != null ? ref.url() : null;
    }
}
