package com.knowledgegame.admin.api.dto.request;

import com.knowledgegame.core.domain.model.domainenum.IpSeriesStatus;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 更新 IP 系列请求 DTO（所有字段可选，null 表示不修改）
 */
@Getter
@Setter
public class UpdateIpSeriesRequest {

    @Size(min = 2, max = 30, message = "编码长度 2-30")
    private String code;

    @Size(min = 2, max = 50, message = "名称长度 2-50")
    private String name;

    @Size(max = 500, message = "描述最长 500")
    private String description;

    private Long coverImageFileId;

    private IpSeriesStatus status;
}
