package com.example.admission.recommendation.service;

import com.example.admission.recommendation.dto.PlanRecommendationResponse;
import com.example.admission.recommendation.dto.RecommendationRequest;
import com.example.admission.recommendation.dto.RecommendationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpecializedModelRecommendationServiceTest {

    @Test
    void searchReturnsAtLeastNinetySixSpecializedRecommendations() {
        SpecializedModelRecommendationService service = new SpecializedModelRecommendationService();
        ReflectionTestUtils.setField(service, "modelPath",
                "../../data/organized/modeling_outputs/specialized_rank_model_2026.csv");

        RecommendationRequest request = new RecommendationRequest();
        request.setYear(2026);
        request.setEducationLevel("SPECIALIZED");
        request.setRank(320000);
        request.setScore(430);
        request.setSubjects(List.of("物理", "化学", "生物"));
        request.setPageNo(1);
        request.setPageSize(96);
        request.setSortBy("probability");
        request.setSortDir("desc");

        RecommendationResponse response = service.search(request);

        assertEquals("specialized-rank-v1-pre", response.getModelVersion());
        assertTrue(response.getTotalPlans() >= 96);
        assertFalse(response.getSchoolGroups().isEmpty());

        long returnedPlans = response.getSchoolGroups().stream()
                .flatMap(group -> group.getPlans().stream())
                .count();
        assertTrue(returnedPlans >= 96);

        List<PlanRecommendationResponse> plans = response.getSchoolGroups().stream()
                .flatMap(group -> group.getPlans().stream())
                .toList();
        assertTrue(plans.stream().allMatch(plan -> "SPECIALIZED".equals(plan.getEducationLevel())));
        assertTrue(plans.stream().allMatch(plan -> plan.getProbability() != null
                && plan.getProbability().compareTo(BigDecimal.valueOf(10)) >= 0
                && plan.getProbability().compareTo(BigDecimal.valueOf(99.99)) <= 0));
        assertTrue(plans.stream().anyMatch(plan -> "冲".equals(plan.getLabel())));
        assertTrue(plans.stream().anyMatch(plan -> "稳".equals(plan.getLabel())));
        assertTrue(plans.stream().anyMatch(plan -> "保".equals(plan.getLabel())));
    }
}
