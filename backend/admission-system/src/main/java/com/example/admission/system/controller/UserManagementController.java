package com.example.admission.system.controller;

import com.example.admission.common.ApiResponse;
import com.example.admission.common.BusinessException;
import com.example.admission.common.ErrorCode;
import com.example.admission.common.PageResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 用户管理 REST 控制器。基础路径：{@code /api/admin/v1}
 *
 * <p>提供管理后台的用户列表查询、角色变更和状态切换功能。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/v1")
@RequiredArgsConstructor
public class UserManagementController {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 分页查询用户列表。
     *
     * @param page 页码（从 1 开始）
     * @param size 每页数量（默认 20，最大 200）
     * @return 分页用户列表
     */
    @GetMapping("/users")
    public ApiResponse<PageResponse<AdminUserResponse>> listUsers(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {

        if (page < 1) page = 1;
        if (size < 1) size = 20;
        if (size > 200) size = 200;

        int offset = (page - 1) * size;

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_account", Long.class);
        long totalCount = total != null ? total : 0L;

        List<AdminUserResponse> records = jdbcTemplate.query(
                "SELECT id, username, nickname, status, created_at FROM user_account ORDER BY id DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> AdminUserResponse.builder()
                        .id(rs.getLong("id"))
                        .username(rs.getString("username"))
                        .nickname(rs.getString("nickname"))
                        .status(rs.getString("status"))
                        .createdAt(rs.getObject("created_at", LocalDateTime.class))
                        .build(),
                size, offset);

        log.debug("Listed users: page={}, size={}, total={}", page, size, totalCount);
        return ApiResponse.success(PageResponse.of(records, totalCount, page, size));
    }

    /**
     * 更新用户角色（MVP：仅返回 OK）。
     *
     * @param id 用户 ID
     * @param body 请求体，包含 role 字段
     * @return 空响应
     */
    @PutMapping("/users/{id}/role")
    public ApiResponse<Void> updateUserRole(@PathVariable("id") Long id,
                                             @org.springframework.web.bind.annotation.RequestBody Map<String, String> body) {
        log.info("Update role for userId={}, role={} (MVP: not implemented)", id, body.get("role"));
        // MVP: 仅返回 OK，不做实际角色变更
        return ApiResponse.success();
    }

    /**
     * 切换用户状态（ACTIVE 和 DISABLED 之间翻转）。
     *
     * @param id 用户 ID
     * @return 切换后的用户状态
     */
    @PutMapping("/users/{id}/toggle-status")
    public ApiResponse<AdminUserResponse> toggleUserStatus(@PathVariable("id") Long id) {
        // 查询当前状态
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, username, nickname, status, created_at FROM user_account WHERE id = ?", id);

        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_EXIST);
        }

        Map<String, Object> row = rows.get(0);
        String currentStatus = (String) row.get("status");
        String newStatus = "ACTIVE".equals(currentStatus) ? "DISABLED" : "ACTIVE";

        jdbcTemplate.update("UPDATE user_account SET status = ?, updated_at = NOW() WHERE id = ?",
                newStatus, id);

        log.info("Toggled user status: userId={}, {} -> {}", id, currentStatus, newStatus);

        AdminUserResponse response = AdminUserResponse.builder()
                .id((Long) row.get("id"))
                .username((String) row.get("username"))
                .nickname((String) row.get("nickname"))
                .status(newStatus)
                .createdAt((LocalDateTime) row.get("created_at"))
                .build();

        return ApiResponse.success(response);
    }

    // --- DTO ---

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminUserResponse {
        private Long id;
        private String username;
        private String nickname;
        private String status;
        private LocalDateTime createdAt;
    }
}
