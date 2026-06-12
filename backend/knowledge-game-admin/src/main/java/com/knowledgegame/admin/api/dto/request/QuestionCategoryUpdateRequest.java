package com.knowledgegame.admin.api.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 题目分类关联更新请求 DTO（全量替换）
 */
@Getter
@Setter
public class QuestionCategoryUpdateRequest {

    /**
     * 分类 ID 列表（全量替换）
     */
    private List<Long> categoryIds;
}