package com.knowledgegame.app.application.assembler;

import com.knowledgegame.app.api.dto.StudyGroupListResponse;
import com.knowledgegame.app.api.dto.StudyGroupResponse;
import com.knowledgegame.core.domain.model.entity.StudyGroup;
import com.knowledgegame.core.domain.model.vo.FileRef;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 学习群组 MapStruct Assembler（领域实体 → DTO）
 */
@Mapper
public interface StudyGroupAssembler {

    StudyGroupAssembler INSTANCE = Mappers.getMapper(StudyGroupAssembler.class);

    @Mapping(target = "avatarFileId", expression = "java(fileIdOf(group.getAvatar()))")
    @Mapping(target = "avatarUrl", expression = "java(urlOf(group.getAvatar()))")
    @Mapping(target = "joinPolicy", expression = "java(group.getJoinPolicy() != null ? group.getJoinPolicy().name() : null)")
    @Mapping(target = "inviteCode", expression = "java(group.getInviteCodeValue())")
    @Mapping(target = "createdAt", expression = "java(toEpochMilli(group.getCreatedAt()))")
    @Mapping(target = "updatedAt", expression = "java(toEpochMilli(group.getUpdatedAt()))")
    StudyGroupResponse toResponse(StudyGroup group);

    /**
     * 转为列表响应 DTO（含 myRole 和 memberCount）
     */
    default StudyGroupListResponse toListResponse(StudyGroup group, String myRole, int memberCount) {
        StudyGroupListResponse response = new StudyGroupListResponse();
        response.setId(group.getId());
        response.setName(group.getName());
        response.setDescription(group.getDescription());
        response.setAvatarFileId(fileIdOf(group.getAvatar()));
        response.setAvatarUrl(urlOf(group.getAvatar()));
        response.setOwnerId(group.getOwnerId());
        response.setJoinPolicy(group.getJoinPolicy() != null ? group.getJoinPolicy().name() : null);
        response.setMyRole(myRole);
        response.setMemberCount(memberCount);
        response.setCreatedAt(toEpochMilli(group.getCreatedAt()));
        response.setUpdatedAt(toEpochMilli(group.getUpdatedAt()));
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
