package com.knowledgegame.app.api.dto;

import java.util.List;

/**
 * 题目分页响应
 */
public class QuestionPageResponse {

    private List<QuestionListResponse> content;
    private long totalElements;
    private int totalPages;

    public List<QuestionListResponse> getContent() { return content; }
    public void setContent(List<QuestionListResponse> content) { this.content = content; }

    public long getTotalElements() { return totalElements; }
    public void setTotalElements(long totalElements) { this.totalElements = totalElements; }

    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }
}
