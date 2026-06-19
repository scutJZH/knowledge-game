package com.knowledgegame.core.domain.service.recyclebin;

import com.knowledgegame.core.domain.model.domainenum.ResourceType;

import java.util.List;

/**
 * 回收站条目策略接口
 * <p>
 * 每种资源类型提供一个实现（策略 Bean），由注册中心自动发现和分派。
 * 单条操作由各 REQ-104~108 实现；批量操作提供默认实现（循环调单条），子类可覆盖为批量 SQL。
 *
 * @param <T> 该策略处理的领域实体类型
 */
public interface RecycleBinItemStrategy<T> {

    /**
     * 该策略处理的资源类型（注册 key）
     */
    ResourceType getResourceType();

    // ===== 单条操作（具体实现由 REQ-104~108 各资源补）=====

    /**
     * 删除前校验：被引用/有子节点则抛 BusinessException
     *
     * @param originalId 原表 ID
     */
    void validateDeletable(Long originalId);

    /**
     * 序列化原领域对象 + 关联数据 → 写入对应 _deleted 详情表 + 总览表登记
     *
     * @param originalId 原表 ID
     * @param deletedBy  删除人 admin username
     */
    void moveToRecycleBin(Long originalId, String deletedBy);

    /**
     * 从 _deleted 详情表读快照 → 校验关联仍存在 → INSERT 原表（强制 INACTIVE）→ DELETE _deleted
     *
     * @param recycleBinId 回收站总览表 ID
     */
    void restore(Long recycleBinId);

    /**
     * 物理删除 _deleted 详情表记录（+ 通过 ResourceType.toBizTypes() 清理关联文件）
     *
     * @param recycleBinId 回收站总览表 ID
     */
    void purge(Long recycleBinId);

    // ===== 批量操作（默认实现：循环调单条，子类可覆盖为批量 SQL）=====

    /**
     * 批量恢复
     */
    default void batchRestore(List<Long> recycleBinIds) {
        recycleBinIds.forEach(this::restore);
    }

    /**
     * 批量永久删除
     */
    default void batchPurge(List<Long> recycleBinIds) {
        recycleBinIds.forEach(this::purge);
    }
}
