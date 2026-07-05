package com.example.admission.catalog.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admission.catalog.dto.EnrollmentPlanQuery;
import com.example.admission.catalog.dto.EnrollmentPlanResponse;
import com.example.admission.catalog.dto.SchoolDetailResponse;
import com.example.admission.catalog.dto.SchoolResponse;
import com.example.admission.catalog.service.EnrollmentPlanServiceImpl;
import com.example.admission.catalog.service.SchoolService;
import com.example.admission.common.ApiResponse;
import com.example.admission.common.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 院校查询控制器.
 *
 * @author admission-system
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/schools")
@RequiredArgsConstructor
public class SchoolController {

    private final SchoolService schoolService;
    private final EnrollmentPlanServiceImpl enrollmentPlanService;

    /**
     * 院校列表查询.
     */
    @GetMapping
    public ApiResponse<PageResponse<SchoolResponse>> listSchools(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "province", required = false) String province,
            @RequestParam(value = "schoolType", required = false) String schoolType,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {

        Page<SchoolResponse> result = schoolService.listSchools(keyword, province, schoolType, page, size);
        PageResponse<SchoolResponse> pageResponse = PageResponse.of(
                result.getRecords(), result.getTotal(), page, size);
        return ApiResponse.success(pageResponse);
    }

    /**
     * 院校详情.
     */
    @GetMapping("/{schoolId}")
    public ApiResponse<SchoolDetailResponse> getSchool(@PathVariable("schoolId") Long schoolId) {
        SchoolDetailResponse detail = schoolService.getSchool(schoolId);
        return ApiResponse.success(detail);
    }

    /**
     * 院校下的招生专业列表.
     */
    @GetMapping("/{schoolId}/plans")
    public ApiResponse<PageResponse<EnrollmentPlanResponse>> getSchoolPlans(
            @PathVariable("schoolId") Long schoolId,
            EnrollmentPlanQuery query) {

        Page<EnrollmentPlanResponse> result = enrollmentPlanService.getSchoolPlans(schoolId, query);
        PageResponse<EnrollmentPlanResponse> pageResponse = PageResponse.of(
                result.getRecords(), result.getTotal(), query.getPage(), query.getSize());
        return ApiResponse.success(pageResponse);
    }
}
