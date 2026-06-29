package com.knowledgegame.app.api.dto;

import com.knowledgegame.core.domain.model.domainenum.GroupIpLibraryStatus;
import jakarta.validation.constraints.NotNull;

/**
 * 更新群组 IP 库单个关联状态请求
 */
public class UpdateGroupIpLibraryStatusRequest {

    @NotNull
    private GroupIpLibraryStatus status;

    public GroupIpLibraryStatus getStatus() { return status; }
    public void setStatus(GroupIpLibraryStatus status) { this.status = status; }
}
