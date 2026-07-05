package com.example.admission.candidate.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分数位次匹配响应 DTO.
 *
 * <p>返回当年一分一段表中该分数对应的累计人数（即省排名位次），
 * 以及数据版本信息，用于前端展示和核对。</p>
 */
@Data
@Builder
public class ScoreRankResolveResponse {

    /** 高考年份 */
    private Integer year;

    /** 高考分数 */
    private Integer score;

    /** 累计人数（等于省排名位次） */
    private Integer cumulativeCount;

    /** 数据版本ID */
    private Long dataVersionId;

    /** 数据版本名称（如 "2025年夏季高考一分一段表 v3"） */
    private String dataVersionName;

    /** 数据版本更新时间 */
    private LocalDateTime updatedAt;
}
