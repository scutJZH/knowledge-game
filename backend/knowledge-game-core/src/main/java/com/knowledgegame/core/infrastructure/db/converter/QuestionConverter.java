package com.knowledgegame.core.infrastructure.db.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegame.core.domain.model.entity.Question;
import com.knowledgegame.core.domain.model.vo.QuestionOption;
import com.knowledgegame.core.infrastructure.db.entity.QuestionPO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.factory.Mappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

/**
 * PO ↔ 领域模型转换器（题目，MapStruct + 手动 JSON 转换）
 */
@Mapper(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface QuestionConverter {

    QuestionConverter INSTANCE = Mappers.getMapper(QuestionConverter.class);

    ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    Logger log = LoggerFactory.getLogger(QuestionConverter.class);

    /**
     * PO 转领域模型（使用 reconstruct 工厂方法，手动处理 JSON 字段）
     */
    default Question toDomain(QuestionPO po) {
        if (po == null) {
            return null;
        }
        return Question.reconstruct(
                po.getId(),
                po.getType(),
                po.getContent(),
                parseOptions(po.getOptions()),
                po.getAnswer(),
                po.getDifficulty(),
                po.getExplanation(),
                parseTags(po.getTags()),
                po.getStatus(),
                po.getCreatedAt(),
                po.getUpdatedAt()
        );
    }

    /**
     * 领域模型转 PO（新增）
     */
    default QuestionPO toPO(Question question) {
        if (question == null) {
            return null;
        }
        QuestionPO po = new QuestionPO();
        po.setId(question.getId());
        po.setType(question.getType());
        po.setContent(question.getContent());
        po.setOptions(serializeOptions(question.getOptions()));
        po.setAnswer(question.getAnswer());
        po.setDifficulty(question.getDifficulty());
        po.setExplanation(question.getExplanation());
        po.setTags(serializeTags(question.getTags()));
        po.setStatus(question.getStatus());
        po.setCreatedAt(question.getCreatedAt());
        po.setUpdatedAt(question.getUpdatedAt());
        return po;
    }

    /**
     * 用领域模型更新已有 PO（忽略 null 字段）
     */
    default void updatePO(@MappingTarget QuestionPO po, Question question) {
        if (question.getType() != null) po.setType(question.getType());
        if (question.getContent() != null) po.setContent(question.getContent());
        po.setOptions(serializeOptions(question.getOptions()));
        po.setAnswer(question.getAnswer());
        if (question.getDifficulty() != null) po.setDifficulty(question.getDifficulty());
        po.setExplanation(question.getExplanation());
        po.setTags(serializeTags(question.getTags()));
        if (question.getStatus() != null) po.setStatus(question.getStatus());
        po.setUpdatedAt(question.getUpdatedAt());
    }

    /**
     * 解析选项 JSON 为 QuestionOption 列表
     */
    private List<QuestionOption> parseOptions(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            List<QuestionOptionData> data = OBJECT_MAPPER.readValue(json,
                    new TypeReference<List<QuestionOptionData>>() {});
            return data.stream()
                    .map(d -> QuestionOption.of(d.key(), d.content()))
                    .toList();
        } catch (JsonProcessingException e) {
            log.warn("选项 JSON 解析失败: {}", json, e);
            return Collections.emptyList();
        }
    }

    /**
     * 序列化 QuestionOption 列表为 JSON
     */
    private String serializeOptions(List<QuestionOption> options) {
        if (options == null) {
            return null;
        }
        try {
            List<QuestionOptionData> data = options.stream()
                    .map(o -> new QuestionOptionData(o.getKey(), o.getContent()))
                    .toList();
            return OBJECT_MAPPER.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * 解析标签 JSON 为字符串列表
     */
    private List<String> parseTags(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("标签 JSON 解析失败: {}", json, e);
            return Collections.emptyList();
        }
    }

    /**
     * 序列化标签列表为 JSON
     */
    private String serializeTags(List<String> tags) {
        if (tags == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(tags);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * JSON 反序列化中间结构
     */
    record QuestionOptionData(String key, String content) {}
}
