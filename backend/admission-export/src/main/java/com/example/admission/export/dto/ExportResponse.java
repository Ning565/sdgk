package com.example.admission.export.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 导出响应.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportResponse {

    /** 下载文件URL */
    private String fileUrl;

    /** 文件名 */
    private String fileName;

    /** 可交互 HTML 下载地址 */
    private String htmlFileUrl;

    /** HTML 文件名 */
    private String htmlFileName;

    /** 导出记录ID */
    private Long exportRecordId;

    /** 导出时间 */
    private LocalDateTime exportedAt;
}
