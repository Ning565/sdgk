package com.example.admission.dataimport.dto;

import com.example.admission.dataimport.entity.DataVersion;

import java.time.LocalDateTime;

/**
 * 数据版本响应.
 *
 * @author admission-system
 */
public class DataVersionResponse {

    private Long id;
    private String dataType;
    private Integer year;
    private Integer versionNo;
    private String status;
    private Long sourceBatchId;
    private Integer rowCount;
    private String checksum;
    private Long publishedBy;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;

    /**
     * 从实体转换为响应对象.
     *
     * @param version 数据版本实体
     * @return 响应对象
     */
    public static DataVersionResponse from(DataVersion version) {
        DataVersionResponse response = new DataVersionResponse();
        response.id = version.getId();
        response.dataType = version.getDataType();
        response.year = version.getYear();
        response.versionNo = version.getVersionNo();
        response.status = version.getStatus();
        response.sourceBatchId = version.getSourceBatchId();
        response.rowCount = version.getRowCount();
        response.checksum = version.getChecksum();
        response.publishedBy = version.getPublishedBy();
        response.publishedAt = version.getPublishedAt();
        response.createdAt = version.getCreatedAt();
        return response;
    }

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
