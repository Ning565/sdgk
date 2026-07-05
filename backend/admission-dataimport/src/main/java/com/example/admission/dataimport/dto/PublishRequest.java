package com.example.admission.dataimport.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 发布请求.
 *
 * @author admission-system
 */
public class PublishRequest {

    /** 导入批次ID */
    @NotNull(message = "批次ID不能为空")
    private Long batchId;

    /** 确认发布（二次确认机制） */
    private boolean confirmed;

    // ========== Getters & Setters ==========

    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long batchId) {
        this.batchId = batchId;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }
}
