package com.example.admission.volunteercheck;

import com.example.admission.candidate.entity.CandidateProfile;
import com.example.admission.catalog.entity.EnrollmentPlan;
import com.example.admission.volunteer.entity.VolunteerItem;
import com.example.admission.volunteercheck.entity.VolunteerCheckIssue;

import java.util.Map;

/**
 * 志愿检查规则接口.
 * 每个规则实现评估志愿表的一项风险维度.
 */
public interface VolunteerCheckRule {

    /** 规则编码 */
    String code();

    /** 问题级别 */
    CheckLevel level();

    /** 问题描述 */
    String message(VolunteerItem item, CandidateProfile profile);

    /** 修复建议 */
    String suggestion();

    /** 当前上下文是否启用该规则 */
    boolean supports(VolunteerCheckContext ctx);

    /**
     * 评估单条志愿项，返回检查结果.
     *
     * @param item    志愿项
     * @param profile 考生档案
     * @param plan    招生计划
     * @param planPredictionLabel 预测标签（冲/稳/保），可为null
     * @return 检查问题，无问题时返回 null
     */
    VolunteerCheckIssue evaluate(VolunteerItem item, CandidateProfile profile,
                                  EnrollmentPlan plan, String planPredictionLabel);

    enum CheckLevel {
        ERROR, WARNING, INFO
    }
}
