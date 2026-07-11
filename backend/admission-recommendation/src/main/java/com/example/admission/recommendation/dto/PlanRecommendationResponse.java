package com.example.admission.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 专业推荐响应项.
 *
 * <p>在基础招生计划信息之上，增加了概率、预测位次、冲稳保标签、
 * 历史录取数据、位次差、计划变化等推荐专属字段。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanRecommendationResponse {

    // ==================== 基础计划信息 ====================

    /** 招生计划ID */
    private Long planId;

    /** 院校ID */
    private Long schoolId;

    /** 院校名称 */
    private String schoolName;

    /** 院校代码 */
    private String schoolCode;

    /** 省份 */
    private String province;

    /** 城市 */
    private String city;

    /** 院校性质 */
    private String schoolType;

    /** 院校标签 */
    private String schoolTag;

    /** 专业名称 */
    private String majorName;

    /** 专业门类 */
    private String majorCategory;

    /** 专业类 */
    private String majorSubcategory;

    /** 教育层次 */
    private String educationLevel;

    /** 招生类型 */
    private String enrollmentType;

    /** 校区名称 */
    private String campusName;

    /** 校区代码 */
    private String campusCode;

    /** 招生人数 */
    private Integer planCount;

    /** 学费（元/年） */
    private BigDecimal tuition;

    /** 学制（年） */
    private Integer duration;

    /** 计划状态 */
    private String planStatus;

    /** 选科要求文本描述 */
    private String subjectRequirementText;

    // ==================== 历史录取数据 ====================

    /** 上一年最低录取位次 */
    private Integer lastYearMinRank;
    private Integer twoYearMinRank;
    private Integer threeYearMinRank;
    private Integer lastYearPlanCount;

    /** 上一年最低录取分数 */
    private BigDecimal lastYearMinScore;

    // ==================== 预测数据（可能为 null，表示待计算） ====================

    /** 录取概率（0-100），null 表示待计算 */
    private BigDecimal probability;

    /** 冲稳保标签: 冲/稳/保，null 表示待计算 */
    private String label;

    /** 预测位次 */
    private Integer predictedRank;

    /** 位次区间下限 */
    private Integer rankRangeMin;

    /** 位次区间上限 */
    private Integer rankRangeMax;

    /** 置信度（0-1） */
    private BigDecimal confidence;

    // ==================== 计算字段 ====================

    /** 位次差: predictedRank - candidateRank，正值表示考生位次落后预测位次 */
    private Integer rankDiff;

    /** 计划变化: 增加/减少/持平/新增 */
    private String planChange;

    /** 本次主动推荐顺序；仅推荐列表有值，全部候选视图可为空 */
    @JsonProperty("recommend_rank")
    private Integer recommendRank;
}
