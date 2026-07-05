package com.example.admission.volunteercheck;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 志愿检查上下文.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VolunteerCheckContext {

    /** 志愿表ID */
    private Long formId;

    /** 考生档案 */
    private com.example.admission.candidate.entity.CandidateProfile profile;

    /** 志愿项列表 */
    private java.util.List<com.example.admission.volunteer.entity.VolunteerItem> items;

    /** planId -> EnrollmentPlan 映射 */
    private Map<Long, com.example.admission.catalog.entity.EnrollmentPlan> planMap;

    /** planId -> 预测标签(冲/稳/保) 映射 */
    private Map<Long, String> predictionLabelMap;
}
