package com.example.admission.recommendation.mapper;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 推荐查询结果 VO，对应复杂 JOIN 查询的返回行.
 *
 * <p>包含 enrollment_plan、school、standard_major、admission_history、
 * prediction_result 多表联查的字段。</p>
 */
@Data
public class RecommendationPlanVO {

    // ==================== enrollment_plan 字段 ====================
    private Long planId;
    private Long planSeriesId;
    private Long dataVersionId;
    private Integer year;
    private Long schoolId;
    private String schoolCode;
    private String schoolName;
    private String majorCode;
    private String majorName;
    private String standardMajorCode;
    private String majorCategory;
    private String campusCode;
    private String campusName;
    private String enrollmentType;
    private String educationLevel;
    private Integer planCount;
    private BigDecimal tuition;
    private Integer duration;
    private String subjectRequirementText;
    private String planStatus;
    private Long eligibleSubjectBitmap;
    
    // ==================== school 表字段 ====================
    private String province;
    private String city;
    private String schoolType;
    private String schoolTag;

    // ==================== standard_major 表字段 ====================
    private String majorSubcategory;

    // ==================== admission_history 表字段 ====================
    private Integer lastYearMinRank;
    private Integer twoYearMinRank;
    private Integer threeYearMinRank;
    private Integer lastYearPlanCount;
    private BigDecimal lastYearMinScore;

    // ==================== prediction_result 表字段 ====================
    private BigDecimal probability;
    private String label;
    private Integer predictedRank;
    private Integer rankRangeMin;
    private Integer rankRangeMax;
    private String modelVersion;
}
