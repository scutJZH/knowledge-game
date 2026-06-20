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
     * <p>
     * <b>实现者契约（REQ-104~108 各资源遵守）：</b>
     * <ul>
     *   <li>恢复后资源状态<b>强制设为 INACTIVE</b>，不保留删除前的状态（由用户手动启用）</li>
     *   <li>原子性由调用方 {@code RecycleBinAppService.restoreInNewTransaction} 保证（{@code @Transactional(REQUIRES_NEW)}），
     *       本方法自身不标注 {@code @Transactional}</li>
     *   <li>业务校验失败（如关联已不存在）抛 {@link com.knowledgegame.core.common.exception.BusinessException}，
     *       异常消息需对管理员友好</li>
     * </ul>
     *
     * @param recycleBinId 回收站总览表 ID
     */
    void restore(Long recycleBinId);

    /**
     * 物理永久删除（REQ-102 框架 + REQ-104~108 各资源实现）
     * <p>
     * <b>实现者契约（REQ-104~108 各资源遵守）：</b>
     * <ul>
     *   <li>三步物理删除：1) 删除 _deleted 详情表记录；2) 通过 ResourceType.toBizTypes() 获取 bizType
     *       数组，调 file 服务删除关联文件；3) 删除 recycle_bin 总览表记录</li>
     *   <li>原子性由调用方 {@code RecycleBinAppService.purgeInNewTransaction} 保证（{@code @Transactional(REQUIRES_NEW)}），
     *       策略实现自身不标注 {@code @Transactional}</li>
     *   <li>永久删除失败（如 file 服务不可达）必须抛 {@link com.knowledgegame.core.common.exception.BusinessException}，
     *       由 AppService 捕获归入批量响应的 failures</li>
     *   <li>应容忍 recycle_bin 记录已被并发删除：DELETE FROM recycle_bin 返回 affectedRows=0 时静默成功</li>
     *   <li>策略实现内部不应再次校验 restoreDeadline，时间窗口语义由调用方决定</li>
     * </ul>
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
