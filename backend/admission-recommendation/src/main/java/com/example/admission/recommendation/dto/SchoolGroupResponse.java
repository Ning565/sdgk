package com.example.admission.recommendation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 院校分组响应.
 *
 * <p>将符合条件的专业按院校分组，每组包含该院校的所有符合条件的专业，
 * 以及该院校的最高/最低概率、符合条件的专业总数等汇总信息。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchoolGroupResponse {

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

    /** 符合条件的专业总数 */
    private Integer eligiblePlanCount;

    /** 该校所有符合条件的专业中的最低录取概率 */
    private BigDecimal minProbability;

    /** 该校所有符合条件的专业中的最高录取概率 */
    private BigDecimal maxProbability;

    /** 该校符合条件的专业列表（按排序字段排列，最多返回该组内前20个） */
    private List<PlanRecommendationResponse> plans;
}
