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
     * 更新必填字段（不支持清空）。可清空字段（description/coverImage）请用对应的 updateXxx / clearXxx 方法
     */
    public void update(String code, String name, IpSeriesStatus status) {
        if (code != null) {
            this.code = code;
        }
        if (name != null) {
            this.name = name;
        }
        if (status != null) {
            this.status = status;
        }
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 更新描述（清空请用 clearDescription）
     *
     * @throws IllegalArgumentException description 为 null 时抛出
     */
    public void updateDescription(String description) {
        if (description == null) {
            throw new IllegalArgumentException("description 清空请用 clearDescription()");
        }
        this.description = description;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 清空描述
     */
    public void clearDescription() {
        this.description = null;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 更新封面图（清空请用 clearCoverImage）
     *
     * @throws IllegalArgumentException coverImage 为 null 时抛出
     */
    public void updateCoverImage(FileRef coverImage) {
        if (coverImage == null) {
            throw new IllegalArgumentException("coverImage 清空请用 clearCoverImage()");
        }
        this.coverImage = coverImage;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 清空封面图
     */
    public void clearCoverImage() {
        this.coverImage = null;
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
