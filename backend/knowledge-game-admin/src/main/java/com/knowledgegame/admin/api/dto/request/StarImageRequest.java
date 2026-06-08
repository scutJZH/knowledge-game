package com.knowledgegame.admin.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 星级图片请求 DTO（嵌套在创建/添加请求中）
 */
@Getter
@Setter
public class StarImageRequest {

    @NotNull(message = "星级不能为空")
    @Min(value = 1, message = "星级最小为 1")
    @Max(value = 5, message = "星级最大为 5")
    private Integer starLevel;

    @NotBlank(message = "图片 URL 不能为空")
    @Size(max = 500, message = "图片 URL 最长 500")
    private String imageUrl;
}
