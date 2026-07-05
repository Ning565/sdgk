package com.example.admission.dataimport.dto;

import com.example.admission.dataimport.entity.ImportRowError;

/**
 * 行级错误响应.
 *
 * @author admission-system
 */
public class ImportRowErrorResponse {

    private Long id;
    private Integer rowNumber;
    private String fieldName;
    private String originalValue;
    private String errorType;
    private String errorMessage;
    private String suggestion;

    /**
     * 从实体转换为响应对象.
     *
     * @param error 行级错误实体
     * @return 响应对象
     */
    public static ImportRowErrorResponse from(ImportRowError error) {
        ImportRowErrorResponse response = new ImportRowErrorResponse();
        response.id = error.getId();
        response.rowNumber = error.getRowNumber();
        response.fieldName = error.getFieldName();
        response.originalValue = error.getOriginalValue();
        response.errorType = error.getErrorType();
        response.errorMessage = error.getErrorMessage();
        response.suggestion = error.getSuggestion();
        return response;
    }

    // ========== Getters & Setters ==========

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
