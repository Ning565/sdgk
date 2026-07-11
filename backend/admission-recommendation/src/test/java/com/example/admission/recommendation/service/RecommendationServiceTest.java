package com.example.admission.recommendation.service;

import com.example.admission.catalog.service.EnrollmentPlanService;
import com.example.admission.dataimport.entity.ActiveDataVersion;
import com.example.admission.dataimport.service.DataVersionService;
import com.example.admission.recommendation.dto.PlanRecommendationResponse;
import com.example.admission.recommendation.dto.RecommendationRequest;
import com.example.admission.recommendation.dto.RecommendationResponse;
import com.example.admission.recommendation.mapper.RecommendationMapper;
import com.example.admission.recommendation.mapper.RecommendationPlanVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 本科数据库推荐路径的位次感知过滤测试.
 *
 * <p>覆盖此前的致命 bug：无 predicted_rank 的特殊招生计划（综评/专项/新增）
 * 被随机概率兜底 + NULL 排序抢占结果，对本科线下考生错误推荐名校高位次专业。</p>
 */
class RecommendationServiceTest {

    private RecommendationMapper mapper;
    private DataVersionService dataVersionService;
    private SpecializedModelRecommendationService specializedService;
    private RecommendationService service;

    @BeforeEach
    void setUp() {
        mapper = Mockito.mock(RecommendationMapper.class);
        dataVersionService = Mockito.mock(DataVersionService.class);
        EnrollmentPlanService enrollmentPlanService = Mockito.mock(EnrollmentPlanService.class);
        specializedService = Mockito.mock(SpecializedModelRecommendationService.class);
        service = new RecommendationService(mapper, enrollmentPlanService, dataVersionService, specializedService);

        ActiveDataVersion planVersion = Mockito.mock(ActiveDataVersion.class);
        when(planVersion.getDataVersionId()).thenReturn(1L);
        when(dataVersionService.getActiveVersion(eq("PLAN"), any())).thenReturn(planVersion);
        when(dataVersionService.getActiveVersion(eq("HISTORY"), any())).thenReturn(null);
    }

    private RecommendationPlanVO vo(long planId, long schoolId, String schoolName,
                                    Integer predictedRank, Integer lastYearMinRank) {
        RecommendationPlanVO vo = new RecommendationPlanVO();
        vo.setPlanId(planId);
        vo.setSchoolId(schoolId);
        vo.setSchoolName(schoolName);
        vo.setSchoolCode("C" + schoolId);
        vo.setMajorName("专业" + planId);
        vo.setPlanCount(10);
        vo.setPlanStatus("ACTIVE");
        vo.setPredictedRank(predictedRank);
        vo.setLastYearMinRank(lastYearMinRank);
        return vo;
    }

    private RecommendationRequest baseRequest(int rank) {
        RecommendationRequest req = new RecommendationRequest();
        req.setYear(2026);
        req.setEducationLevel("UNDERGRADUATE");
        req.setRank(rank);
        req.setPageNo(1);
        req.setPageSize(96);
        req.setRecommendationCount(96);
        req.setRushProbabilityMin(BigDecimal.valueOf(20));
        req.setSortBy("probability");
        req.setSortDir("asc");
        return req;
    }

    private Map<Long, PlanRecommendationResponse> byPlanId(RecommendationResponse response) {
        return response.getSchoolGroups().stream()
                .flatMap(group -> group.getPlans().stream())
                .collect(Collectors.toMap(PlanRecommendationResponse::getPlanId, p -> p));
    }

    @Test
    void excludesUnreachableSpecialAndHighRankUndergraduatePlans() {
        // 考生本科线下：位次 428423
        List<RecommendationPlanVO> pool = List.of(
                // 山东师范大学综评：无自身位次（兜底），同校最宽松位次 100000，考生远差于它 → 剔除
                vo(1L, 1L, "山东师范大学", null, null),
                vo(2L, 1L, "山东师范大学", 100000, null),
                // 可达职业本科：预估位次 430000，概率约 50% → 保留
                vo(3L, 2L, "某职业技术大学", 430000, null)
        );
        when(mapper.searchCandidates(any(), eq(1L), any(), anyInt())).thenReturn(pool);

        RecommendationResponse response = service.search(baseRequest(428423), null);
        Map<Long, PlanRecommendationResponse> plans = byPlanId(response);

        assertFalse(plans.containsKey(1L), "无位次的名校综评专业应被院校位次兜底剔除");
        assertFalse(plans.containsKey(2L), "远超考生位次的名校专业应被冲刺下限过滤");
        assertTrue(plans.containsKey(3L), "位次可达的本科专业应保留");
    }

    @Test
    void fallbackPlanIsProbabilityCappedAndNeverSafe() {
        // 考生位次 50000，远好于同校最宽松位次 95000
        List<RecommendationPlanVO> pool = List.of(
                vo(10L, 3L, "某本科院校", null, null),   // 兜底专业
                vo(11L, 3L, "某本科院校", 95000, null)   // 提供同校参考位次
        );
        when(mapper.searchCandidates(any(), eq(1L), any(), anyInt())).thenReturn(pool);

        RecommendationResponse response = service.search(baseRequest(50000), null);
        Map<Long, PlanRecommendationResponse> plans = byPlanId(response);

        PlanRecommendationResponse fallback = plans.get(10L);
        assertNotNull(fallback, "同校最宽松位次可达时兜底专业应保留");
        assertTrue(fallback.getProbability().compareTo(BigDecimal.valueOf(50)) <= 0,
                "兜底专业概率应封顶 50%");
        assertNotEquals("保", fallback.getLabel(), "兜底专业不得进入保底区");

        PlanRecommendationResponse normal = plans.get(11L);
        assertNotNull(normal, "有自身位次且可达的专业应保留");
        assertTrue(normal.getProbability().compareTo(BigDecimal.valueOf(80)) >= 0,
                "考生位次远好于该专业时应为高概率");
    }
}
