package com.knowledgegame.app.api.dto;

import jakarta.validation.constraints.NotNull;

public class TransferOwnershipRequest {

    @NotNull
    private Long toUserId;

    public Long getToUserId() {
        return toUserId;
    }

    public void setToUserId(Long toUserId) {
        this.toUserId = toUserId;
    }
}
