package com.example.admission.system.controller;

import com.example.admission.common.ApiResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台仪表盘 REST 控制器。基础路径：{@code /api/admin/v1}
 *
 * <p>提供系统概览统计数据，包括用户数、志愿表数、院校数、导入批次和版本数。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/v1")
@RequiredArgsConstructor
public class DashboardController {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 获取仪表盘统计数据。
     *
     * @return 统计数据
     */
    @GetMapping("/dashboard/stats")
    public ApiResponse<DashboardStatsResponse> getStats() {
        log.debug("Fetching dashboard stats");

        long userCount = countTable("user_account");
        long formCount = countTable("volunteer_form");
        long schoolCount = countTable("school");
        long importCount = countTable("import_batch");
        long versionCount = countTable("data_version");

        DashboardStatsResponse stats = DashboardStatsResponse.builder()
                .userCount(userCount)
                .formCount(formCount)
                .schoolCount(schoolCount)
                .importCount(importCount)
                .versionCount(versionCount)
                .build();

        log.debug("Dashboard stats: {}", stats);
        return ApiResponse.success(stats);
    }

    private long countTable(String tableName) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName, Long.class);
        return count != null ? count : 0L;
    }

    // --- DTO ---

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DashboardStatsResponse {
        private long userCount;
        private long formCount;
        private long schoolCount;
        private long importCount;
        private long versionCount;
    }
}
