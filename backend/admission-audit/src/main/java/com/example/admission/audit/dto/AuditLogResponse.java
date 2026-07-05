package com.example.admission.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 审计日志响应 DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogResponse {

    private Long id;

    private Long adminUserId;

    private String action;

    private String targetType;

    private Long targetId;

    private String detailJson;

    private String ipAddress;

    private LocalDateTime createdAt;
}
