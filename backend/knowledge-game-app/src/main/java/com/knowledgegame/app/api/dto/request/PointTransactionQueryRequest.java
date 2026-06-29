package com.knowledgegame.app.api.dto.request;

import com.knowledgegame.app.application.command.PointTransactionQuery;
import com.knowledgegame.core.common.exception.BusinessException;
import com.knowledgegame.core.common.result.ResultCode;
import com.knowledgegame.core.domain.model.domainenum.ReferenceType;
import com.knowledgegame.core.domain.model.domainenum.TxType;
import com.knowledgegame.core.domain.model.vo.SortField;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 积分流水查询请求参数（GET @ModelAttribute 绑定）
 */
public class PointTransactionQueryRequest {

    private int page = 1;
    private int size = 10;
    private String type;
    private String referenceType;
    private Long userId;
    private Long groupId;
    private Long startDate;
    private Long endDate;
    private String sort;
    private String order;

    public PointTransactionQuery toQuery() {
        if (page < 1) {
            throw new BusinessException(400, "page 必须 >= 1");
        }
        if (size < 1 || size > 100) {
            throw new BusinessException(400, "size 必须在 1-100 之间");
        }

        TxType txType = null;
        if (type != null && !type.isBlank()) {
            try {
                txType = TxType.valueOf(type.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException(
                        ResultCode.POINT_TRANSACTION_REFERENCE_TYPE_INVALID);
            }
        }

        ReferenceType refType = null;
        if (referenceType != null && !referenceType.isBlank()) {
            try {
                refType = ReferenceType.valueOf(referenceType.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException(
                        ResultCode.POINT_TRANSACTION_REFERENCE_TYPE_INVALID);
            }
        }

        LocalDateTime start = null;
        if (startDate != null) {
            start = Instant.ofEpochMilli(startDate).atZone(ZoneId.systemDefault()).toLocalDateTime();
        }
        LocalDateTime end = null;
        if (endDate != null) {
            end = Instant.ofEpochMilli(endDate).atZone(ZoneId.systemDefault()).toLocalDateTime();
        }

        SortField sortField = SortField.parse(sort, order);

        return new PointTransactionQuery(userId, groupId, txType, refType,
                start, end, sortField, page, size);
    }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getReferenceType() { return referenceType; }
    public void setReferenceType(String referenceType) { this.referenceType = referenceType; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }

    public Long getStartDate() { return startDate; }
    public void setStartDate(Long startDate) { this.startDate = startDate; }

    public Long getEndDate() { return endDate; }
    public void setEndDate(Long endDate) { this.endDate = endDate; }

    public String getSort() { return sort; }
    public void setSort(String sort) { this.sort = sort; }

    public String getOrder() { return order; }
    public void setOrder(String order) { this.order = order; }
}
