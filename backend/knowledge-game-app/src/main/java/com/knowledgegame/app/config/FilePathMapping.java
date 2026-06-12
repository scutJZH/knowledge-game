package com.knowledgegame.app.config;

import java.util.Map;

/**
 * 文件路径映射
 * <p>
 * 维护 bizType -> basePath 的关联关系。
 * bizType 是前端感知的业务类型，basePath 是传给文件服务的存储路径标识。
 * 后续有新功能需要上传文件时，在此添加映射即可。
 */
public final class FilePathMapping {

    private FilePathMapping() {
    }

    private static final Map<String, String> MAPPING = Map.of(
    );

    /**
     * 将业务类型转换为文件存储路径标识
     *
     * @param bizType 业务类型
     * @return 文件存储路径标识，不存在返回 null
     */
    public static String toBasePath(String bizType) {
        return MAPPING.get(bizType);
    }
}
