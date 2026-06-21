package com.knowledgegame.admin.api.assembler;

import com.knowledgegame.admin.api.dto.response.FailureDetailResponse;
import com.knowledgegame.admin.api.dto.response.ScheduledTaskLogResponse;
import com.knowledgegame.core.domain.model.entity.ScheduledTaskLog;
import com.knowledgegame.core.domain.model.vo.FailureDetail;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 定时任务执行日志 Domain → Response 转换器（MapStruct）
 */
@Mapper
public interface ScheduledTaskLogAssembler {

    ScheduledTaskLogAssembler INSTANCE = Mappers.getMapper(ScheduledTaskLogAssembler.class);

    @Mapping(target = "executedAt", source = "executedAt", qualifiedByName = "toEpochMilli")
    ScheduledTaskLogResponse toResponse(ScheduledTaskLog entity);

    @Named("toEpochMilli")
    default Long toEpochMilli(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /**
     * FailureDetail → FailureDetailResponse 映射
     * <p>
     * MapStruct 自动按字段名匹配生成实现。此声明让 MapStruct 识别
     * List&lt;FailureDetail&gt; → List&lt;FailureDetailResponse&gt; 的转换路径。
     */
    FailureDetailResponse toResponse(FailureDetail detail);
}
