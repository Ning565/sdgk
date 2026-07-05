package com.example.admission.candidate.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 分数位次匹配请求 DTO.
 *
 * <p>传入年份和分数，查询当年一分一段表中该分数对应的累计人数（省排名位次）。</p>
 */
@Data
public class ScoreRankResolveRequest {

    /** 高考年份 */
    @NotNull(message = "年份不能为空")
    private Integer year;

    /** 高考分数 */
    @NotNull(message = "分数不能为空")
    private Integer score;
}
