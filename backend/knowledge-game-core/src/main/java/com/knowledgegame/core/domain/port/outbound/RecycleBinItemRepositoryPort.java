package com.knowledgegame.core.domain.port.outbound;

import com.knowledgegame.core.domain.model.domainenum.ResourceType;
import com.knowledgegame.core.domain.model.entity.RecycleBinItem;
import com.knowledgegame.core.domain.model.vo.PageResult;
import com.knowledgegame.core.domain.model.vo.SortField;

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

    // ===== 以下方法留 REQ-102/103/104~108 实现 =====

    /**
     * 保存回收站条目（REQ-104~108 DELETE 端点对接时调用）
     */
    default void save(RecycleBinItem item) {
        throw new UnsupportedOperationException("save 方法将在 REQ-104~108 实现");
    }

    /**
     * 按 ID 删除回收站条目（REQ-102/103 用）
     */
    default void deleteById(Long id) {
        throw new UnsupportedOperationException("deleteById 方法将在 REQ-102/103 实现");
    }
}
