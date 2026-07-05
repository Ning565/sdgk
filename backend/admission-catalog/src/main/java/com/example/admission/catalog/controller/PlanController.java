package com.example.admission.catalog.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admission.catalog.dto.AdmissionHistoryResponse;
import com.example.admission.catalog.dto.EnrollmentPlanQuery;
import com.example.admission.catalog.dto.EnrollmentPlanResponse;
import com.example.admission.catalog.dto.PlanDetailResponse;
import com.example.admission.catalog.service.EnrollmentPlanServiceImpl;
import com.example.admission.common.ApiResponse;
import com.example.admission.common.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 招生计划查询控制器.
 *
 * @author admission-system
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/plans")
@RequiredArgsConstructor
public class PlanController {

    private final EnrollmentPlanServiceImpl enrollmentPlanService;

    /**
     * 招生计划列表查询.
     */
    @GetMapping
    public ApiResponse<PageResponse<EnrollmentPlanResponse>> listPlans(EnrollmentPlanQuery query) {
        Page<EnrollmentPlanResponse> result = enrollmentPlanService.listPlans(query);
        PageResponse<EnrollmentPlanResponse> pageResponse = PageResponse.of(
                result.getRecords(), result.getTotal(), query.getPage(), query.getSize());
        return ApiResponse.success(pageResponse);
    }

    /**
     * 招生计划详情.
     *
     * @param planId 招生计划 ID 招生计划ID
     * @param userId 当前用户ID (可选)
     */
    @GetMapping("/{planId}")
    public ApiResponse<PlanDetailResponse> getPlanDetail(
            @PathVariable("planId") Long planId,
            @RequestParam(value = "userId", required = false) Long userId) {
        PlanDetailResponse detail = enrollmentPlanService.getPlanDetail(planId, userId);
        return ApiResponse.success(detail);
    }

    /**
     * 招生计划的历史录取数据.
     */
    @GetMapping("/{planId}/history")
    public ApiResponse<List<AdmissionHistoryResponse>> getPlanHistory(@PathVariable("planId") Long planId) {
        List<AdmissionHistoryResponse> history = enrollmentPlanService.getPlanHistory(planId);
        return ApiResponse.success(history);
    }
}
