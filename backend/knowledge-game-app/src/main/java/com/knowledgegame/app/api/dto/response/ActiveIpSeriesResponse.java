package com.knowledgegame.app.api.dto.response;

import lombok.Data;

/**
 * ACTIVE IP 系列响应 DTO（用户端 IP 库 Tab 数据源）
 */
@Data
public class ActiveIpSeriesResponse {

    private Long id;

    private String name;

    private String code;

    private Long coverImageFileId;

    private String coverImageUrl;
}
