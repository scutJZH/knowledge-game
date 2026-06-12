package com.knowledgegame.admin.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 创建题目请求 DTO
 */
@Getter
@Setter
public class CreateQuestionRequest {

    @NotBlank(message = "题型不能为空")
    private String type;

    @NotBlank(message = "题目内容不能为空")
    @Size(max = 500, message = "题目内容不超过 500 字")
    private String content;

    private List<OptionItem> options;

    @NotBlank(message = "答案不能为空")
    private String answer;

    @NotNull(message = "难度不能为空")
    private Integer difficulty;

    private String explanation;

    private List<String> tags;

    private List<Long> categoryIds;

    /**
     * 选项 DTO
     */
    @Getter
    @Setter
    public static class OptionItem {
        private String key;
        private String content;
    }
}