package com.knowledgegame.admin.api.assembler;

import com.knowledgegame.admin.api.dto.response.AdminUserResponse;
import com.knowledgegame.core.domain.model.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * 管理端用户 DTO 转换器（MapStruct）
 */
@Mapper
public interface AdminUserAssembler {

    AdminUserAssembler INSTANCE = Mappers.getMapper(AdminUserAssembler.class);

    /**
     * 领域模型转响应 DTO（枚举转字符串）
     */
    @Mapping(target = "role", expression = "java(user.getRole().name())")
    AdminUserResponse toResponse(User user);
}
