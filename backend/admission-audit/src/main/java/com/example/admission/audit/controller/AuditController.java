package com.example.admission.audit.controller;

import com.example.admission.audit.dto.AuditLogResponse;
import com.example.admission.audit.service.AuditService;
import com.example.admission.common.ApiResponse;
import com.example.admission.common.PageRequest;
import com.example.admission.common.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审计日志控制器（管理员接口）.
 *
 * <p>基础路径： {@code /api/admin/v1/audit-logs}</p>
 * <p>需要 ADMIN 角色（Spring Security 配置拦截 /api/admin/v1/**）</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/v1/audit-logs")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    /**
     * 分页查询审计日志.
     *
     * <p>支持按管理员ID、操作动作、操作对象类型筛选。</p>
     *
     * @param page        页码（从1开始）
     * @param size        每页条数
     * @param adminUserId 管理员ID（可选）
     * @param action      操作动作（可选）
     * @param targetType  操作对象类型（可选）
     * @return 分页审计日志列表
     */
    @GetMapping
    public ApiResponse<PageResponse<AuditLogResponse>> listLogs(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "adminUserId", required = false) Long adminUserId,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "targetType", required = false) String targetType) {

        log.info("Audit log query: page={}, size={}, adminUserId={}, action={}, targetType={}",
                page, size, adminUserId, action, targetType);

        PageRequest pageRequest = new PageRequest(page, size);
        PageResponse<AuditLogResponse> result = auditService.listLogs(
                pageRequest, adminUserId, action, targetType);

        return ApiResponse.success(result);
    }
}
