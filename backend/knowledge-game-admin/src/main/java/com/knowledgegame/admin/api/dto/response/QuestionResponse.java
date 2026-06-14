package com.knowledgegame.admin.api.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 题目响应 DTO
 */
@Getter
@Builder
public class QuestionResponse {

    private Long id;
    private String type;
    private String content;
    private List<OptionItem> options;
    private String answer;
    private String explanation;
    private Integer difficulty;
    private List<String> tags;
    private String status;
    private List<Long> categoryIds;
    private Long createdAt;
    private Long updatedAt;

    /**
     * 选项响应
     */
    @Getter
    @Builder
    public static class OptionItem {
        private String key;
        private String content;
    }
}