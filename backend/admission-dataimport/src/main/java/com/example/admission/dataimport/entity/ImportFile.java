package com.example.admission.dataimport.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 导入文件实体，对应 import_file 表.
 *
 * <p>记录导入过程中产生的文件，包括原始文件和错误明细文件。</p>
 *
 * @author admission-system
 */
@TableName("import_file")
public class ImportFile {

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 导入批次ID */
    @TableField("batch_id")
    private Long batchId;

    /** 文件类型: ORIGINAL / ERROR_DETAIL */
    @TableField("file_type")
    private String fileType;

    /** 文件存储路径 */
    @TableField("file_url")
    private String fileUrl;

    /** 创建时间 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    // ========== 文件类型常量 ==========

    public static final String FILE_TYPE_ORIGINAL = "ORIGINAL";
    public static final String FILE_TYPE_ERROR_DETAIL = "ERROR_DETAIL";

    // ========== Getters & Setters ==========

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long batchId) {
        this.batchId = batchId;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
