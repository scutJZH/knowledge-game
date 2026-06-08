package com.knowledgegame.core.domain.model.vo;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 领域分页结果（替代 Spring Page，domain 层零框架依赖）
 */
@Getter
@Builder
public class PageResult<T> {

    /**
     * 数据列表
     */
    private List<T> content;

    /**
     * 总记录数
     */
    private long totalElements;

    /**
     * 当前页码（从 0 开始）
     */
    private int pageNumber;

    /**
     * 每页大小
     */
    private int pageSize;

    /**
     * 总页数
     */
    private int totalPages;

    /**
     * 是否有下一页
     */
    public boolean hasNext() {
        return pageNumber + 1 < totalPages;
    }
}
