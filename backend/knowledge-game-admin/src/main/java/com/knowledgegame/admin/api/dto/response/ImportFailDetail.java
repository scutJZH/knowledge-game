package com.knowledgegame.admin.api.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * 导入失败明细
 */
@Getter
@Builder
public class ImportFailDetail {

    /**
     * 行号
     */
    private int row;

    /**
     * 失败原因
     */
    private String reason;
}