package com.example.admission.dataimport.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 导入批次实体，对应 import_batch 表.
 *
 * <p>记录每次数据导入的批次信息，包括数据类型、年份、文件名、
 * 处理状态和行数统计等。</p>
 *
 * @author admission-system
 */
@TableName("import_batch")
public class ImportBatch {

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 数据类型: SCORE_RANK / PLAN / HISTORY / LINK */
    @TableField("data_type")
    private String dataType;

    /** 数据年份 */
    @TableField("year")
    private Integer year;

    /** 原始文件名 */
    @TableField("file_name")
    private String fileName;

    /** 文件大小(字节) */
    @TableField("file_size")
    private Long fileSize;

    /** 文件存储路径 */
    @TableField("file_url")
    private String fileUrl;

    /** 导入状态: UPLOADING / PARSING / VALIDATION_FAILED / READY / PUBLISHED / CANCELLED */
    @TableField("status")
    private String status;

    /** 总行数 */
    @TableField("total_rows")
    private Integer totalRows;

    /** 有效行数 */
    @TableField("valid_rows")
    private Integer validRows;

    /** 错误行数 */
    @TableField("error_rows")
    private Integer errorRows;

    /** 导入人(admin_user.id) */
    @TableField("created_by")
    private Long createdBy;

    /** 创建时间 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    // ========== 状态常量 ==========

    /** 上传中 */
    public static final String STATUS_UPLOADING = "UPLOADING";
    /** 解析中 */
    public static final String STATUS_PARSING = "PARSING";
    /** 校验失败 */
    public static final String STATUS_VALIDATION_FAILED = "VALIDATION_FAILED";
    /** 就绪(等待发布) */
    public static final String STATUS_READY = "READY";
    /** 已发布 */
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    /** 已取消 */
    public static final String STATUS_CANCELLED = "CANCELLED";

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

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(Integer totalRows) {
        this.totalRows = totalRows;
    }

    public Integer getValidRows() {
        return validRows;
    }

    public void setValidRows(Integer validRows) {
        this.validRows = validRows;
    }

    public Integer getErrorRows() {
        return errorRows;
    }

    public void setErrorRows(Integer errorRows) {
        this.errorRows = errorRows;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
