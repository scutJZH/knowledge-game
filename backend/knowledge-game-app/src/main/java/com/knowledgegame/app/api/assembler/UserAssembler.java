package com.knowledgegame.app.api.assembler;

import com.knowledgegame.app.api.dto.response.UserResponse;
import com.knowledgegame.core.domain.model.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * 领域模型 ↔ DTO 转换器（MapStruct）
 */
@Mapper
public interface UserAssembler {

    UserAssembler INSTANCE = Mappers.getMapper(UserAssembler.class);

    /**
     * 领域模型转响应 DTO（枚举转字符串）
     */
    @Mapping(target = "role", expression = "java(user.getRole().name())")
    UserResponse toResponse(User user);
}
