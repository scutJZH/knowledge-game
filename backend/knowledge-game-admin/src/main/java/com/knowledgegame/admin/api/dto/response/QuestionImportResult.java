package com.knowledgegame.admin.api.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 题目导入结果响应
 */
@Getter
@Builder
public class QuestionImportResult {

    /**
     * 总行数
     */
    private int totalCount;

    /**
     * 成功数
     */
    private int successCount;

    /**
     * 失败数
     */
    private int failCount;

    /**
     * 失败明细
     */
    private List<ImportFailDetail> failDetails;
}