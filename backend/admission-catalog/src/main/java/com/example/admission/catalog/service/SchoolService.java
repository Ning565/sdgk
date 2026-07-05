package com.example.admission.catalog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.admission.catalog.dto.SchoolDetailResponse;
import com.example.admission.catalog.dto.SchoolResponse;
import com.example.admission.catalog.entity.EnrollmentPlan;
import com.example.admission.catalog.entity.School;
import com.example.admission.dataimport.mapper.ActiveDataVersionMapper;
import com.example.admission.catalog.mapper.EnrollmentPlanMapper;
import com.example.admission.catalog.mapper.SchoolMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Year;

/**
 * 院校查询服务.
 *
 * @author admission-system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchoolService {

    private final SchoolMapper schoolMapper;
    private final EnrollmentPlanMapper enrollmentPlanMapper;
    private final ActiveDataVersionMapper activeDataVersionMapper;

    /**
     * 查询院校详情，包含当年在山东的招生专业数.
     */
    @Transactional(readOnly = true)
    public SchoolDetailResponse getSchool(Long schoolId) {
        School school = schoolMapper.selectById(schoolId);
        if (school == null) {
            return null;
        }

        SchoolDetailResponse response = toDetailResponse(school);

        // 计算当年在山东招生的专业数：从当前生效版本查询
        int currentYear = Year.now().getValue();
        Long activeVersionId = activeDataVersionMapper.getActiveVersionId("PLAN", currentYear);
        if (activeVersionId != null) {
            Long planCount = enrollmentPlanMapper.selectCount(
                    new LambdaQueryWrapper<EnrollmentPlan>()
                            .eq(EnrollmentPlan::getSchoolCode, school.getSchoolCode())
                            .eq(EnrollmentPlan::getDataVersionId, activeVersionId)
                            .eq(EnrollmentPlan::getYear, currentYear)
            );
            response.setPlanCount(planCount != null ? planCount.intValue() : 0);
        } else {
            response.setPlanCount(0);
        }

        // eligiblePlanCount 需要考生上下文，无上下文时返回 0
        response.setEligiblePlanCount(0);

        return response;
    }

    /**
     * 分页查询院校列表.
     *
     * @param keyword   院校名称模糊搜索
     * @param province  所在省份
     * @param schoolType 院校类型
     * @param page      当前页码
     * @param size      每页大小
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public Page<SchoolResponse> listSchools(String keyword, String province, String schoolType, int page, int size) {
        LambdaQueryWrapper<School> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w
                    .like(School::getSchoolName, keyword)
                    .or()
                    .like(School::getSchoolCode, keyword)
                    .or()
                    .like(School::getShortName, keyword)
            );
        }
        if (StringUtils.hasText(province)) {
            wrapper.eq(School::getProvince, province);
        }
        if (StringUtils.hasText(schoolType)) {
            wrapper.eq(School::getSchoolType, schoolType);
        }

        wrapper.orderByAsc(School::getSchoolCode);

        Page<School> entityPage = schoolMapper.selectPage(new Page<>(page, size), wrapper);

        Page<SchoolResponse> resultPage = new Page<>(page, size, entityPage.getTotal());
        resultPage.setRecords(entityPage.getRecords().stream()
                .map(this::toResponse)
                .toList());
        return resultPage;
    }

    // ==================== 实体 -> DTO 转换 ====================

    private SchoolResponse toResponse(School school) {
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

    private SchoolDetailResponse toDetailResponse(School school) {
        SchoolDetailResponse response = new SchoolDetailResponse();
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
}
