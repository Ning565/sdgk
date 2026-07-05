package com.example.admission.recommendation.util;

import com.example.admission.recommendation.dto.PlanRecommendationResponse;
import com.example.admission.recommendation.dto.SchoolGroupResponse;

import java.math.BigDecimal;
import java.util.*;

/**
 * 院校分组工具.
 *
 * <p>将专业推荐列表按 schoolId 分组，每组按排序值排列，
 * 并统计每个院校的 minProbability / maxProbability / eligiblePlanCount。</p>
 *
 * <h3>分组规则</h3>
 * <ol>
 *   <li>先按 schoolId 分组</li>
 *   <li>院校组按该校最优（最高或最低，取决于排序方向）排序值排列</li>
 *   <li>院校内专业按同一排序字段排列</li>
 *   <li>每个院校组内最多返回前 20 个专业</li>
 * </ol>
 */
public final class SchoolGrouper {

    /** 每个院校组内最多返回的专业数 */
    private static final int MAX_PLANS_PER_SCHOOL = 20;

    private SchoolGrouper() {
    }

    /**
     * 将专业推荐列表按院校分组.
     *
     * <p>每个院校组使用本校符合条件的专业中的最优排序值作为组排序依据。</p>
     *
     * @param plans  已排序的专业推荐列表
     * @param sortBy 排序字段（用于确定组内和组间排序方向）
     * @param sortDir 排序方向
     * @return 按排序值排序的院校分组列表
     */
    public static List<SchoolGroupResponse> group(List<PlanRecommendationResponse> plans,
                                                   String sortBy, String sortDir) {
        if (plans == null || plans.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. 按 schoolId 分组，保持组内顺序（plans 已是排序后的列表）
        Map<Long, List<PlanRecommendationResponse>> schoolPlanMap = new LinkedHashMap<>();
        for (PlanRecommendationResponse plan : plans) {
            schoolPlanMap.computeIfAbsent(plan.getSchoolId(), k -> new ArrayList<>()).add(plan);
        }

        // 2. 构建 SchoolGroupResponse 列表
        List<SchoolGroupResponse> groups = new ArrayList<>();
        for (Map.Entry<Long, List<PlanRecommendationResponse>> entry : schoolPlanMap.entrySet()) {
            List<PlanRecommendationResponse> schoolPlans = entry.getValue();

            // 取前 MAX_PLANS_PER_SCHOOL 个专业
            List<PlanRecommendationResponse> topPlans = schoolPlans.size() > MAX_PLANS_PER_SCHOOL
                    ? new ArrayList<>(schoolPlans.subList(0, MAX_PLANS_PER_SCHOOL))
                    : new ArrayList<>(schoolPlans);

            // 获取院校基本信息（从第一个专业中提取）
            PlanRecommendationResponse firstPlan = schoolPlans.get(0);

            // 统计概率范围
            BigDecimal minProb = null;
            BigDecimal maxProb = null;
            for (PlanRecommendationResponse plan : schoolPlans) {
                if (plan.getProbability() != null) {
                    if (minProb == null || plan.getProbability().compareTo(minProb) < 0) {
                        minProb = plan.getProbability();
                    }
                    if (maxProb == null || plan.getProbability().compareTo(maxProb) > 0) {
                        maxProb = plan.getProbability();
                    }
                }
            }

            SchoolGroupResponse group = SchoolGroupResponse.builder()
                    .schoolId(firstPlan.getSchoolId())
                    .schoolName(firstPlan.getSchoolName())
                    .schoolCode(firstPlan.getSchoolCode())
                    .province(firstPlan.getProvince())
                    .city(firstPlan.getCity())
                    .schoolType(firstPlan.getSchoolType())
                    .schoolTag(firstPlan.getSchoolTag())
                    .eligiblePlanCount(schoolPlans.size())
                    .minProbability(minProb)
                    .maxProbability(maxProb)
                    .plans(topPlans)
                    .build();

            groups.add(group);
        }

        // 3. 按院校组排序值排序
        // 院校组排序值 = 组内第一个专业的排序值（plans 已全局排序，组内第一个即最优）
        // 由于使用 LinkedHashMap 保持了插入顺序（即原 plans 的顺序），
        // 各组的第一个计划已经按全局排序顺序出现，因此 groups 已按计划出现顺序排列
        // 直接返回即可（LinkedHashMap 遍历顺序 = 各校首个计划在原列表中的位置）

        // 但如果不同学校的专业交错出现，LinkedHashMap 保留了首次出现的顺序，这是正确的
        // 无需再排序

        return groups;
    }

    /**
     * 计算位次差: predictedRank - candidateRank.
     *
     * @param predictedRank  预测位次
     * @param candidateRank  考生位次
     * @return 位次差，正值表示超出
     */
    public static Integer computeRankDiff(Integer predictedRank, Integer candidateRank) {
        if (predictedRank == null || candidateRank == null) {
            return null;
        }
        return predictedRank - candidateRank;
    }

    /**
     * 计算计划变化标签.
     *
     * @param currentPlanCount 本年招生人数
     * @param lastYearPlanCount 上一年招生人数
     * @return 增加/减少/持平/新增
     */
    public static String computePlanChange(Integer currentPlanCount, Integer lastYearPlanCount) {
        if (lastYearPlanCount == null || lastYearPlanCount == 0) {
            return "新增";
        }
        if (currentPlanCount == null) {
            return "持平";
        }
        int diff = currentPlanCount - lastYearPlanCount;
        if (diff > 0) {
            return "增加";
        } else if (diff < 0) {
            return "减少";
        } else {
            return "持平";
        }
    }
}
