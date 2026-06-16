package com.knowledgegame.core.domain.model.entity;

import com.knowledgegame.core.domain.model.domainenum.IpSeriesStatus;
import com.knowledgegame.core.domain.model.vo.FileRef;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * IP 系列领域实体（无框架注解）
 */
@Getter
public class IpSeries {

    private Long id;
    private String code;
    private String name;
    private String description;
    private FileRef coverImage;
    private IpSeriesStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 创建新 IP 系列（工厂方法）
     */
    public static IpSeries create(String code, String name, String description,
                                  FileRef coverImage, IpSeriesStatus status) {
        IpSeries series = new IpSeries();
        series.code = code;
        series.name = name;
        series.description = description;
        series.coverImage = coverImage;
        series.status = status;
        series.createdAt = LocalDateTime.now();
        series.updatedAt = LocalDateTime.now();
        return series;
    }

    /**
     * 从持久化重建（用于 Repository 加载）
     */
    public static IpSeries reconstruct(Long id, String code, String name, String description,
                                       FileRef coverImage, IpSeriesStatus status,
                                       LocalDateTime createdAt, LocalDateTime updatedAt) {
        IpSeries series = new IpSeries();
        series.id = id;
        series.code = code;
        series.name = name;
        series.description = description;
        series.coverImage = coverImage;
        series.status = status;
        series.createdAt = createdAt;
        series.updatedAt = updatedAt;
        return series;
    }

    /**
     * 更新 IP 系列信息（FileRef 整体替换，传 null 表示清空封面图）
     */
    public void update(String code, String name, String description,
                       FileRef coverImage, IpSeriesStatus status) {
        if (code != null) {
            this.code = code;
        }
        if (name != null) {
            this.name = name;
        }
        this.description = description;
        this.coverImage = coverImage;
        if (status != null) {
            this.status = status;
        }
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 软删除
     */
    public void deactivate() {
        this.status = IpSeriesStatus.INACTIVE;
        this.updatedAt = LocalDateTime.now();
    }
}
