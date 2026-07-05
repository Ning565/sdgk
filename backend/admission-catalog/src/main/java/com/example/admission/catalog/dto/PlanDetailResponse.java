package com.example.admission.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 招生计划详情响应，继承 EnrollmentPlanResponse，增加院校、历史、预测、外链等信息.
 *
 * @author admission-system
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PlanDetailResponse extends EnrollmentPlanResponse {

    /** 院校基本信息 */
    private SchoolResponse school;

    /** 近三年历史录取数据 (按年份升序排列) */
    private List<AdmissionHistoryResponse> history;

    /** 预测结果 (如果算法有缓存)，无数据时为 null */
    private Map<String, Object> prediction;

    /** 外链列表 (缺失时为空列表) */
    private List<LinkResponse> links;

    /** 已加入的志愿表列表 */
    private List<VolunteerFormBrief> inVolunteerForms;

    /**
     * 志愿表简要信息.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VolunteerFormBrief {
        /** 志愿表ID */
        private Long formId;
        /** 志愿表名称 */
        private String formName;
    }
}
