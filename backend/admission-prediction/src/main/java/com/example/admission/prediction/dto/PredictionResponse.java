package com.example.admission.prediction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 单个计划的预测结果 DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictionResponse {

    /** 招生计划ID */
    private Long planId;

    /** 录取概率 (1-99) */
    private Integer probability;

    /** 冲稳保标签: 冲/稳/保 */
    private String label;

    /** 预测位次下限 */
    private Integer predictedRankMin;

    /** 预测位次上限 */
    private Integer predictedRankMax;

    /** 置信度: 低/中/高 */
    private String confidence;

    /** 预测理由列表 */
    private List<String> reasons;

    /** 算法模型版本 */
    private String modelVersion;
}
