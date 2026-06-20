package com.knowledgegame.app.application.assembler;

import com.knowledgegame.app.api.dto.GroupMemberResponse;
import com.knowledgegame.core.domain.model.entity.GroupMember;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 群组成员 MapStruct Assembler（领域实体 → DTO）
 */
@Mapper
public interface GroupMemberAssembler {

    GroupMemberAssembler INSTANCE = Mappers.getMapper(GroupMemberAssembler.class);

    @Mapping(target = "role", expression = "java(member.getRole() != null ? member.getRole().name() : null)")
    @Mapping(target = "joinedAt", expression = "java(toEpochMilli(member.getJoinedAt()))")
    GroupMemberResponse toResponse(GroupMember member);

    default Long toEpochMilli(LocalDateTime dateTime) {
        if (dateTime == null) return null;
        return dateTime.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
    }
}
