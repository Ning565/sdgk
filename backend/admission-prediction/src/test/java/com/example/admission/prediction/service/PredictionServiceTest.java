package com.example.admission.prediction.service;

import com.example.admission.prediction.dto.PredictionRequest;
import com.example.admission.prediction.dto.PredictionResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PredictionServiceTest {

    private final PredictionService predictionService = new PredictionService();

    @Test
    void predictsBaoWhenCandidateRankIsBetterThanLowestAdmittedRank() {
        PredictionRequest request = requestWithRanks(1_000, 2_000);

        PredictionResponse response = predictionService.predict(request).get(0);

        assertThat(response.getLabel()).isEqualTo("保");
        assertThat(response.getProbability()).isGreaterThan(50);
        assertThat(response.getReasons()).anyMatch(reason -> reason.contains("领先"));
    }

    @Test
    void predictsChongWhenCandidateRankIsWorseThanLowestAdmittedRank() {
        PredictionRequest request = requestWithRanks(3_000, 2_000);

        PredictionResponse response = predictionService.predict(request).get(0);

        assertThat(response.getLabel()).isEqualTo("冲");
        assertThat(response.getProbability()).isLessThan(50);
        assertThat(response.getReasons()).anyMatch(reason -> reason.contains("落后"));
    }

    private PredictionRequest requestWithRanks(int candidateRank, int lowestRank) {
        PredictionRequest.CandidateInfo candidate = new PredictionRequest.CandidateInfo();
        candidate.setYear(2026);
        candidate.setRank(candidateRank);

        PredictionRequest.HistoryEntry history = new PredictionRequest.HistoryEntry();
        history.setYear(2025);
        history.setLowestRank(lowestRank);
        history.setPlanCount(10);

        PredictionRequest.PlanInfo plan = new PredictionRequest.PlanInfo();
        plan.setPlanId(1L);
        plan.setHistory(List.of(history));

        PredictionRequest request = new PredictionRequest();
        request.setCandidate(candidate);
        request.setPlans(List.of(plan));
        return request;
    }
}
