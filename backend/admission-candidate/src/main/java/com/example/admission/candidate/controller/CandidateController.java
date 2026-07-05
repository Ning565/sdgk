package com.example.admission.candidate.controller;

import com.example.admission.auth.entity.UserAccount;
import com.example.admission.auth.service.AuthService;
import com.example.admission.candidate.dto.CandidateProfileRequest;
import com.example.admission.candidate.dto.CandidateProfileResponse;
import com.example.admission.candidate.entity.CandidateProfile;
import com.example.admission.candidate.service.CandidateService;
import com.example.admission.common.ApiResponse;
import com.example.admission.common.BusinessException;
import com.example.admission.common.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 考生档案 REST 控制器.
 *
 * <p>基础路径： {@code /api/v1/candidate-profiles}</p>
 *
 * <h3>接口列表：</h3>
 * <ul>
 *   <li>GET  /{year} — 查询指定年份的考生档案</li>
 *   <li>PUT  /{year} — 创建或更新指定年份的考生档案</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/candidate-profiles")
@RequiredArgsConstructor
public class CandidateController {

    private final CandidateService candidateService;
    private final AuthService authService;

    /**
     * 查询当前用户指定年份的考生档案.
     *
     * <p>需要登录态。如果用户未登录，返回 code=2000 (UNAUTHORIZED)
     * 并附带 needLogin=true 的标记。</p>
     *
     * @param year 年份 高考年份
     * @return ApiResponse 包含 CandidateProfileResponse
     */
    @GetMapping("/{year}")
    public ApiResponse<CandidateProfileResponse> getProfile(@PathVariable("year") Integer year) {
        // 检查登录状态
        UserAccount user;
        try {
            user = authService.checkLogin();
        } catch (BusinessException e) {
            // 未登录时返回未授权，前端根据 code=2000 跳转登录页
            log.debug("Unauthenticated access to profile query: year={}", year);
            return ApiResponse.error(ErrorCode.UNAUTHORIZED);
        }

        CandidateProfile profile = candidateService.getProfile(year);
        if (profile == null) {
            // 档案不存在返回空数据
            log.info("Profile not found for userId={}, year={}", user.getId(), year);
            return ApiResponse.success(null);
        }

        CandidateProfileResponse response = buildResponse(profile, user);
        return ApiResponse.success(response);
    }

    /**
     * 创建或更新当前用户指定年份的考生档案.
     *
     * <p>同一用户同一年度最多一份档案。如已存在则更新。
     * 更新时如果分数发生变化且 rankSource=AUTO，自动重新匹配位次。</p>
     *
     * @param year 年份    高考年份（路径参数，必须与请求体中的 year 一致）
     * @param req 请求对象uest HTTP 请求 考生档案请求
     * @return ApiResponse 包含更新后的 CandidateProfileResponse
     */
    @PutMapping("/{year}")
    public ApiResponse<CandidateProfileResponse> saveOrUpdate(@PathVariable("year") Integer year,
                                                               @Valid @RequestBody CandidateProfileRequest request) {
        // 路径参数 year 与请求体 year 一致性检查
        if (!year.equals(request.getYear())) {
            log.warn("Year mismatch: pathYear={}, bodyYear={}", year, request.getYear());
            throw new BusinessException(ErrorCode.PARAM_ERROR, "路径参数年份与请求体年份不一致");
        }

        UserAccount user = authService.checkLogin();

        log.info("Saving profile: userId={}, year={}, score={}, rankSource={}",
                user.getId(), year, request.getScore(), request.getRankSource());

        CandidateProfile profile = candidateService.saveOrUpdate(request);

        CandidateProfileResponse response = buildResponse(profile, user);
        return ApiResponse.success(response);
    }

    /**
     * 将实体转换为响应 DTO.
     */
    private CandidateProfileResponse buildResponse(CandidateProfile profile, UserAccount user) {
        return CandidateProfileResponse.builder()
                .id(profile.getId())
                .userId(profile.getUserId())
                .mobileMasked(user.getMobileMasked() != null ? user.getMobileMasked() : user.getMobileHash())
                .year(profile.getYear())
                .score(profile.getScore())
                .rank(profile.getRank())
                .rankSource(profile.getRankSource())
                .subjectComboIndex(profile.getSubjectComboIndex())
                .subjects(profile.getSubjects())
                .educationLevel(profile.getEducationLevel())
                .preferredRegions(profile.getPreferredRegions())
                .preferredMajors(profile.getPreferredMajors())
                .excludedMajors(profile.getExcludedMajors())
                .tuitionMax(profile.getTuitionMax())
                .schoolNature(profile.getSchoolNature())
                .acceptJointProgram(profile.getAcceptJointProgram())
                .cooperationTypes(profile.getCooperationTypes())
                .remark(profile.getRemark())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }
}
