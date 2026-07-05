package com.example.admission.prediction.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 预测批量请求 DTO.
 */
@Data
public class PredictionRequest {

    @NotNull(message = "考生信息不能为空")
    @Valid
    private CandidateInfo candidate;

    @NotEmpty(message = "招生计划列表不能为空")
    @Valid
    private List<PlanInfo> plans;

    /**
     * 考生信息.
     */
    @Data
    public static class CandidateInfo {

        @NotNull(message = "年度不能为空")
        private Integer year;

        private Integer score;

        @NotNull(message = "位次不能为空")
        private Integer rank;

        /** 选考科目列表，如 ["物理","化学","生物"] */
        private List<String> subjects;
    }

    /**
     * 招生计划信息（含历史录取数据）.
     */
    @Data
    public static class PlanInfo {

        @NotNull(message = "计划ID不能为空")
        private Long planId;

        /** 该计划的历史录取数据 */
        private List<HistoryEntry> history;
    }

    /**
     * 历史录取数据条目.
     */
    @Data
    public static class HistoryEntry {

        @NotNull(message = "历史年度不能为空")
        private Integer year;

        private Integer lowestRank;

        private Integer planCount;
    }
}
