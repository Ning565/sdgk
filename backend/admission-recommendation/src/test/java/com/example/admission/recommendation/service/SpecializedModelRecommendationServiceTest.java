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

        assertEquals("specialized-rank-v2-specialist-workbook", response.getModelVersion());
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
                && plan.getProbability().compareTo(BigDecimal.ONE) >= 0
                && plan.getProbability().compareTo(BigDecimal.valueOf(99.99)) <= 0));
        assertTrue(plans.stream().anyMatch(plan -> "冲".equals(plan.getLabel())));
        assertTrue(plans.stream().anyMatch(plan -> "稳".equals(plan.getLabel())));
        assertTrue(plans.stream().anyMatch(plan -> "保".equals(plan.getLabel())));
    }

    @Test
    void searchHonorsCustomPortfolioCountAndRushFloor() {
        SpecializedModelRecommendationService service = new SpecializedModelRecommendationService();
        ReflectionTestUtils.setField(service, "modelPath",
                "../../data/organized/modeling_outputs/specialized_rank_model_2026.csv");

        RecommendationRequest request = new RecommendationRequest();
        request.setYear(2026);
        request.setEducationLevel("SPECIALIZED");
        request.setRank(320000);
        request.setSubjects(List.of("物理", "化学", "生物"));
        request.setPageNo(1);
        request.setPageSize(96);
        request.setRecommendationCount(30);
        request.setRushRatio(BigDecimal.valueOf(0.40));
        request.setStableRatio(BigDecimal.valueOf(0.30));
        request.setSafeRatio(BigDecimal.valueOf(0.30));
        request.setRushProbabilityMin(BigDecimal.valueOf(20));
        request.setSortBy("probability");
        request.setSortDir("desc");

        RecommendationResponse response = service.search(request);
        List<PlanRecommendationResponse> plans = response.getSchoolGroups().stream()
                .flatMap(group -> group.getPlans().stream())
                .toList();

        assertEquals(30, plans.size());
        assertTrue(plans.stream()
                .filter(plan -> "冲".equals(plan.getLabel()))
                .allMatch(plan -> plan.getProbability().compareTo(BigDecimal.valueOf(20)) >= 0));
    }

    @Test
    void searchTreatsSubjectRequirementsAsHardFilter() {
        SpecializedModelRecommendationService service = new SpecializedModelRecommendationService();
        ReflectionTestUtils.setField(service, "modelPath",
                "../../data/organized/modeling_outputs/specialized_rank_model_2026.csv");

        RecommendationRequest request = new RecommendationRequest();
        request.setYear(2026);
        request.setEducationLevel("SPECIALIZED");
        request.setRank(320000);
        request.setKeyword("口腔医学");
        request.setSubjects(List.of("历史", "生物", "地理"));
        request.setPageNo(1);
        request.setRecommendationCount(500);
        request.setSortBy("probability");
        request.setSortDir("desc");

        RecommendationResponse response = service.search(request);
        List<PlanRecommendationResponse> plans = response.getSchoolGroups().stream()
                .flatMap(group -> group.getPlans().stream())
                .toList();

        assertTrue(plans.stream()
                .noneMatch(plan -> plan.getSubjectRequirementText() != null
                        && plan.getSubjectRequirementText().contains("物理")));

        request.setSubjects(List.of());
        RecommendationResponse withoutSubjects = service.search(request);
        List<PlanRecommendationResponse> unrestrictedOnly = withoutSubjects.getSchoolGroups().stream()
                .flatMap(group -> group.getPlans().stream())
                .toList();

        assertTrue(unrestrictedOnly.stream()
                .allMatch(plan -> plan.getSubjectRequirementText() == null
                        || plan.getSubjectRequirementText().isBlank()
                        || "不限".equals(plan.getSubjectRequirementText())));
    }
}
