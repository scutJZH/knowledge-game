package com.knowledgegame.admin.api.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 卡牌模板导入结果响应
 */
@Getter
@Builder
public class CardTemplateImportResult {

    /** 总行数 */
    private int totalCount;

    /** 成功数 */
    private int successCount;

    /** 失败数 */
    private int failCount;

    /** 失败明细（复用 ImportFailDetail） */
    private List<ImportFailDetail> failDetails;
}
