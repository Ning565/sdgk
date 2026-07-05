package com.example.admission.dataimport.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 创建导入批次请求.
 *
 * @author admission-system
 */
public class ImportBatchCreateRequest {

    /** 数据类型: SCORE_RANK / PLAN / HISTORY / LINK */
    @NotBlank(message = "数据类型不能为空")
    private String dataType;

    /** 数据年份 */
    @NotNull(message = "数据年份不能为空")
    private Integer year;

    /** 文件名 */
    @NotBlank(message = "文件名不能为空")
    private String fileName;

    /** 文件大小(字节) */
    private Long fileSize;

    // ========== Getters & Setters ==========

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
}
