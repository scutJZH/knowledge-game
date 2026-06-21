package com.knowledgegame.core.infrastructure.db.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegame.core.domain.model.entity.ScheduledTaskLog;
import com.knowledgegame.core.domain.model.vo.FailureDetail;
import com.knowledgegame.core.infrastructure.db.entity.ScheduledTaskLogPO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

/**
 * 定时任务执行日志 PO ↔ Domain 转换器（MapStruct）
 * <p>
 * failure_details 为 MySQL json 列，Java String 类型 ↔ List&lt;FailureDetail&gt;，
 * 由 default 方法手动处理 JSON 序列化/反序列化。
 */
@Mapper
public interface ScheduledTaskLogConverter {

    ScheduledTaskLogConverter INSTANCE = Mappers.getMapper(ScheduledTaskLogConverter.class);

    ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    Logger log = LoggerFactory.getLogger(ScheduledTaskLogConverter.class);

    /**
     * PO 转领域模型（手动处理 failure_details JSON 反序列化）
     */
    default ScheduledTaskLog toDomain(ScheduledTaskLogPO po) {
        return new ScheduledTaskLog(
                po.getId(),
                po.getTaskName(),
                po.getTaskDisplay(),
                po.getExecutedAt(),
                po.getDurationMs(),
                po.getTotalCount(),
                po.getSuccessCount(),
                po.getFailureCount(),
                parseFailureDetails(po.getFailureDetails()),
                po.getStatus()
        );
    }

    /**
     * 领域模型转 PO（手动处理 failure_details JSON 序列化）
     */
    default ScheduledTaskLogPO toPO(ScheduledTaskLog domain) {
        return ScheduledTaskLogPO.builder()
                .id(domain.getId())
                .taskName(domain.getTaskName())
                .taskDisplay(domain.getTaskDisplay())
                .executedAt(domain.getExecutedAt())
                .durationMs(domain.getDurationMs())
                .totalCount(domain.getTotalCount())
                .successCount(domain.getSuccessCount())
                .failureCount(domain.getFailureCount())
                .failureDetails(serializeFailureDetails(domain.getFailureDetails()))
                .status(domain.getStatus())
                .build();
    }

    /**
     * JSON 字符串 → List&lt;FailureDetail&gt;
     * <p>
     * null / blank → null（该字段 nullable）
     */
    private List<FailureDetail> parseFailureDetails(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<List<FailureDetailData>>() {})
                    .stream()
                    .map(d -> new FailureDetail(d.recycleBinId(), d.resourceType(), d.name(), d.reason()))
                    .toList();
        } catch (JsonProcessingException e) {
            log.warn("failure_details JSON 解析失败: {}", json, e);
            return Collections.emptyList();
        }
    }

    /**
     * List&lt;FailureDetail&gt; → JSON 字符串
     * <p>
     * null → null
     */
    private String serializeFailureDetails(List<FailureDetail> details) {
        if (details == null) {
            return null;
        }
        try {
            List<FailureDetailData> data = details.stream()
                    .map(d -> new FailureDetailData(d.recycleBinId(), d.resourceType(), d.name(), d.reason()))
                    .toList();
            return OBJECT_MAPPER.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            log.error("failure_details JSON 序列化失败", e);
            return null;
        }
    }

    /**
     * JSON 序列化/反序列化的中间 DTO（避免依赖 FailureDetail record 的 Jackson 兼容性）
     */
    record FailureDetailData(Long recycleBinId, String resourceType, String name, String reason) {}
}
