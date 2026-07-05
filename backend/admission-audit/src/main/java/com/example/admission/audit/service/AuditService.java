package com.example.admission.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admission.audit.dto.AuditLogResponse;
import com.example.admission.audit.entity.AuditLog;
import com.example.admission.audit.mapper.AuditLogMapper;
import com.example.admission.common.PageRequest;
import com.example.admission.common.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 审计日志服务.
 *
 * <p>记录管理员操作审计日志，支持分页查询。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogMapper auditLogMapper;

    /**
     * 记录一条审计日志.
     *
     * @param adminUserId 操作管理员ID
     * @param action      操作动作（如 CREATE/UPDATE/DELETE/PUBLISH/EXPORT）
     * @param targetType  操作对象类型（如 PLAN/HISTORY/YEAR_CONFIG）
     * @param targetId    操作对象ID
     * @param detail      操作详情（JSON 字符串）
     * @param ipAddress 客户端 IP 地址   操作者IP地址
     */
    @Transactional
    public void log(Long adminUserId, String action, String targetType,
                    Long targetId, String detail, String ipAddress) {
        AuditLog auditLog = new AuditLog();
        auditLog.setAdminUserId(adminUserId);
        auditLog.setAction(action);
        auditLog.setTargetType(targetType);
        auditLog.setTargetId(targetId);
        auditLog.setDetailJson(detail);
        auditLog.setIpAddress(ipAddress);
        auditLog.setCreatedAt(LocalDateTime.now());

        auditLogMapper.insert(auditLog);
        log.info("Audit log recorded: adminUserId={}, action={}, targetType={}, targetId={}",
                adminUserId, action, targetType, targetId);
    }

    /**
     * 分页查询审计日志.
     *
     * @param pageRequest 分页参数
     * @param adminUserId 管理员ID筛选（可选）
     * @param action      操作动作筛选（可选）
     * @param targetType  操作对象类型筛选（可选）
     * @return 分页审计日志列表
     */
    public PageResponse<AuditLogResponse> listLogs(PageRequest pageRequest,
                                                    Long adminUserId,
                                                    String action,
                                                    String targetType) {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();

        if (adminUserId != null) {
            wrapper.eq(AuditLog::getAdminUserId, adminUserId);
        }
        if (action != null && !action.isBlank()) {
            wrapper.eq(AuditLog::getAction, action);
        }
        if (targetType != null && !targetType.isBlank()) {
            wrapper.eq(AuditLog::getTargetType, targetType);
        }

        // 按创建时间降序
        wrapper.orderByDesc(AuditLog::getCreatedAt);

        Page<AuditLog> mpPage = new Page<>(pageRequest.getPage(), pageRequest.getSize());
        Page<AuditLog> resultPage = auditLogMapper.selectPage(mpPage, wrapper);

        List<AuditLogResponse> records = resultPage.getRecords().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return PageResponse.of(records, resultPage.getTotal(),
                pageRequest.getPage(), pageRequest.getSize());
    }

    private AuditLogResponse toResponse(AuditLog log) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .adminUserId(log.getAdminUserId())
                .action(log.getAction())
                .targetType(log.getTargetType())
                .targetId(log.getTargetId())
                .detailJson(log.getDetailJson())
                .ipAddress(log.getIpAddress())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
