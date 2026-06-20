package com.knowledgegame.admin.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * 批量永久删除结果 DTO
 */
@Getter
@AllArgsConstructor
public class BatchPurgeResult {

    private List<Long> successIds;
    private List<Failure> failures;

    /**
     * 单条永久删除失败的记录
     */
    @Getter
    @AllArgsConstructor
    public static class Failure {
        private Long id;
        private String errorMessage;
    }
}
