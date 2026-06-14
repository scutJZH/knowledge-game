package com.knowledgegame.admin.api.assembler;

import com.knowledgegame.admin.api.dto.response.QuestionResponse;
import com.knowledgegame.core.domain.model.entity.Question;
import com.knowledgegame.core.domain.model.vo.QuestionOption;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

/**
 * 题目领域模型 → DTO 转换器
 */
@Mapper
public interface QuestionAssembler {

    QuestionAssembler INSTANCE = Mappers.getMapper(QuestionAssembler.class);

    /**
     * 领域模型转响应 DTO
     */
    @Mapping(target = "type", expression = "java(question.getType().name())")
    @Mapping(target = "difficulty", expression = "java(question.getDifficulty().getLevel())")
    @Mapping(target = "status", expression = "java(question.getStatus().name())")
    @Mapping(target = "options", expression = "java(toOptionItems(question.getOptions()))")
    @Mapping(target = "categoryIds", ignore = true)
    @Mapping(target = "createdAt", expression = "java(toEpochMilli(question.getCreatedAt()))")
    @Mapping(target = "updatedAt", expression = "java(toEpochMilli(question.getUpdatedAt()))")
    QuestionResponse toResponse(Question question);

    /**
     * LocalDateTime 转 epoch 毫秒（UTC）
     */
    default Long toEpochMilli(LocalDateTime dateTime) {
        if (dateTime == null) return null;
        return dateTime.atZone(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    /**
     * 选项值对象转 DTO
     */
    default List<QuestionResponse.OptionItem> toOptionItems(List<QuestionOption> options) {
        if (options == null) {
            return Collections.emptyList();
        }
        return options.stream()
                .map(o -> QuestionResponse.OptionItem.builder()
                        .key(o.getKey())
                        .content(o.getContent())
                        .build())
                .toList();
    }
}