package com.knowledgegame.app.api.dto.response;

import java.util.List;

/**
 * 积分流水分页响应（泛型，适配群组视角和个人视角两种响应类型）
 */
public class PointTransactionPageResponse<T> {

    private List<T> content;
    private long totalElements;
    private int totalPages;

    public PointTransactionPageResponse() {}

    public PointTransactionPageResponse(List<T> content, long totalElements, int totalPages) {
        this.content = content;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
    }

    public List<T> getContent() { return content; }
    public void setContent(List<T> content) { this.content = content; }

    public long getTotalElements() { return totalElements; }
    public void setTotalElements(long totalElements) { this.totalElements = totalElements; }

    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }
}
