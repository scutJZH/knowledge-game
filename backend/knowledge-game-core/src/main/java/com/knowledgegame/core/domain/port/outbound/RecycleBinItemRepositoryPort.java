package com.knowledgegame.core.domain.port.outbound;

import com.knowledgegame.core.domain.model.domainenum.ResourceType;
import com.knowledgegame.core.domain.model.entity.RecycleBinItem;
import com.knowledgegame.core.domain.model.vo.PageResult;
import com.knowledgegame.core.domain.model.vo.SortField;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 回收站条目仓储端口（出端口）
 * <p>
 * 定义回收站总览表的持久化操作。本需求实现列表查询和按 ID 查询，
 * 写入和删除操作留 REQ-102/103/104~108 实现。
 */
public interface RecycleBinItemRepositoryPort {

    /**
     * 分页查询回收站条目
     *
     * @param type      资源类型过滤，null 表示不过滤
     * @param keyword   关键字模糊匹配 originalName，null/空 表示不过滤
     * @param page      页码（1-based）
     * @param size      每页条数
     * @param sortField 排序字段，null 表示使用默认排序（deletedAt DESC）
     * @return 分页结果
     */
    PageResult<RecycleBinItem> findAll(ResourceType type, String keyword, int page, int size, SortField sortField);

    /**
     * 按 ID 查询回收站条目
     *
     * @param id 回收站记录 ID
     * @return 回收站条目，不存在返回 empty
     */
    Optional<RecycleBinItem> findById(Long id);

    /**
     * 按 ID 集合批量查询回收站条目（REQ-103 批量恢复使用）
     *
     * @param ids 回收站记录 ID 集合
     * @return 存在的条目列表（不存在的 ID 静默跳过，由调用方自行判断缺失）
     */
    List<RecycleBinItem> findAllById(Collection<Long> ids);
}
