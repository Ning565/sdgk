package com.example.admission.catalog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admission.catalog.dto.AdmissionHistoryResponse;
import com.example.admission.catalog.dto.EnrollmentPlanQuery;
import com.example.admission.catalog.dto.EnrollmentPlanResponse;
import com.example.admission.catalog.dto.LinkResponse;
import com.example.admission.catalog.dto.PlanDetailResponse;
import com.example.admission.catalog.dto.SchoolResponse;
import com.example.admission.catalog.entity.AdmissionHistory;
import com.example.admission.catalog.entity.EnrollmentPlan;
import com.example.admission.catalog.entity.School;
import com.example.admission.catalog.entity.SchoolLink;
import com.example.admission.dataimport.mapper.ActiveDataVersionMapper;
import com.example.admission.catalog.mapper.AdmissionHistoryMapper;
import com.example.admission.catalog.mapper.EnrollmentPlanMapper;
import com.example.admission.catalog.mapper.SchoolLinkMapper;
import com.example.admission.catalog.mapper.SchoolMapper;
import com.example.admission.common.BusinessException;
import com.example.admission.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Year;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 招生计划查询服务实现.
 *
 * <p>实现 {@link EnrollmentPlanService} 接口供 admission-recommendation 等模块调用，
 * 同时提供面向 Web 层的 DTO 查询方法。</p>
 *
 * @author admission-system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnrollmentPlanServiceImpl implements EnrollmentPlanService {

    private final EnrollmentPlanMapper enrollmentPlanMapper;
    private final SchoolMapper schoolMapper;
    private final AdmissionHistoryMapper admissionHistoryMapper;
    private final SchoolLinkMapper schoolLinkMapper;
    private final ActiveDataVersionMapper activeDataVersionMapper;

    // ==================== EnrollmentPlanService 接口实现 ====================

    @Override
    @Transactional(readOnly = true)
    public List<EnrollmentPlan> listActivePlans(Integer year, Long dataVersionId, String educationLevel) {
        LambdaQueryWrapper<EnrollmentPlan> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EnrollmentPlan::getDataVersionId, dataVersionId);
        if (year != null) {
            wrapper.eq(EnrollmentPlan::getYear, year);
        }
        if (StringUtils.hasText(educationLevel)) {
            wrapper.eq(EnrollmentPlan::getEducationLevel, educationLevel);
        }
        return enrollmentPlanMapper.selectList(wrapper);
    }

    @Override
    @Transactional(readOnly = true)
    public EnrollmentPlan getPlanById(Long planId) {
        return enrollmentPlanMapper.selectById(planId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnrollmentPlan> listPlansByIds(List<Long> planIds) {
        if (planIds == null || planIds.isEmpty()) {
            return Collections.emptyList();
        }
        return enrollmentPlanMapper.selectByIds(planIds);
    }

    @Override
    @Transactional(readOnly = true)
    public School getSchoolById(Long schoolId) {
        return schoolMapper.selectById(schoolId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<School> listSchoolsByIds(List<Long> schoolIds) {
        if (schoolIds == null || schoolIds.isEmpty()) {
            return Collections.emptyList();
        }
        return schoolMapper.selectByIds(schoolIds);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdmissionHistory> listHistoryByPlanSeriesIds(List<Long> planSeriesIds, Integer year) {
        if (planSeriesIds == null || planSeriesIds.isEmpty()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<AdmissionHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(AdmissionHistory::getPlanSeriesId, planSeriesIds);
        if (year != null) {
            wrapper.eq(AdmissionHistory::getYear, year);
        }
        wrapper.orderByDesc(AdmissionHistory::getYear);
        return admissionHistoryMapper.selectList(wrapper);
    }

    // ==================== Web 层查询方法 ====================

    /**
     * 查询招生计划详情.
     *
     * <ol>
     *   <li>查询当前生效版本的招生计划</li>
     *   <li>查询近三年历史录取数据 (通过 admission_history JOIN plan_series)</li>
     *   <li>查询院校外链</li>
     *   <li>如果有 userId，查询预测缓存和志愿表</li>
     * </ol>
     *
     * @param planId 招生计划 ID 招生计划ID
     * @param userId 当前用户ID (可选)
     * @return 招生计划详情
     */
    @Transactional(readOnly = true)
    public PlanDetailResponse getPlanDetail(Long planId, Long userId) {
        EnrollmentPlan plan = enrollmentPlanMapper.selectById(planId);
        if (plan == null) {
            throw new BusinessException(ErrorCode.ENROLLMENT_PLAN_NOT_FOUND);
        }

        PlanDetailResponse response = toPlanDetailResponse(plan);

        // 1. 查询院校基本信息
        School school = schoolMapper.selectOne(
                new LambdaQueryWrapper<School>()
                        .eq(School::getSchoolCode, plan.getSchoolCode())
        );
        if (school != null) {
            response.setSchool(toSchoolResponse(school));
        } else {
            response.setSchool(null);
        }

        // 2. 查询近三年历史录取数据
        if (plan.getPlanSeriesId() != null) {
            List<AdmissionHistory> histories = admissionHistoryMapper
                    .selectByPlanSeriesIdWithLimit(plan.getPlanSeriesId(), 3);
            response.setHistory(histories.stream()
                    .map(this::toHistoryResponse)
                    .collect(Collectors.toList()));
        } else {
            response.setHistory(Collections.emptyList());
        }

        // 3. 查询院校外链
        if (school != null) {
            List<SchoolLink> links = schoolLinkMapper.selectList(
                    new LambdaQueryWrapper<SchoolLink>()
                            .eq(SchoolLink::getSchoolId, school.getId())
                            .orderByAsc(SchoolLink::getSortOrder)
            );
            response.setLinks(links.stream()
                    .map(this::toLinkResponse)
                    .collect(Collectors.toList()));
        } else {
            response.setLinks(Collections.emptyList());
        }

        // 4. 预测缓存 — 暂未对接算法，返回 null
        response.setPrediction(null);

        // 5. 已加入的志愿表 — 暂未对接志愿模块，返回空列表
        response.setInVolunteerForms(Collections.emptyList());

        return response;
    }

    /**
     * 获取招生计划的历史录取数据.
     *
     * @param planId 招生计划 ID 招生计划ID
     * @return 历史录取数据列表
     */
    @Transactional(readOnly = true)
    public List<AdmissionHistoryResponse> getPlanHistory(Long planId) {
        EnrollmentPlan plan = enrollmentPlanMapper.selectById(planId);
        if (plan == null) {
            throw new BusinessException(ErrorCode.ENROLLMENT_PLAN_NOT_FOUND);
        }
        if (plan.getPlanSeriesId() == null) {
            return Collections.emptyList();
        }
        List<AdmissionHistory> histories = admissionHistoryMapper
                .selectByPlanSeriesIdWithLimit(plan.getPlanSeriesId(), 3);
        return histories.stream()
                .map(this::toHistoryResponse)
                .collect(Collectors.toList());
    }

    /**
     * 根据查询条件分页查询招生计划列表.
     * 使用当前生效的 data_version.
     */
    @Transactional(readOnly = true)
    public Page<EnrollmentPlanResponse> listPlans(EnrollmentPlanQuery query) {
        int year = query.getYear() != null ? query.getYear() : Year.now().getValue();
        Long activeVersionId = activeDataVersionMapper.getActiveVersionId("PLAN", year);
        if (activeVersionId == null) {
            log.warn("未找到 year={} 的生效 PLAN 数据版本，返回空结果", year);
            return new Page<>(query.getPage(), query.getSize(), 0);
        }

        LambdaQueryWrapper<EnrollmentPlan> wrapper = buildQueryWrapper(query, activeVersionId);
        applySorting(wrapper, query);

        Page<EnrollmentPlan> entityPage = enrollmentPlanMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), wrapper);

        Page<EnrollmentPlanResponse> resultPage = new Page<>(query.getPage(), query.getSize(), entityPage.getTotal());
        resultPage.setRecords(entityPage.getRecords().stream()
                .map(this::toEnrollmentPlanResponse)
                .collect(Collectors.toList()));
        return resultPage;
    }

    /**
     * 查询指定院校的招生计划列表.
     */
    @Transactional(readOnly = true)
    public Page<EnrollmentPlanResponse> getSchoolPlans(Long schoolId, EnrollmentPlanQuery query) {
        School school = schoolMapper.selectById(schoolId);
        if (school == null) {
            return new Page<>(query.getPage(), query.getSize(), 0);
        }

        int year = query.getYear() != null ? query.getYear() : Year.now().getValue();
        Long activeVersionId = activeDataVersionMapper.getActiveVersionId("PLAN", year);
        if (activeVersionId == null) {
            log.warn("未找到 year={} 的生效 PLAN 数据版本，返回空结果", year);
            return new Page<>(query.getPage(), query.getSize(), 0);
        }

        LambdaQueryWrapper<EnrollmentPlan> wrapper = buildQueryWrapper(query, activeVersionId);
        wrapper.eq(EnrollmentPlan::getSchoolCode, school.getSchoolCode());

        applySorting(wrapper, query);

        Page<EnrollmentPlan> entityPage = enrollmentPlanMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), wrapper);

        Page<EnrollmentPlanResponse> resultPage = new Page<>(query.getPage(), query.getSize(), entityPage.getTotal());
        resultPage.setRecords(entityPage.getRecords().stream()
                .map(this::toEnrollmentPlanResponse)
                .collect(Collectors.toList()));
        return resultPage;
    }

    // ==================== 查询条件构建 ====================

    private LambdaQueryWrapper<EnrollmentPlan> buildQueryWrapper(EnrollmentPlanQuery query, Long activeVersionId) {
        LambdaQueryWrapper<EnrollmentPlan> wrapper = new LambdaQueryWrapper<>();

        wrapper.eq(EnrollmentPlan::getDataVersionId, activeVersionId);

        if (query.getYear() != null) {
            wrapper.eq(EnrollmentPlan::getYear, query.getYear());
        }
        if (StringUtils.hasText(query.getEducationLevel())) {
            wrapper.eq(EnrollmentPlan::getEducationLevel, query.getEducationLevel());
        }
        if (StringUtils.hasText(query.getMajorCategory())) {
            wrapper.eq(EnrollmentPlan::getMajorCategory, query.getMajorCategory());
        }
        if (StringUtils.hasText(query.getMajorSubcategory())) {
            wrapper.like(EnrollmentPlan::getStandardMajorCode, query.getMajorSubcategory());
        }
        if (StringUtils.hasText(query.getEnrollmentType())) {
            wrapper.eq(EnrollmentPlan::getEnrollmentType, query.getEnrollmentType());
        }
        if (StringUtils.hasText(query.getCampusCode())) {
            wrapper.eq(EnrollmentPlan::getCampusCode, query.getCampusCode());
        }
        if (query.getTuitionMin() != null) {
            wrapper.ge(EnrollmentPlan::getTuition, query.getTuitionMin());
        }
        if (query.getTuitionMax() != null) {
            wrapper.le(EnrollmentPlan::getTuition, query.getTuitionMax());
        }
        if (query.getPlanCountMin() != null) {
            wrapper.ge(EnrollmentPlan::getPlanCount, query.getPlanCountMin());
        }
        if (query.getPlanCountMax() != null) {
            wrapper.le(EnrollmentPlan::getPlanCount, query.getPlanCountMax());
        }
        if (StringUtils.hasText(query.getPlanStatus())) {
            wrapper.eq(EnrollmentPlan::getPlanStatus, query.getPlanStatus());
        }

        // 关键字搜索: 学校名称 或 专业名称模糊匹配
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword();
            wrapper.and(w -> w
                    .like(EnrollmentPlan::getSchoolName, keyword)
                    .or()
                    .like(EnrollmentPlan::getMajorName, keyword)
            );
        }

        return wrapper;
    }

    private void applySorting(LambdaQueryWrapper<EnrollmentPlan> wrapper, EnrollmentPlanQuery query) {
        String sortBy = query.getSortBy();
        boolean asc = "asc".equalsIgnoreCase(query.getSortDir());

        if (!StringUtils.hasText(sortBy)) {
            wrapper.orderByAsc(EnrollmentPlan::getSchoolCode);
            return;
        }

        switch (sortBy) {
            case "planCount":
                wrapper.orderBy(true, asc, EnrollmentPlan::getPlanCount);
                break;
            case "tuition":
                wrapper.orderBy(true, asc, EnrollmentPlan::getTuition);
                break;
            case "schoolName":
                wrapper.orderBy(true, asc, EnrollmentPlan::getSchoolName);
                break;
            case "majorName":
                wrapper.orderBy(true, asc, EnrollmentPlan::getMajorName);
                break;
            default:
                wrapper.orderByAsc(EnrollmentPlan::getSchoolCode);
        }
    }

    // ==================== 实体 -> DTO 转换 ====================

    private PlanDetailResponse toPlanDetailResponse(EnrollmentPlan plan) {
        PlanDetailResponse response = new PlanDetailResponse();
        copyPlanFields(response, plan);
        return response;
    }

    private EnrollmentPlanResponse toEnrollmentPlanResponse(EnrollmentPlan plan) {
        EnrollmentPlanResponse response = new EnrollmentPlanResponse();
        copyPlanFields(response, plan);
        return response;
    }

    private void copyPlanFields(EnrollmentPlanResponse response, EnrollmentPlan plan) {
        response.setPlanId(plan.getId());
        response.setYear(plan.getYear());
        response.setSchoolId(plan.getSchoolId());
        response.setSchoolCode(plan.getSchoolCode());
        response.setSchoolName(plan.getSchoolName());
        response.setMajorCode(plan.getMajorCode());
        response.setMajorName(plan.getMajorName());
        response.setStandardMajorCode(plan.getStandardMajorCode());
        response.setMajorCategory(plan.getMajorCategory());
        response.setCampusCode(plan.getCampusCode());
        response.setCampusName(plan.getCampusName());
        response.setEnrollmentType(plan.getEnrollmentType());
        response.setEducationLevel(plan.getEducationLevel());
        response.setPlanCount(plan.getPlanCount());
        response.setTuition(plan.getTuition());
        response.setDuration(plan.getDuration());
        response.setSubjectRequirementText(plan.getSubjectRequirementText());
        response.setSubjectRuleJson(plan.getSubjectRuleJson());
        response.setPlanStatus(plan.getPlanStatus());
        response.setDataVersionId(plan.getDataVersionId());
    }

    private SchoolResponse toSchoolResponse(School school) {
        SchoolResponse response = new SchoolResponse();
        response.setId(school.getId());
        response.setCode(school.getSchoolCode());
        response.setName(school.getSchoolName());
        response.setShortName(school.getShortName());
        response.setProvince(school.getProvince());
        response.setCity(school.getCity());
        response.setSchoolType(school.getSchoolType());
        response.setSchoolTag(school.getSchoolTag());
        response.setWebsite(school.getWebsite());
        response.setLogoUrl(school.getLogoUrl());
        return response;
    }

    private AdmissionHistoryResponse toHistoryResponse(AdmissionHistory history) {
        AdmissionHistoryResponse response = new AdmissionHistoryResponse();
        response.setYear(history.getYear());
        response.setPlanCount(history.getPlanCount());
        response.setMinScore(history.getMinScore());
        response.setMinRank(history.getMinRank());
        response.setAvgScore(history.getAvgScore());
        response.setAvgRank(history.getAvgRank());
        response.setMaxScore(history.getMaxScore());
        response.setMaxRank(history.getMaxRank());
        response.setAdmissionBatch(history.getAdmissionBatch());
        return response;
    }

    private LinkResponse toLinkResponse(SchoolLink link) {
        return new LinkResponse(link.getLinkType(), link.getTitle(), link.getUrl());
    }
}
