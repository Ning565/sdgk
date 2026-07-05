package com.example.admission.volunteercheck.controller;

import com.example.admission.common.ApiResponse;
import com.example.admission.volunteercheck.dto.CheckResultResponse;
import com.example.admission.volunteercheck.service.VolunteerCheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 志愿检查接口.
 */
@RestController
@RequestMapping("/api/v1/volunteer-forms/{formId}/check")
@RequiredArgsConstructor
public class VolunteerCheckController {

    private final VolunteerCheckService volunteerCheckService;

    /** 执行志愿检查 */
    @PostMapping
    public ApiResponse<CheckResultResponse> checkForm(@PathVariable("formId") Long formId) {
        return ApiResponse.success(volunteerCheckService.checkForm(formId));
    }

    /** 获取最新检查结果 */
    @GetMapping("/latest")
    public ApiResponse<CheckResultResponse> getLatestCheckResult(@PathVariable("formId") Long formId) {
        return ApiResponse.success(volunteerCheckService.getLatestCheckResult(formId));
    }
}
