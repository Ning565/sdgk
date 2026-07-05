package com.example.admission.export.dto;

import lombok.Data;

/**
 * 导出请求.
 */
@Data
public class ExportRequest {

    /** 是否确认导出（有错误时需设为true） */
    private boolean confirmWithErrors;

    /** 幂等操作ID */
    private String clientOperationId;
}
