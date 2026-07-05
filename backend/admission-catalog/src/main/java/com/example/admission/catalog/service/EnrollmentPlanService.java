package com.example.admission.catalog.service;

import com.example.admission.catalog.entity.AdmissionHistory;
import com.example.admission.catalog.entity.EnrollmentPlan;
import com.example.admission.catalog.entity.School;

import java.util.List;

/**
 * 招生计划查询服务接口.
 *
 * <p>由 admission-catalog 模块实现，admission-recommendation 模块通过此接口
 * 查询招生计划、院校、历史录取等基础信息，不直接访问 Mapper。</p>
 */
public interface EnrollmentPlanService {

    /**
     * 查询指定年度当前生效版本的招生计划列表（含基础过滤）.
     *
     * @param year 年份           招生年度
     * @param dataVersionId  生效数据版本ID
     * @param educationLevel 教育层次（可选）
     * @return 符合条件的招生计划实体列表
     */
    List<EnrollmentPlan> listActivePlans(Integer year, Long dataVersionId, String educationLevel);

    /**
     * 根据ID查询招生计划.
     *
     * @param planId 招生计划 ID 计划ID
     * @return 招生计划实体
     */
    EnrollmentPlan getPlanById(Long planId);

    /**
     * 批量查询招生计划.
     *
     * @param planId 招生计划 IDs 计划ID列表
     * @return 招生计划实体列表
     */
    List<EnrollmentPlan> listPlansByIds(List<Long> planIds);

    /**
     * 根据院校ID查询院校信息.
     *
     * @param schoolId 院校ID
     * @return 院校实体
     */
    School getSchoolById(Long schoolId);

    /**
     * 批量查询院校信息.
     *
     * @param schoolIds 院校ID列表
     * @return 院校实体列表
     */
    List<School> listSchoolsByIds(List<Long> schoolIds);

    /**
     * 根据计划系列ID查询上一年历史录取数据.
     *
     * @param planSeriesIds 计划系列ID列表
     * @param year 年份          历史数据年度
     * @return 历史录取数据列表
     */
    List<AdmissionHistory> listHistoryByPlanSeriesIds(List<Long> planSeriesIds, Integer year);
}
