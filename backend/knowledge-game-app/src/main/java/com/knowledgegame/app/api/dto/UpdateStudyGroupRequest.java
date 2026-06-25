package com.knowledgegame.app.api.dto;

/**
 * 编辑群组信息请求
 */
public class UpdateStudyGroupRequest {

    private String name;
    private String description;
    private Long avatarFileId;
    private String joinPolicy;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Long getAvatarFileId() { return avatarFileId; }
    public void setAvatarFileId(Long avatarFileId) { this.avatarFileId = avatarFileId; }

    public String getJoinPolicy() { return joinPolicy; }
    public void setJoinPolicy(String joinPolicy) { this.joinPolicy = joinPolicy; }
}
