package com.knowledgegame.admin.application.service;

import com.knowledgegame.admin.api.assembler.ScheduledTaskLogAssembler;
import com.knowledgegame.admin.api.dto.request.ScheduledTaskLogListRequest;
import com.knowledgegame.admin.api.dto.response.ScheduledTaskLogResponse;
import com.knowledgegame.core.domain.model.entity.ScheduledTaskLog;
import com.knowledgegame.core.domain.model.vo.PageResult;
import com.knowledgegame.core.domain.port.outbound.ScheduledTaskLogRepositoryPort;
import org.springframework.stereotype.Service;

/**
 * 定时任务执行日志 AppService
 */
@Service
public class ScheduledTaskLogAppService {

    private final ScheduledTaskLogRepositoryPort repositoryPort;

    public ScheduledTaskLogAppService(ScheduledTaskLogRepositoryPort repositoryPort) {
        this.repositoryPort = repositoryPort;
    }

    /**
     * 分页查询定时任务执行日志
     */
    public PageResult<ScheduledTaskLogResponse> list(ScheduledTaskLogListRequest request) {
        PageResult<ScheduledTaskLog> result = repositoryPort.findAll(
                request.getTaskName(), request.getPage(), request.getSize());
        return PageResult.<ScheduledTaskLogResponse>builder()
                .content(result.getContent().stream()
                        .map(ScheduledTaskLogAssembler.INSTANCE::toResponse).toList())
                .totalElements(result.getTotalElements())
                .pageNumber(result.getPageNumber())
                .pageSize(result.getPageSize())
                .totalPages(result.getTotalPages())
                .build();
    }
}
