package com.knowledgegame.app.api.dto.response;

/**
 * 积分流水响应（个人跨群组视角，额外携带群组信息）
 */
public class PointTransactionCrossGroupResponse extends PointTransactionResponse {

    private String groupName;
    private String groupAvatarUrl;

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public String getGroupAvatarUrl() { return groupAvatarUrl; }
    public void setGroupAvatarUrl(String groupAvatarUrl) { this.groupAvatarUrl = groupAvatarUrl; }
}
