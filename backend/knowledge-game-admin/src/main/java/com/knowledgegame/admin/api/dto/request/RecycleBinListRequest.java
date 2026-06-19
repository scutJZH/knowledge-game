package com.knowledgegame.admin.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 回收站列表查询请求参数
 * <p>
 * 所有参数可选，全空时返回第一页默认排序（deletedAt DESC）的全部记录。
 * resourceType 支持 null/空/"ALL"（大小写不敏感）= 不过滤，
 * 非法枚举值由 AppService 校验并返回 PARAM_ERROR。
 */
public class RecycleBinListRequest {

    @Min(0)
    private Integer page = 0;

    @Min(1)
    @Max(100)
    private Integer size = 20;

    private String resourceType;

    @Size(max = 100)
    private String keyword;

    private String sort;

    private String order;

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getSort() {
        return sort;
    }

    public void setSort(String sort) {
        this.sort = sort;
    }

    public String getOrder() {
        return order;
    }

    public void setOrder(String order) {
        this.order = order;
    }
}
