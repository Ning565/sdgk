package com.example.admission.dataimport.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 导入行级错误实体，对应 import_row_error 表.
 *
 * <p>记录导入过程中每行数据的校验错误详情。</p>
 *
 * @author admission-system
 */
@TableName("import_row_error")
public class ImportRowError {

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 导入批次ID */
    @TableField("batch_id")
    private Long batchId;

    /** 错误行号 */
    @TableField("row_number")
    private Integer rowNumber;

    /** 错误字段名 */
    @TableField("field_name")
    private String fieldName;

    /** 原始值 */
    @TableField("original_value")
    private String originalValue;

    /** 错误类型: FORMAT / REQUIRED / DUPLICATE / REFERENCE / RANGE / BUSINESS */
    @TableField("error_type")
    private String errorType;

    /** 错误描述 */
    @TableField("error_message")
    private String errorMessage;

    /** 修改建议 */
    @TableField("suggestion")
    private String suggestion;

    // ========== 错误类型常量 ==========

    public static final String ERROR_FORMAT = "FORMAT";
    public static final String ERROR_REQUIRED = "REQUIRED";
    public static final String ERROR_DUPLICATE = "DUPLICATE";
    public static final String ERROR_REFERENCE = "REFERENCE";
    public static final String ERROR_RANGE = "RANGE";
    public static final String ERROR_BUSINESS = "BUSINESS";

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

    public Integer getRowNumber() {
        return rowNumber;
    }

    public void setRowNumber(Integer rowNumber) {
        this.rowNumber = rowNumber;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getOriginalValue() {
        return originalValue;
    }

    public void setOriginalValue(String originalValue) {
        this.originalValue = originalValue;
    }

    public String getErrorType() {
        return errorType;
    }

    public void setErrorType(String errorType) {
        this.errorType = errorType;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }
}
