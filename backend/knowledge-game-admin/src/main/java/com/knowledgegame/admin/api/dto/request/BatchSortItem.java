package com.knowledgegame.admin.api.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 批量排序项
 */
@Getter
@Setter
public class BatchSortItem {

    @NotNull(message = "id 不能为空")
    private Long id;

    @NotNull(message = "sortOrder 不能为空")
    private Integer sortOrder;
}
