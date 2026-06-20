package com.knowledgegame.admin.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * 批量恢复结果 DTO
 */
@Getter
@AllArgsConstructor
public class BatchRestoreResult {

    private List<Long> successIds;
    private List<Failure> failures;

    /**
     * 单条恢复失败的记录
     */
    @Getter
    @AllArgsConstructor
    public static class Failure {
        private Long id;
        private String errorMessage;
    }
}
