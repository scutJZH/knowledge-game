package com.knowledgegame.core.domain.model.vo;

import com.knowledgegame.core.domain.model.domainenum.ResourceType;

/**
 * 删除快照值对象标记接口
 * <p>
 * 每种资源类型实现自己的快照类，定义序列化和反序列化契约。
 * 具体实现由 REQ-104~108 各资源补充。
 */
public interface DeletedSnapshot {

    /**
     * 快照所属的资源类型
     */
    ResourceType getResourceType();

    /**
     * 原表 ID（恢复时用）
     */
    Long getOriginalId();

    /**
     * 将快照序列化为 JSON（存入 _deleted 表的 related_data 字段）
     */
    String toJson();
}
