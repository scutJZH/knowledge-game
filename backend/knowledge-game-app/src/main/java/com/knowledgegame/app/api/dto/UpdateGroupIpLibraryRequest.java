package com.knowledgegame.app.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public class UpdateGroupIpLibraryRequest {

    @NotNull
    @Size(max = 100)
    private List<Long> ipSeriesIds;

    public List<Long> getIpSeriesIds() { return ipSeriesIds; }
    public void setIpSeriesIds(List<Long> ipSeriesIds) { this.ipSeriesIds = ipSeriesIds; }
}
