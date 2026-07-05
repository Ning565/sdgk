package com.example.admission.catalog.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 历史录取数据响应.
 *
 * @author admission-system
 */
@Data
public class AdmissionHistoryResponse {

    /** 录取年份 */
    private Integer year;

    /** 当年计划招生数 */
    private Integer planCount;

    /** 最低分 */
    private BigDecimal minScore;

    /** 最低位次 */
    private Integer minRank;

    /** 平均分 */
    private BigDecimal avgScore;

    /** 平均位次 */
    private Integer avgRank;

    /** 最高分 */
    private BigDecimal maxScore;

    /** 最高位次 */
    private Integer maxRank;

    /** 录取批次 */
    private String admissionBatch;
}
