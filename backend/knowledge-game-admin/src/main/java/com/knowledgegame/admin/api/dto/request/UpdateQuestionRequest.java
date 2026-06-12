package com.knowledgegame.admin.api.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 更新题目请求 DTO（所有字段可选，null 表示不修改）
 */
@Getter
@Setter
public class UpdateQuestionRequest {

    @Size(max = 500, message = "题目内容不超过 500 字")
    private String content;

    private List<CreateQuestionRequest.OptionItem> options;

    private String answer;

    private Integer difficulty;

    private String explanation;

    private List<String> tags;
}