package com.example.admission.dataimport.dto;

import com.example.admission.dataimport.entity.ImportBatch;

import java.time.LocalDateTime;

/**
 * 导入批次响应.
 *
 * @author admission-system
 */
public class ImportBatchResponse {

    private Long batchId;
    private String dataType;
    private Integer year;
    private String fileName;
    private Long fileSize;
    private String status;
    private Integer totalRows;
    private Integer validRows;
    private Integer errorRows;
    private Long createdBy;
    private LocalDateTime createdAt;

    /**
     * 从实体转换为响应对象.
     *
     * @param batch 导入批次实体
     * @return 响应对象
     */
    public static ImportBatchResponse from(ImportBatch batch) {
        ImportBatchResponse response = new ImportBatchResponse();
        response.batchId = batch.getId();
        response.dataType = batch.getDataType();
        response.year = batch.getYear();
        response.fileName = batch.getFileName();
        response.fileSize = batch.getFileSize();
        response.status = batch.getStatus();
        response.totalRows = batch.getTotalRows();
        response.validRows = batch.getValidRows();
        response.errorRows = batch.getErrorRows();
        response.createdBy = batch.getCreatedBy();
        response.createdAt = batch.getCreatedAt();
        return response;
    }

    // ========== Getters & Setters ==========

    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long batchId) {
        this.batchId = batchId;
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
}
