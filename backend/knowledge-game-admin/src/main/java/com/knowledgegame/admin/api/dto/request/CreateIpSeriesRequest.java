package com.knowledgegame.admin.api.dto.request;

import com.knowledgegame.core.domain.model.domainenum.IpSeriesStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 创建 IP 系列请求 DTO
 */
@Getter
@Setter
public class CreateIpSeriesRequest {

    @NotBlank(message = "编码不能为空")
    @Size(min = 2, max = 30, message = "编码长度 2-30")
    private String code;

    @NotBlank(message = "名称不能为空")
    @Size(min = 2, max = 50, message = "名称长度 2-50")
    private String name;

    @Size(max = 500, message = "描述最长 500")
    private String description;

    @Size(max = 500, message = "封面图 URL 最长 500")
    private String coverImageUrl;

    @NotNull(message = "状态不能为空")
    private IpSeriesStatus status;
}
