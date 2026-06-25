package com.knowledgegame.app.api.dto;

public class GroupIpLibraryResponse {

    private Long id;
    private Long groupId;
    private Long ipSeriesId;
    private String ipSeriesName;
    private String ipSeriesCode;
    private Long coverImageFileId;
    private String coverImageUrl;
    private Long addedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }

    public Long getIpSeriesId() { return ipSeriesId; }
    public void setIpSeriesId(Long ipSeriesId) { this.ipSeriesId = ipSeriesId; }

    public String getIpSeriesName() { return ipSeriesName; }
    public void setIpSeriesName(String ipSeriesName) { this.ipSeriesName = ipSeriesName; }

    public String getIpSeriesCode() { return ipSeriesCode; }
    public void setIpSeriesCode(String ipSeriesCode) { this.ipSeriesCode = ipSeriesCode; }

    public Long getCoverImageFileId() { return coverImageFileId; }
    public void setCoverImageFileId(Long coverImageFileId) { this.coverImageFileId = coverImageFileId; }

    public String getCoverImageUrl() { return coverImageUrl; }
    public void setCoverImageUrl(String coverImageUrl) { this.coverImageUrl = coverImageUrl; }

    public Long getAddedAt() { return addedAt; }
    public void setAddedAt(Long addedAt) { this.addedAt = addedAt; }
}
