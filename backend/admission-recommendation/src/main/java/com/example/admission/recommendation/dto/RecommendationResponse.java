package com.example.admission.recommendation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * 推荐搜索完整响应.
 *
 * <p>包含院校分组列表、汇总统计数据、数据版本信息、追踪ID等。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationResponse {

    /** 院校分组列表（按排序值降序排列） */
    private List<SchoolGroupResponse> schoolGroups;

    /** 符合条件的专业总数 */
    private Long totalPlans;

    /** 符合条件的院校总数 */
    private Integer totalSchools;

    /** 招生计划数据版本 */
    private String planDataVersion;

    /** 历史录取数据版本 */
    private String historyDataVersion;

    /** 预测模型版本 */
    private String modelVersion;

    /** 数据更新时间 */
    private Instant updatedAt;

    /** 分布式追踪ID */
    private String traceId;
}
