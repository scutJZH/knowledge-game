package com.knowledgegame.core.domain.service.recyclebin;

import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.domain.model.domainenum.ResourceType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 回收站策略注册中心
 * <p>
 * Spring 注入所有策略 Bean，自动按 resourceType 注册到 EnumMap。
 * 这是回收站系统的核心扩展点：新增资源只需写一个策略 Bean，注册中心自动发现。
 */
public class RecycleBinItemStrategyRegistry {

    private final Map<ResourceType, RecycleBinItemStrategy<?>> registry = new EnumMap<>(ResourceType.class);

    /**
     * Spring 注入所有策略 Bean，自动按 resourceType 注册。
     * <p>
     * 本需求交付时 List 为空（无策略 Bean），Spring 对 List<T> 注入在无候选 Bean 时返回空列表，
     * 应用正常启动不报错。
     *
     * @param strategies 所有 RecycleBinItemStrategy 实现（Spring 自动注入）
     * @throws IllegalStateException 同类型重复注册
     */
    public RecycleBinItemStrategyRegistry(List<RecycleBinItemStrategy<?>> strategies) {
        for (RecycleBinItemStrategy<?> s : strategies) {
            RecycleBinItemStrategy<?> existing = registry.put(s.getResourceType(), s);
            if (existing != null) {
                throw new IllegalStateException("ResourceType " + s.getResourceType()
                        + " 已注册策略: " + existing.getClass().getName()
                        + "，新策略: " + s.getClass().getName());
            }
        }
    }

    /**
     * 按资源类型获取策略
     *
     * @param type 资源类型
     * @return 对应的策略实现
     * @throws BusinessException 该资源类型暂未接入回收站
     */
    public RecycleBinItemStrategy<?> get(ResourceType type) {
        RecycleBinItemStrategy<?> s = registry.get(type);
        if (s == null) {
            throw new BusinessException(501, "资源类型 " + type + " 暂未接入回收站");
        }
        return s;
    }

    /**
     * 当前已注册的资源类型（供前端目录树渲染 + 列表 resourceType 校验）
     */
    public Set<ResourceType> supportedTypes() {
        return Collections.unmodifiableSet(registry.keySet());
    }
}
