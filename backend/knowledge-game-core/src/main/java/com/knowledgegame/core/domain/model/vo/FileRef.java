package com.knowledgegame.core.domain.model.vo;

import java.util.Objects;

/**
 * 图片引用值对象
 * <p>
 * 表示聚合根对一张图片的引用，包含 fileId（事实源）和 url（冗余查询字段）。
 * <p>
 * 允许的两种状态：
 * - (null, null)：无图片
 * - (Long, String)：完整引用
 * <p>
 * 禁止的半状态（构造时抛 IllegalArgumentException）：
 * - (Long, null)：fileId 存在但 url 缺失
 * - (null, String)：url 存在但 fileId 缺失
 */
public final class FileRef {

    private final Long fileId;
    private final String url;

    private FileRef(Long fileId, String url) {
        this.fileId = fileId;
        this.url = url;
    }

    /**
     * 工厂方法：fileId 与 url 必须同时存在或同时为空。
     * 附加校验：fileId 不能为 0（非法自增ID），url 不能为空串。
     */
    public static FileRef of(Long fileId, String url) {
        if (fileId == null && url == null) {
            return new FileRef(null, null);
        }
        if (fileId == null || url == null) {
            throw new IllegalArgumentException(
                    "FileRef 禁止半状态: fileId=" + fileId + ", url=" + url
            );
        }
        if (fileId == 0) {
            throw new IllegalArgumentException("FileRef fileId 不能为 0");
        }
        if (url.isEmpty()) {
            throw new IllegalArgumentException("FileRef url 不能为空串");
        }
        return new FileRef(fileId, url);
    }

    public Long fileId() {
        return fileId;
    }

    public String url() {
        return url;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FileRef other)) return false;
        return Objects.equals(fileId, other.fileId) && Objects.equals(url, other.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileId, url);
    }

    @Override
    public String toString() {
        return "FileRef{fileId=" + fileId + ", url='" + url + "'}";
    }
}
