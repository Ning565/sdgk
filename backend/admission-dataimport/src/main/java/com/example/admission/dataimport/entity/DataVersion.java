package com.example.admission.dataimport.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 数据版本实体，对应 data_version 表.
 *
 * <p>管理各类型数据的版本，支持多版本共存和状态流转，
 * 是数据发布和回滚的核心实体。</p>
 *
 * @author admission-system
 */
@TableName("data_version")
public class DataVersion {

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 数据类型: SCORE_RANK / PLAN / HISTORY / LINK */
    @TableField("data_type")
    private String dataType;

    /** 数据年份 */
    @TableField("year")
    private Integer year;

    /** 版本号(同类型同年递增) */
    @TableField("version_no")
    private Integer versionNo;

    /** 版本状态: DRAFT / VALIDATING / READY / PUBLISHED / ARCHIVED */
    @TableField("status")
    private String status;

    /** 来源导入批次ID */
    @TableField("source_batch_id")
    private Long sourceBatchId;

    /** 数据行数 */
    @TableField("row_count")
    private Integer rowCount;

    /** 数据校验和(SHA-256) */
    @TableField("checksum")
    private String checksum;

    /** 发布人(admin_user.id) */
    @TableField("published_by")
    private Long publishedBy;

    /** 发布时间 */
    @TableField("published_at")
    private LocalDateTime publishedAt;

    /** 创建时间 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    // ========== 状态常量 ==========

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_VALIDATING = "VALIDATING";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    // ========== Getters & Setters ==========

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getSourceBatchId() {
        return sourceBatchId;
    }

    public void setSourceBatchId(Long sourceBatchId) {
        this.sourceBatchId = sourceBatchId;
    }

    public Integer getRowCount() {
        return rowCount;
    }

    public void setRowCount(Integer rowCount) {
        this.rowCount = rowCount;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public Long getPublishedBy() {
        return publishedBy;
    }

    public void setPublishedBy(Long publishedBy) {
        this.publishedBy = publishedBy;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
