package com.knowledgegame.core.infrastructure.db.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledgegame.core.domain.model.entity.KnowledgeItem;
import com.knowledgegame.core.domain.model.vo.FileRef;
import com.knowledgegame.core.infrastructure.db.entity.KnowledgeItemPO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.factory.Mappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

/**
 * PO ↔ 领域模型转换器（知识条目，MapStruct + 手动 JSON 转换）
 */
@Mapper(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface KnowledgeItemConverter {

    KnowledgeItemConverter INSTANCE = Mappers.getMapper(KnowledgeItemConverter.class);

    ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    Logger log = LoggerFactory.getLogger(KnowledgeItemConverter.class);

    /**
     * PO 转领域模型（使用 reconstruct 工厂方法，手动处理 JSON 字段和封面图）
     */
    default KnowledgeItem toDomain(KnowledgeItemPO po) {
        if (po == null) {
            return null;
        }
        return KnowledgeItem.reconstruct(
                po.getId(),
                po.getTitle(),
                po.getContent(),
                po.getContentHtml(),
                toFileRef(po.getCoverImageFileId(), po.getCoverImageUrl()),
                parseTags(po.getTags()),
                po.getSortOrder(),
                po.getStatus(),
                po.getCreatedAt(),
                po.getUpdatedAt()
        );
    }

    /**
     * 领域模型转 PO（新增）
     */
    default KnowledgeItemPO toPO(KnowledgeItem item) {
        if (item == null) {
            return null;
        }
        KnowledgeItemPO po = new KnowledgeItemPO();
        po.setId(item.getId());
        po.setTitle(item.getTitle());
        po.setContent(item.getContent());
        po.setContentHtml(item.getContentHtml());
        if (item.getCoverImage() != null) {
            po.setCoverImageFileId(item.getCoverImage().fileId());
            po.setCoverImageUrl(item.getCoverImage().url());
        }
        po.setTags(serializeTags(item.getTags()));
        po.setSortOrder(item.getSortOrder());
        po.setStatus(item.getStatus());
        po.setCreatedAt(item.getCreatedAt());
        po.setUpdatedAt(item.getUpdatedAt());
        return po;
    }

    /**
     * 用领域模型更新已有 PO（忽略 null 字段）
     */
    default void updatePO(@MappingTarget KnowledgeItemPO po, KnowledgeItem item) {
        if (item.getTitle() != null) po.setTitle(item.getTitle());
        if (item.getContent() != null) po.setContent(item.getContent());
        if (item.getContentHtml() != null) po.setContentHtml(item.getContentHtml());
        // FileRef 双字段：仅当领域实体携带新的 FileRef 时才更新 PO（null 表示不修改）
        if (item.getCoverImage() != null) {
            po.setCoverImageFileId(item.getCoverImage().fileId());
            po.setCoverImageUrl(item.getCoverImage().url());
        }
        po.setTags(serializeTags(item.getTags()));
        po.setSortOrder(item.getSortOrder());
        if (item.getStatus() != null) po.setStatus(item.getStatus());
        po.setUpdatedAt(item.getUpdatedAt());
    }

    /**
     * fileId + url 双字段 → FileRef 值对象
     */
    private FileRef toFileRef(Long fileId, String url) {
        if (fileId == null && url == null) {
            return null;
        }
        return FileRef.of(fileId, url);
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
}
